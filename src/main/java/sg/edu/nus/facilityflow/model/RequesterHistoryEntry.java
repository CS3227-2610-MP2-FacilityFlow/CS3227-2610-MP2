package sg.edu.nus.facilityflow.model;

import java.time.Instant;

public record RequesterHistoryEntry(String kind, String text, Instant occurredAt) { }
