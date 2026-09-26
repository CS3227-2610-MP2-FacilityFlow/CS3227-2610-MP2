package sg.edu.nus.facilityflow.model;

import java.time.Instant;

/** One Manager-visible request event, including internal work and requester updates. */
public record ManagerHistoryEntry(
        String type,
        String actor,
        String description,
        Instant occurredAt) {
}
