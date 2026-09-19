package sg.edu.nus.facilityflow.storage;

import java.util.List;
import java.util.Optional;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.UserAccount;

/** Transaction boundary needed by manager request operations. */
public interface ManagerAssignmentStore {
    <T> T inTransaction(TransactionWork<T> work);

    @FunctionalInterface
    interface TransactionWork<T> {
        T execute(TransactionContext transaction);
    }

    interface TransactionContext {
        Optional<UserAccount> findAccount(long accountId);

        Optional<MaintenanceRequest> findRequest(long requestId);

        List<MaintenanceRequest> listRequests();

        List<UserAccount> listActiveTechnicians();

        void updateRequest(MaintenanceRequest request);

        void appendAuditEvent(AuditEvent event);
    }
}
