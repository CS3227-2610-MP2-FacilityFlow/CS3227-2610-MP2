package sg.edu.nus.facilityflow.model;

/** TEC-001: counts for the current Technician's active personal queue. */
public record TechnicianDashboardCounts(
        int assignedCount,
        int inProgressCount,
        int completedCount) {

    public TechnicianDashboardCounts {
        if (assignedCount < 0 || inProgressCount < 0 || completedCount < 0) {
            throw new IllegalArgumentException("Dashboard counts cannot be negative");
        }
    }

    /** Number of requests currently awaiting Technician work or Manager review. */
    public int totalCount() {
        return assignedCount + inProgressCount + completedCount;
    }
}
