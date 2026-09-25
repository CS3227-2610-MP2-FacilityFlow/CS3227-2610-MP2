package sg.edu.nus.facilityflow.model;

import java.time.Instant;

public record RequesterUpdate(long id, long requestId, long authorId, String text, Instant createdAt) { }
