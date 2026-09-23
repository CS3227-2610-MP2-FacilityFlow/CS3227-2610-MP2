package sg.edu.nus.facilityflow.auth;

import java.time.Instant;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;

/** Test-only trusted issuer for pre-authentication service fixtures. Not packaged with the app. */
public final class TestSessions {
    public static final SessionManager MANAGER = new SessionManager();

    private TestSessions() {
    }

    public static AuthenticatedSession issue(long id) {
        return MANAGER.open(new UserAccount(id, "fixture", "Fixture", Role.REQUESTER,
                true, Instant.EPOCH, Instant.EPOCH));
    }
}
