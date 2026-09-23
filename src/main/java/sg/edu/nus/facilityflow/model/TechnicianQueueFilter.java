package sg.edu.nus.facilityflow.model;

/** Optional TEC-002 search and enum/category filters for a Technician queue. */
public record TechnicianQueueFilter(
        String query,
        RequestStatus status,
        String category,
        ManagerPriority priority) {

    public TechnicianQueueFilter {
        query = normalizeOptional(query);
        category = normalizeOptional(category);
    }

    public static TechnicianQueueFilter none() {
        return new TechnicianQueueFilter(null, null, null, null);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
