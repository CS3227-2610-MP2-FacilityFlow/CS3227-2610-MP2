package sg.edu.nus.facilityflow.model;

import java.time.Instant;
import java.util.Objects;

public record UserAccount(
        long id,
        String username,
        String displayName,
        Role role,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public UserAccount {
        if (id <= 0) {
            throw new IllegalArgumentException("Account ID must be positive");
        }
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
