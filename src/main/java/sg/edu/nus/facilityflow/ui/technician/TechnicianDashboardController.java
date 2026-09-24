package sg.edu.nus.facilityflow.ui.technician;

import java.util.List;
import java.util.Objects;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.service.TechnicianRequestService;

/** Presentation adapter for the Technician dashboard; lifecycle rules remain in the service. */
public final class TechnicianDashboardController {
    private final TechnicianRequestService requestService;
    private final AuthenticatedSession session;

    public TechnicianDashboardController(
            TechnicianRequestService requestService, AuthenticatedSession session) {
        this.requestService = Objects.requireNonNull(requestService, "requestService");
        this.session = Objects.requireNonNull(session, "session");
    }

    public List<MaintenanceRequest> loadRequests() {
        return requestService.listAssignedRequests(session);
    }

    public MaintenanceRequest startWork(MaintenanceRequest request) {
        Objects.requireNonNull(request, "request");
        return requestService.startWork(session, request.id());
    }
}
