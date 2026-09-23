package sg.edu.nus.facilityflow.auth;

/** Opaque, process-local capability. Only the authentication package may issue it. */
public final class AuthenticatedSession {
    private final long accountId;
    private final long version;

    AuthenticatedSession(long accountId, long version) {
        this.accountId = accountId;
        this.version = version;
    }

    public long accountId() {
        return accountId;
    }

    long version() {
        return version;
    }
}
