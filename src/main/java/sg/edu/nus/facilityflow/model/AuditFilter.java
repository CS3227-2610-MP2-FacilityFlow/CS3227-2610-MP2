package sg.edu.nus.facilityflow.model;

import java.time.LocalDate;

public record AuditFilter(
        String requestQuery,
        String actorQuery,
        String actionQuery,
        LocalDate from,
        LocalDate through) {

    public static AuditFilter empty() {
        return new AuditFilter(null, null, null, null, null);
    }
}
