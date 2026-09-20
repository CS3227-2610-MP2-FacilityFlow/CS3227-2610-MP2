package sg.edu.nus.facilityflow.auth;

public record AuthenticatedSession(long accountId) {
    public AuthenticatedSession {
        if (accountId <= 0) {
            throw new IllegalArgumentException("Session account ID must be positive");
        }
    }
}
