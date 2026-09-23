package sg.edu.nus.facilityflow.model;

import java.time.Instant;
import java.util.Objects;

/** LIF-007: immutable Technician-authored evidence of work performed. */
public record WorkLog(
        long id,
        long requestId,
        long authorId,
        String note,
        int minutesSpent,
        Instant createdAt) {

    public WorkLog {
        if (id <= 0 || requestId <= 0 || authorId <= 0) {
            throw new IllegalArgumentException("Work-log, request and author IDs must be positive");
        }
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(createdAt, "createdAt");
        int noteLength = note.codePointCount(0, note.length());
        if (note.isBlank() || noteLength > 1_000) {
            throw new IllegalArgumentException("Work-log note must contain 1 to 1,000 characters");
        }
        if (minutesSpent < 1 || minutesSpent > 1_440) {
            throw new IllegalArgumentException("Minutes spent must be from 1 to 1,440");
        }
    }
}
