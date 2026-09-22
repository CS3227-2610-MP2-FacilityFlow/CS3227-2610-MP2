package sg.edu.nus.facilityflow.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** REQ-002/003/014/015: creation and owner-only reads; never exposes raw audit details.
 * Sessions must come from the shared authentication boundary, not from form input.
 */
public final class RequesterRequestService {
    private final ManagerAssignmentStore store;
    private final RequestValidator validator;
    private final Clock clock;

    public RequesterRequestService(ManagerAssignmentStore store, RequestValidator validator, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MaintenanceRequest createRequest(AuthenticatedSession session, RequestDraft draft) {
        return store.inTransaction(transaction -> {
            UserAccount actor = requireActiveRequester(transaction, session);
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
        return store.inTransaction(transaction -> {
            UserAccount actor = requireActiveRequester(transaction, session);
            return transaction.listOwnRequests(actor.id()).stream()
                    .filter(request -> request.requesterId() == actor.id())
                    .sorted(Comparator.comparing(MaintenanceRequest::createdAt).reversed()
                            .thenComparing(MaintenanceRequest::displayId))
                    .toList();
        });
    }

    public MaintenanceRequest getOwnRequest(AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            UserAccount actor = requireActiveRequester(transaction, session);
            return transaction.findOwnRequest(actor.id(), requestId)
                    .filter(request -> request.requesterId() == actor.id())
                    .orElseThrow(() -> new AuthorizationException("Request is unavailable."));
        });
    }

    private static UserAccount requireActiveRequester(
            ManagerAssignmentStore.TransactionContext transaction, AuthenticatedSession session) {
        if (session == null) {
            throw unauthorized();
        }
        return transaction.findAccount(session.accountId())
                .filter(UserAccount::active)
                .filter(account -> account.role() == Role.REQUESTER)
                .orElseThrow(RequesterRequestService::unauthorized);
    }

    private static AuthorizationException unauthorized() {
        return new AuthorizationException(
                "Your session is not authorized for this operation.");
    }
}
