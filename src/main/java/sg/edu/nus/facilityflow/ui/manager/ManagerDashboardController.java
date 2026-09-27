package sg.edu.nus.facilityflow.ui.manager;

import java.util.List;
import java.util.Objects;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.AuthenticationService;
import sg.edu.nus.facilityflow.model.AuditFilter;
import sg.edu.nus.facilityflow.model.AuditRecord;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerDashboardSummary;
import sg.edu.nus.facilityflow.model.ManagerHistoryEntry;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ManagerRequestFilter;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.service.ManagerAccountService;
import sg.edu.nus.facilityflow.service.ManagerRequestService;

/** Presentation adapter for the Manager dashboard; lifecycle rules remain in the service. */
public final class ManagerDashboardController {
    private final ManagerRequestService requestService;
    private final ManagerAccountService accountService;
    private final AuthenticationService authenticationService;
    private final AuthenticatedSession session;

    public ManagerDashboardController(
            ManagerRequestService requestService,
            ManagerAccountService accountService,
            AuthenticationService authenticationService,
            AuthenticatedSession session) {
        this.requestService = Objects.requireNonNull(requestService, "requestService");
        this.accountService = Objects.requireNonNull(accountService, "accountService");
        this.authenticationService = Objects.requireNonNull(
                authenticationService, "authenticationService");
        this.session = Objects.requireNonNull(session, "session");
    }

    public List<MaintenanceRequest> loadRequests() {
        return requestService.listAllRequests(session);
    }

    public List<MaintenanceRequest> loadRequests(ManagerRequestFilter filter) {
        return requestService.listAllRequests(session, filter);
    }

    public List<UserAccount> loadActiveTechnicians() {
        return requestService.listActiveTechnicians(session);
    }

    public List<UserAccount> loadRequesters() {
        return requestService.listRequesters(session);
    }

    public ManagerDashboardSummary loadSummary() {
        return requestService.getDashboardSummary(session);
    }

    public List<ManagerHistoryEntry> loadHistory(long requestId) {
        return requestService.getRequestHistory(session, requestId);
    }

    public List<AuditRecord> loadAudit(AuditFilter filter) {
        return requestService.listAuditRecords(session, filter);
    }

    public MaintenanceRequest assign(
            MaintenanceRequest request, UserAccount technician, ManagerPriority priority) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(technician, "technician");
        return requestService.assignOpenRequest(session, request.id(), technician.id(), priority);
    }

    public MaintenanceRequest reassign(
            MaintenanceRequest request, UserAccount technician, String reason) {
        return requestService.reassignRequest(session, request.id(), technician.id(), reason);
    }

    public MaintenanceRequest close(MaintenanceRequest request) {
        return requestService.closeCompletedRequest(session, request.id());
    }

    public MaintenanceRequest returnForRework(
            MaintenanceRequest request, UserAccount technician, String reason) {
        return requestService.returnCompletedRequest(
                session, request.id(), technician.id(), reason);
    }

    public MaintenanceRequest reopen(
            MaintenanceRequest request, UserAccount technician, String reason) {
        return requestService.reopenClosedRequest(
                session, request.id(), technician.id(), reason);
    }

    public MaintenanceRequest cancel(MaintenanceRequest request, String reason) {
        return requestService.cancelRequest(session, request.id(), reason);
    }

    public MaintenanceRequest recordOnBehalf(UserAccount requester, RequestDraft draft) {
        return requestService.recordOnBehalf(session, requester.id(), draft);
    }

    public MaintenanceRequest correct(
            MaintenanceRequest request, RequestDraft draft, ManagerPriority priority) {
        return requestService.correctRequest(session, request.id(), draft, priority);
    }

    public List<UserAccount> loadAccounts() {
        return accountService.listAccounts(session);
    }

    public UserAccount createAccount(
            String username, String displayName, Role role, char[] password) {
        return accountService.createAccount(session, username, displayName, role, password);
    }

    public UserAccount setActive(UserAccount account, boolean active) {
        return accountService.setActive(session, account.id(), active);
    }

    public UserAccount changeRole(UserAccount account, Role role) {
        return accountService.changeRole(session, account.id(), role);
    }

    public void resetPassword(UserAccount account, char[] password) {
        authenticationService.resetPassword(session, account.id(), password);
    }
}
