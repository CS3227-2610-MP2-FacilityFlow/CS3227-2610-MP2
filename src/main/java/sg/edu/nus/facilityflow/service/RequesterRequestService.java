package sg.edu.nus.facilityflow.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.time.ZoneId;
import java.util.Locale;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.model.RequesterFilter;
import sg.edu.nus.facilityflow.model.RequesterHistoryEntry;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** Requester use cases with owner-scoped reads, lifecycle guards, and filtered public history.
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
            if (draft == null) {
                throw new ValidationException("Request details are required.");
            }
            var errors = validator.validate(draft);
            if (!errors.isEmpty()) {
                throw new ValidationException(String.join(" ", errors.values()));
            }
            var request = transaction.createOpenRequest(actor.id(), draft, clock.instant());
            transaction.appendAuditEvent(new AuditEvent(request.id(), actor.id(),
                    "REQUEST_CREATED", "{\"status\":\"OPEN\"}", request.createdAt()));
            return request;
        });
    }

    public List<MaintenanceRequest> listOwnRequests(AuthenticatedSession session) {
        return listOwnRequests(session, new RequesterFilter(null, null, null, null, null));
    }

    public List<MaintenanceRequest> listOwnRequests(AuthenticatedSession session, RequesterFilter filter) {
        RequesterFilter effective = filter == null ? new RequesterFilter(null, null, null, null, null) : filter;
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.REQUESTER);
            String query = effective.query() == null ? "" : effective.query().strip().toLowerCase(Locale.ROOT);
            var from = effective.from() == null ? null : effective.from().atStartOfDay(ZoneId.systemDefault()).toInstant();
            var until = effective.through() == null ? null : effective.through().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            return transaction.listOwnRequests(actor.id()).stream()
                    .filter(request -> request.requesterId() == actor.id())
                    .filter(request -> effective.status() == null || request.status() == effective.status())
                    .filter(request -> effective.category() == null || effective.category().isBlank() || request.category().equals(effective.category()))
                    .filter(request -> query.isEmpty() || request.displayId().toLowerCase(Locale.ROOT).contains(query)
                            || request.title().toLowerCase(Locale.ROOT).contains(query) || request.location().toLowerCase(Locale.ROOT).contains(query))
                    .filter(request -> from == null || !request.createdAt().isBefore(from))
                    .filter(request -> until == null || request.createdAt().isBefore(until))
                    .sorted(Comparator.comparing(MaintenanceRequest::createdAt).reversed()
                            .thenComparing(MaintenanceRequest::displayId))
                    .toList();
        });
    }

    public MaintenanceRequest editOpenRequest(AuthenticatedSession session, long requestId, RequestDraft draft) {
        return store.inTransaction(tx -> {
            UserAccount actor = sessions.requireRole(tx, session, Role.REQUESTER);
            var current = tx.findOwnRequest(actor.id(), requestId).orElseThrow(() -> new AuthorizationException("Request is unavailable."));
            if (current.status() != RequestStatus.OPEN) {
                throw new ValidationException("Only OPEN requests can be edited.");
            }
            if (draft == null) {
                throw new ValidationException("Request details are required.");
            }
            var errors = validator.validate(draft);
            if (!errors.isEmpty()) {
                throw new ValidationException(String.join(" ", errors.values()));
            }
            var updated = new MaintenanceRequest(current.id(), current.displayId(), current.requesterId(), draft.title(), draft.description(), draft.location(), draft.category(), draft.urgency(), current.managerPriority(), current.status(), current.assigneeId(), current.assignedAt(), current.resolutionSummary(), current.completedAt(), current.createdAt(), clock.instant());
            if (!tx.updateRequesterRequest(updated, actor.id(), RequestStatus.OPEN)) {
                throw new ValidationException("Request changed. Refresh and try again.");
            }
            tx.appendAuditEvent(new AuditEvent(requestId, actor.id(), "REQUEST_EDITED", "{\"fields\":[\"title\",\"description\",\"location\",\"category\",\"reportedUrgency\"]}", updated.updatedAt()));
            return updated;
        });
    }

    public MaintenanceRequest cancelOpenRequest(AuthenticatedSession session, long requestId, String reason) {
        String normalized = reason == null ? "" : reason.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 5 || length > 500) {
            throw new ValidationException("Cancellation reason must contain 5 to 500 characters.");
        }
        return store.inTransaction(tx -> {
            UserAccount actor = sessions.requireRole(tx, session, Role.REQUESTER);
            var current = tx.findOwnRequest(actor.id(), requestId).orElseThrow(() -> new AuthorizationException("Request is unavailable."));
            if (current.status() != RequestStatus.OPEN) {
                throw new ValidationException("Only OPEN requests can be cancelled.");
            }
            var at = clock.instant();
            if (!tx.cancelRequesterRequest(requestId, actor.id(), at)) {
                throw new ValidationException("Request changed. Refresh and try again.");
            }
            tx.appendAuditEvent(new AuditEvent(requestId, actor.id(), "REQUEST_CANCELLED", normalized, at));
            return getUpdated(tx, actor.id(), requestId);
        });
    }

    public void addFollowUp(AuthenticatedSession session, long requestId, String text) {
        String normalized = text == null ? "" : text.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 1000) {
            throw new ValidationException("Follow-up must contain 1 to 1,000 characters.");
        }
        store.inTransaction(tx -> {
            UserAccount actor = sessions.requireRole(tx, session, Role.REQUESTER);
            var request = tx.findOwnRequest(actor.id(), requestId).orElseThrow(() -> new AuthorizationException("Request is unavailable."));
            if (request.status() == RequestStatus.CLOSED || request.status() == RequestStatus.CANCELLED) {
                throw new ValidationException("This request no longer accepts follow-ups.");
            }
            tx.addRequesterUpdate(requestId, actor.id(), normalized, clock.instant());
            return null;
        });
    }

    public List<RequesterHistoryEntry> getVisibleHistory(AuthenticatedSession session, long requestId) {
        return store.inTransaction(tx -> {
            UserAccount actor = sessions.requireRole(tx, session, Role.REQUESTER);
            tx.findOwnRequest(actor.id(), requestId).orElseThrow(() -> new AuthorizationException("Request is unavailable."));
            var entries = new java.util.ArrayList<>(tx.listRequesterHistory(requestId));
            tx.listRequesterUpdates(requestId).forEach(update -> entries.add(new RequesterHistoryEntry("Follow-up", update.text(), update.createdAt())));
            entries.sort(java.util.Comparator.comparing(RequesterHistoryEntry::occurredAt));
            return List.copyOf(entries);
        });
    }

    private static MaintenanceRequest getUpdated(ManagerAssignmentStore.TransactionContext tx, long owner, long id) {
        return tx.findOwnRequest(owner, id).orElseThrow(() -> new AuthorizationException("Request is unavailable."));
    }

    public MaintenanceRequest getOwnRequest(AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.REQUESTER);
            return transaction.findOwnRequest(actor.id(), requestId)
                    .filter(request -> request.requesterId() == actor.id())
                    .orElseThrow(() -> new AuthorizationException("Request is unavailable."));
        });
    }

}
