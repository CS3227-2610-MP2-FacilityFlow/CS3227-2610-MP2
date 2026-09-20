package sg.edu.nus.facilityflow.ui.manager;

import java.util.List;
import java.util.Objects;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.service.ManagerRequestService;

/** Presentation adapter for the Manager dashboard; lifecycle rules remain in the service. */
public final class ManagerDashboardController {
    private final ManagerRequestService requestService;
    private final AuthenticatedSession session;

    public ManagerDashboardController(
            ManagerRequestService requestService, AuthenticatedSession session) {
        this.requestService = Objects.requireNonNull(requestService, "requestService");
        this.session = Objects.requireNonNull(session, "session");
    }

    public List<MaintenanceRequest> loadRequests() {
        return requestService.listAllRequests(session);
    }

    public List<UserAccount> loadActiveTechnicians() {
        return requestService.listActiveTechnicians(session);
    }

    public MaintenanceRequest assign(
            MaintenanceRequest request, UserAccount technician, ManagerPriority priority) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(technician, "technician");
        return requestService.assignOpenRequest(session, request.id(), technician.id(), priority);
    }
}
