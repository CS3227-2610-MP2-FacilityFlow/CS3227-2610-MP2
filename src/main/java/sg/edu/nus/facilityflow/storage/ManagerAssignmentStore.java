package sg.edu.nus.facilityflow.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.AccountCredentials;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.model.RequestStatus;

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

        boolean updateOwnOpenRequest(MaintenanceRequest request, long requesterId);

        Optional<String> findOwnCancellationReason(long requesterId, long requestId);

        List<MaintenanceRequest> listAssignedRequests(long technicianId);

        Optional<MaintenanceRequest> findAssignedRequest(long technicianId, long requestId);

        List<WorkLog> listWorkLogs(long requestId);

        WorkLog appendWorkLog(
                long requestId, long authorId, String note, int minutesSpent, Instant createdAt);

        boolean updateTechnicianRequest(
                MaintenanceRequest request, long technicianId, RequestStatus expectedStatus);

        List<UserAccount> listActiveTechnicians();

        void updateRequest(MaintenanceRequest request);

        void appendAuditEvent(AuditEvent event);
    }
}
