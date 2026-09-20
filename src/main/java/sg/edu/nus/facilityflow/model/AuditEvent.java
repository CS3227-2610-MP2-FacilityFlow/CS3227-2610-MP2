package sg.edu.nus.facilityflow.model;

import java.time.Instant;
import java.util.Objects;

public record AuditEvent(
        long requestId,
        long actorId,
        String action,
        String detail,
        Instant occurredAt) {

    public AuditEvent {
        if (requestId <= 0 || actorId <= 0) {
            throw new IllegalArgumentException("Audit subject and actor IDs must be positive");
        }
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
