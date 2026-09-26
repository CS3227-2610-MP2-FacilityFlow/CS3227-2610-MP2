package sg.edu.nus.facilityflow.auth;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.service.ValidationException;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** Login and password changes consume and clear supplied password arrays. */
public final class AuthenticationService {
    private static final System.Logger LOG = System.getLogger(AuthenticationService.class.getName());
    // Unknown users still perform the same PBKDF2 work; this value never authenticates an account.
    private static final String DUMMY_HASH = "pbkdf2-sha256$600000$AAAAAAAAAAAAAAAAAAAAAA==$"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private final ManagerAssignmentStore store;
    private final SessionManager sessions;
    private final Clock clock;

    public AuthenticationService(ManagerAssignmentStore store, SessionManager sessions, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.sessions = Objects.requireNonNull(sessions);
        this.clock = Objects.requireNonNull(clock);
    }

    public AuthenticatedSession login(String username, char[] password) {
        try {
            var account = store.inTransaction(transaction -> {
                var credentials = transaction.findCredentials(username == null ? "" : username.strip());
                boolean matches = PasswordHasher.matches(password,
                        credentials.map(value -> value.passwordHash()).orElse(DUMMY_HASH));
                if (!matches || credentials.isEmpty() || !credentials.get().account().active()) {
                    LOG.log(System.Logger.Level.INFO, "event=login_failed component=authentication");
                    throw new AuthorizationException("Invalid username or password.");
                }
                var user = credentials.get().account();
                transaction.appendAccountAudit(user.id(), user.id(), "LOGIN_SUCCEEDED", clock.instant());
                return user;
            });
            return sessions.open(account);
        } finally {
            clear(password);
        }
    }

    public UserAccount currentUser(AuthenticatedSession session) {
        return store.inTransaction(transaction -> sessions.requireAccount(transaction, session));
    }

    public void logout(AuthenticatedSession session) {
        sessions.logout(session);
    }

    public void changePassword(AuthenticatedSession session, char[] current, char[] replacement) {
        try {
            store.inTransaction(transaction -> {
                var actor = sessions.requireAccount(transaction, session);
                var credentials = transaction.findCredentials(actor.username()).orElseThrow();
                if (!PasswordHasher.matches(current, credentials.passwordHash())) {
                    throw new ValidationException("Current password is incorrect.");
                }
                transaction.updatePassword(actor.id(), PasswordHasher.hash(replacement), false, clock.instant());
                transaction.appendAccountAudit(
                        actor.id(), actor.id(), "PASSWORD_CHANGED",
                        "{\"fields\":[\"password_hash\"]}", clock.instant());
                return null;
            });
        } finally {
            clear(current);
            clear(replacement);
        }
    }

    public void resetPassword(AuthenticatedSession session, long targetId, char[] replacement) {
        try {
            store.inTransaction(transaction -> {
                var actor = sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
                if (actor.id() == targetId || transaction.findAccount(targetId).isEmpty()) {
                    throw new ValidationException("Select another existing account to reset.");
                }
                transaction.updatePassword(targetId, PasswordHasher.hash(replacement), true, clock.instant());
                transaction.appendAccountAudit(
                        actor.id(), targetId, "PASSWORD_RESET",
                        "{\"fields\":[\"password_hash\",\"session_version\"]}",
                        clock.instant());
                return null;
            });
        } finally {
            clear(replacement);
        }
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
