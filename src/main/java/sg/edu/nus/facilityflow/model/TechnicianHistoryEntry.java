package sg.edu.nus.facilityflow.model;

import java.time.Instant;

/** One chronological Technician-visible entry, clearly distinguishing its source. */
public record TechnicianHistoryEntry(
        String type,
        long authorId,
        String text,
        Integer minutesSpent,
        Instant occurredAt) {
}
