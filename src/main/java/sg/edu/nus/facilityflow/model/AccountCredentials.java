package sg.edu.nus.facilityflow.model;

/** Storage/authentication-only data; never pass to a view or diagnostic message. */
public record AccountCredentials(UserAccount account, String passwordHash) {
    @Override
    public String toString() {
        return "AccountCredentials[redacted]";
    }
}
