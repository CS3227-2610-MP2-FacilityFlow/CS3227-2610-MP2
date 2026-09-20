package sg.edu.nus.facilityflow.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** Manager request use cases. Authorization and lifecycle rules belong here, not in UI code. */
public final class ManagerRequestService {
    private static final String AUTHORIZATION_FAILURE =
            "Your session is not authorized for this operation. Please sign in again.";

    private final ManagerAssignmentStore store;
    private final Clock clock;

    public ManagerRequestService(ManagerAssignmentStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<MaintenanceRequest> listAllRequests(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            requireActiveManager(transaction, session);
            return transaction.listRequests().stream().sorted(managerQueueOrder()).toList();
        });
    }

    public List<UserAccount> listActiveTechnicians(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            requireActiveManager(transaction, session);
            return List.copyOf(transaction.listActiveTechnicians());
        });
    }

    public MaintenanceRequest assignOpenRequest(
            AuthenticatedSession session,
            long requestId,
            long technicianId,
            ManagerPriority priority) {
        return store.inTransaction(transaction -> {
            UserAccount actor = requireActiveManager(transaction, session);
            if (priority == null) {
                throw new ValidationException("Manager priority is required before assignment.");
            }
            MaintenanceRequest request = transaction.findRequest(requestId)
                    .orElseThrow(() -> new ValidationException("Request was not found."));
            if (request.status() != RequestStatus.OPEN) {
                throw new ValidationException("Request must be OPEN before it can be assigned.");
            }

            UserAccount technician = transaction.findAccount(technicianId)
                    .filter(UserAccount::active)
                    .filter(account -> account.role() == Role.TECHNICIAN)
                    .orElseThrow(() -> new ValidationException(
                            "Assignee must be an active Technician."));

            Instant assignedAt = clock.instant();
            MaintenanceRequest assigned = request.assignTo(technician.id(), priority, assignedAt);
            transaction.updateRequest(assigned);
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(),
                    actor.id(),
                    "REQUEST_ASSIGNED",
                    "Assigned to account " + technician.id() + " with priority " + priority,
                    assignedAt));
            return assigned;
        });
    }

    private static UserAccount requireActiveManager(
            ManagerAssignmentStore.TransactionContext transaction,
            AuthenticatedSession session) {
        if (session == null) {
            throw new AuthorizationException(AUTHORIZATION_FAILURE);
        }
        return transaction.findAccount(session.accountId())
                .filter(UserAccount::active)
                .filter(account -> account.role() == Role.FACILITIES_MANAGER)
                .orElseThrow(() -> new AuthorizationException(AUTHORIZATION_FAILURE));
    }

    private static Comparator<MaintenanceRequest> managerQueueOrder() {
        return Comparator.comparingInt(ManagerRequestService::groupOrder)
                .thenComparingInt(ManagerRequestService::withinGroupOrder)
                .thenComparingLong(ManagerRequestService::groupTimestampOrder)
                .thenComparing(MaintenanceRequest::displayId);
    }

    private static int groupOrder(MaintenanceRequest request) {
        return switch (request.status()) {
            case OPEN -> 0;
            case ASSIGNED, IN_PROGRESS -> 1;
            case COMPLETED, CLOSED, CANCELLED -> 2;
        };
    }

    private static int withinGroupOrder(MaintenanceRequest request) {
        if (request.status() == RequestStatus.OPEN) {
            return switch (request.reportedUrgency()) {
                case EMERGENCY -> 0;
                case HIGH -> 1;
                case NORMAL -> 2;
                case LOW -> 3;
            };
        }
        if (request.status() == RequestStatus.ASSIGNED
                || request.status() == RequestStatus.IN_PROGRESS) {
            if (request.managerPriority() == null) {
                return 4;
            }
            return switch (request.managerPriority()) {
                case CRITICAL -> 0;
                case HIGH -> 1;
                case MEDIUM -> 2;
                case LOW -> 3;
            };
        }
        return 0;
    }

    private static long groupTimestampOrder(MaintenanceRequest request) {
        if (request.status() == RequestStatus.OPEN) {
            return request.createdAt().toEpochMilli();
        }
        if (request.status() == RequestStatus.ASSIGNED
                || request.status() == RequestStatus.IN_PROGRESS) {
            return request.updatedAt().toEpochMilli();
        }
        return -request.updatedAt().toEpochMilli();
    }
}
