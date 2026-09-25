package sg.edu.nus.facilityflow.ui.technician;

import java.util.List;
import java.util.Objects;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.model.TechnicianDashboardCounts;
import sg.edu.nus.facilityflow.model.TechnicianQueueFilter;
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

    public List<MaintenanceRequest> loadRequests(TechnicianQueueFilter filter) {
        return requestService.listAssignedRequests(session, filter);
    }

    public TechnicianDashboardCounts loadDashboardCounts() {
        return requestService.getDashboardCounts(session);
    }

    public MaintenanceRequest startWork(MaintenanceRequest request) {
        Objects.requireNonNull(request, "request");
        return requestService.startWork(session, request.id());
    }

    public List<WorkLog> loadWorkLogs(MaintenanceRequest request) {
        Objects.requireNonNull(request, "request");
        return requestService.listWorkLogs(session, request.id());
    }

    public WorkLog addWorkLog(MaintenanceRequest request, String note, int minutesSpent) {
        Objects.requireNonNull(request, "request");
        return requestService.addWorkLog(session, request.id(), note, minutesSpent);
    }

    public MaintenanceRequest completeWork(MaintenanceRequest request, String resolutionSummary) {
        Objects.requireNonNull(request, "request");
        return requestService.completeWork(session, request.id(), resolutionSummary);
    }
}
