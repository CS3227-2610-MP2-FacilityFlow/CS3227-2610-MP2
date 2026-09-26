package sg.edu.nus.facilityflow.model;

import java.util.List;
import java.util.Map;

public record ManagerDashboardSummary(
        Map<RequestStatus, Integer> statusCounts,
        Map<ManagerPriority, Integer> priorityCounts,
        List<TechnicianWorkload> technicianWorkloads) {

    public ManagerDashboardSummary {
        statusCounts = Map.copyOf(statusCounts);
        priorityCounts = Map.copyOf(priorityCounts);
        technicianWorkloads = List.copyOf(technicianWorkloads);
    }
}
