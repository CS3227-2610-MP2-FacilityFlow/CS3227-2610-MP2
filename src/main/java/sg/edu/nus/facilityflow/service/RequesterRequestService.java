package sg.edu.nus.facilityflow.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** REQ-002/003/007/008/014/015: creation, OPEN edits/cancellation and owner-only reads.
 * Sessions must come from the shared authentication boundary, not from form input.
 */
public final class RequesterRequestService {
    private final ManagerAssignmentStore store;
    private final RequestValidator validator;
    private final Clock clock;
    private final SessionManager sessions;

    public RequesterRequestService(ManagerAssignmentStore store, RequestValidator validator, Clock clock, SessionManager sessions) {
        this.store = Objects.requireNonNull(store, "store");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public MaintenanceRequest createRequest(AuthenticatedSession session, RequestDraft draft) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.REQUESTER);
            validateDraft(draft);
            var request = transaction.createOpenRequest(actor.id(), draft, clock.instant());
            transaction.appendAuditEvent(new AuditEvent(request.id(), actor.id(),
                    "REQUEST_CREATED", "{\"status\":\"OPEN\"}", request.createdAt()));
            return request;
        });
    }

    public List<MaintenanceRequest> listOwnRequests(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.REQUESTER);
            return transaction.listOwnRequests(actor.id()).stream()
                    .filter(request -> request.requesterId() == actor.id())
                    .sorted(Comparator.comparing(MaintenanceRequest::createdAt).reversed()
                            .thenComparing(MaintenanceRequest::displayId))
                    .toList();
        });
    }

    public MaintenanceRequest getOwnRequest(AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.REQUESTER);
            return requireOwnRequest(transaction, actor.id(), requestId);
        });
    }

    /** REQ-007, LIF-013/016: re-read ownership/state and atomically audit the edit. */
    public MaintenanceRequest editRequest(AuthenticatedSession session, long requestId, RequestDraft draft) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.REQUESTER);
            MaintenanceRequest request = requireOwnOpenRequest(transaction, actor.id(), requestId);
            validateDraft(draft);
            MaintenanceRequest edited = request.editDetails(draft, clock.instant());
            updateOwnOpenRequest(transaction, edited, actor.id());
            transaction.appendAuditEvent(new AuditEvent(request.id(), actor.id(), "REQUEST_EDITED",
                    editAuditDetail(request, edited), edited.updatedAt()));
            return edited;
        });
    }

    /** REQ-008, LIF-011/012: cancellation and its visible reason commit together. */
    public MaintenanceRequest cancelRequest(AuthenticatedSession session, long requestId, String reason) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.REQUESTER);
            MaintenanceRequest request = requireOwnOpenRequest(transaction, actor.id(), requestId);
            String normalizedReason = reason == null ? "" : reason.strip();
            int length = normalizedReason.codePointCount(0, normalizedReason.length());
            if (length < 5 || length > 500) {
                throw new ValidationException("Cancellation reason must contain 5 to 500 characters after trimming.");
            }
            MaintenanceRequest cancelled = request.cancel(clock.instant());
            updateOwnOpenRequest(transaction, cancelled, actor.id());
            transaction.appendAuditEvent(new AuditEvent(request.id(), actor.id(), "REQUEST_CANCELLED",
                    "{\"oldStatus\":\"OPEN\",\"newStatus\":\"CANCELLED\",\"reason\":\""
                            + ManagerRequestService.escapeJson(normalizedReason) + "\"}", cancelled.updatedAt()));
            return cancelled;
        });
    }

    /** REQ-A05/010: exposes only the cancellation reason, never raw audit details. */
    public Optional<String> getOwnCancellationReason(AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.REQUESTER);
            MaintenanceRequest request = requireOwnRequest(transaction, actor.id(), requestId);
            return request.status() == RequestStatus.CANCELLED
                    ? transaction.findOwnCancellationReason(actor.id(), requestId) : Optional.empty();
        });
    }

    private static String editAuditDetail(MaintenanceRequest before, MaintenanceRequest after) {
        var fields = new ArrayList<String>();
        if (!before.title().equals(after.title())) {
            fields.add("\"title\"");
        }
        if (!before.description().equals(after.description())) {
            fields.add("\"description\"");
        }
        if (!before.location().equals(after.location())) {
            fields.add("\"location\"");
        }
        if (!before.category().equals(after.category())) {
            fields.add("\"category\"");
        }
        if (before.reportedUrgency() != after.reportedUrgency()) {
            fields.add("\"reportedUrgency\"");
        }
        return "{\"changedFields\":[" + String.join(",", fields) + "]}";
    }

    private void validateDraft(RequestDraft draft) {
        if (draft == null) {
            throw new ValidationException("Request details are required.");
        }
        var errors = validator.validate(draft);
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(" ", errors.values()));
        }
    }

    private static MaintenanceRequest requireOwnRequest(
            ManagerAssignmentStore.TransactionContext transaction, long requesterId, long requestId) {
        return transaction.findOwnRequest(requesterId, requestId)
                .filter(request -> request.requesterId() == requesterId)
                .orElseThrow(() -> new AuthorizationException("Request is unavailable."));
    }

    private static MaintenanceRequest requireOwnOpenRequest(
            ManagerAssignmentStore.TransactionContext transaction, long requesterId, long requestId) {
        MaintenanceRequest request = requireOwnRequest(transaction, requesterId, requestId);
        if (request.status() != RequestStatus.OPEN) {
            throw new ValidationException("Only OPEN requests can be edited or cancelled. Refresh to see the current status.");
        }
        return request;
    }

    private static void updateOwnOpenRequest(
            ManagerAssignmentStore.TransactionContext transaction, MaintenanceRequest request, long requesterId) {
        if (!transaction.updateOwnOpenRequest(request, requesterId)) {
            throw new AuthorizationException("The request ownership or status changed. Refresh and try again.");
        }
    }
}
