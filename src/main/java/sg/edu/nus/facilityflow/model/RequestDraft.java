package sg.edu.nus.facilityflow.model;

/** Untrusted form input; contains no owner, status, or system-generated ID. */
public record RequestDraft(String title, String description, String location,
                           String category, ReportedUrgency urgency) {
    /** LIF-004: normalize boundaries without altering internal text. */
    public RequestDraft {
        title = normalize(title);
        description = normalize(description);
        location = normalize(location);
        category = normalize(category);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
