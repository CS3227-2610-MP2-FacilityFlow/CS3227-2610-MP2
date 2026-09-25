package sg.edu.nus.facilityflow.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.AccountCredentials;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.TechnicianDashboardCounts;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.model.RequesterUpdate;
import sg.edu.nus.facilityflow.model.RequesterHistoryEntry;

/** Shared transaction seam for authenticated request operations across all roles. */
public interface ManagerAssignmentStore {
    <T> T inTransaction(TransactionWork<T> work);

    @FunctionalInterface
    interface TransactionWork<T> {
        T execute(TransactionContext transaction);
    }

    interface TransactionContext {
        Optional<UserAccount> findAccount(long accountId);

        Optional<AccountCredentials> findCredentials(String username);

        void updatePassword(long accountId, String hash, boolean invalidateSessions, Instant changedAt);

        void appendAccountAudit(long actorId, long targetId, String action, Instant occurredAt);

        Optional<MaintenanceRequest> findRequest(long requestId);

        List<MaintenanceRequest> listRequests();

        MaintenanceRequest createOpenRequest(long requesterId, RequestDraft draft, Instant createdAt);

        List<MaintenanceRequest> listOwnRequests(long requesterId);

        Optional<MaintenanceRequest> findOwnRequest(long requesterId, long requestId);

        List<MaintenanceRequest> listAssignedRequests(long technicianId);

        TechnicianDashboardCounts getTechnicianDashboardCounts(long technicianId);

        Optional<MaintenanceRequest> findAssignedRequest(long technicianId, long requestId);

        List<WorkLog> listWorkLogs(long requestId);

        WorkLog appendWorkLog(
                long requestId, long authorId, String note, int minutesSpent, Instant createdAt);

        boolean updateTechnicianRequest(
                MaintenanceRequest request, long technicianId, RequestStatus expectedStatus);

        List<UserAccount> listActiveTechnicians();

        void updateRequest(MaintenanceRequest request);

        void appendAuditEvent(AuditEvent event);

        default void addRequesterUpdate(long requestId, long authorId, String text, Instant at) {
            throw new UnsupportedOperationException("Requester updates are unavailable");
        }

        default List<RequesterUpdate> listRequesterUpdates(long requestId) { return List.of(); }

        default List<RequesterHistoryEntry> listRequesterHistory(long requestId) { return List.of(); }

        default boolean updateRequesterRequest(MaintenanceRequest request, long ownerId, RequestStatus expected) {
            throw new UnsupportedOperationException("Requester edits are unavailable");
        }

        default boolean cancelRequesterRequest(long requestId, long ownerId, Instant at) {
            throw new UnsupportedOperationException("Requester cancellation is unavailable");
        }
    }
}
