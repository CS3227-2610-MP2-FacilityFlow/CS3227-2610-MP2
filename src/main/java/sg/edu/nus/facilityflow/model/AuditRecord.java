package sg.edu.nus.facilityflow.model;

import java.time.Instant;

/** Read-only enriched audit record used by the Manager audit viewer. */
public record AuditRecord(
        long id,
        Long requestId,
        String requestDisplayId,
        long actorId,
        String actorName,
        String action,
        String targetType,
        long targetId,
        String detail,
        Instant occurredAt) {
}
