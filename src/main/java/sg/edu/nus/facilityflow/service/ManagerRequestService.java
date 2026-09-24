package sg.edu.nus.facilityflow.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** Manager request use cases. Authorization and lifecycle rules belong here, not in UI code. */
public final class ManagerRequestService {
    private final ManagerAssignmentStore store;
    private final Clock clock;
    private final SessionManager sessions;

    public ManagerRequestService(ManagerAssignmentStore store, Clock clock, SessionManager sessions) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public List<MaintenanceRequest> listAllRequests(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            return transaction.listRequests().stream().sorted(managerQueueOrder()).toList();
        });
    }

    public List<UserAccount> listActiveTechnicians(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            return List.copyOf(transaction.listActiveTechnicians());
        });
    }

    public MaintenanceRequest assignOpenRequest(
            AuthenticatedSession session,
            long requestId,
            long technicianId,
            ManagerPriority priority) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
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

    public MaintenanceRequest reassignRequest(
            AuthenticatedSession session,
            long requestId,
            long technicianId,
            String reason) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            String normalizedReason = normalizeReason(reason);
            MaintenanceRequest request = transaction.findRequest(requestId)
                    .orElseThrow(() -> new ValidationException("Request was not found."));
            if (request.status() != RequestStatus.ASSIGNED
                    && request.status() != RequestStatus.IN_PROGRESS) {
                throw new ValidationException(
                        "Only ASSIGNED or IN_PROGRESS requests can be reassigned.");
            }
            if (Objects.equals(request.assigneeId(), technicianId)) {
                throw new ValidationException("Select a different Technician for reassignment.");
            }
            UserAccount technician = transaction.findAccount(technicianId)
                    .filter(UserAccount::active)
                    .filter(account -> account.role() == Role.TECHNICIAN)
                    .orElseThrow(() -> new ValidationException(
                            "Assignee must be an active Technician."));

            Instant reassignedAt = clock.instant();
            MaintenanceRequest reassigned = request.reassignTo(technician.id(), reassignedAt);
            transaction.updateRequest(reassigned);
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(),
                    actor.id(),
                    "REQUEST_REASSIGNED",
                    "{\"oldAssigneeId\":" + request.assigneeId()
                            + ",\"newAssigneeId\":" + technician.id()
                            + ",\"reason\":\"" + escapeJson(normalizedReason) + "\"}",
                    reassignedAt));
            return reassigned;
        });
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            throw new ValidationException("Reassignment reason is required.");
        }
        String normalized = reason.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 5 || length > 500) {
            throw new ValidationException("Reassignment reason must contain 5 to 500 characters.");
        }
        return normalized;
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u%04x".formatted((int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
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
