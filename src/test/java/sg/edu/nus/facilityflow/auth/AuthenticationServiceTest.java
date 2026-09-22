package sg.edu.nus.facilityflow.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.service.ValidationException;
import sg.edu.nus.facilityflow.storage.StorageException;

class AuthenticationServiceTest {
    @TempDir
    Path directory;
    private AuthFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new AuthFixture(directory);
    }

    @Test
    @DisplayName("AUT-011/014/017 DAT-016 case-insensitive login issues a session after safe audit and clears password")
    void logsInAndAudits() throws Exception {
        char[] password = "password".toCharArray();
        var session = fixture.auth.login(" OWNER ", password);
        assertEquals(Role.REQUESTER, fixture.auth.currentUser(session).role());
        assertTrue(fixture.requester.listOwnRequests(session).isEmpty());
        assertEquals("\0".repeat(8), new String(password));
        assertEquals(1, fixture.scalar("""
                SELECT COUNT(*) FROM audit_events WHERE action = 'LOGIN_SUCCEEDED'
                    AND actor_id = 1 AND target_type = 'ACCOUNT' AND target_id = 1
                    AND request_id IS NULL AND detail = '{}'
                """));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "inactive", "owner' OR 1=1 --"})
    @DisplayName("AUT-011/012/017/021 invalid and inactive logins disclose nothing and write no audits")
    void rejectsInvalidLogin(String username) throws Exception {
        char[] password = "password".toCharArray();
        var error = assertThrows(AuthorizationException.class, () -> fixture.auth.login(username, password));
        var wrong = assertThrows(AuthorizationException.class,
                () -> fixture.auth.login("owner", "incorrect".toCharArray()));
        assertEquals("Invalid username or password.", error.getMessage());
        assertEquals(error.getMessage(), wrong.getMessage());
        assertEquals("\0".repeat(8), new String(password));
        assertEquals(0, fixture.scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("AUT-015/016/018 fabricated, logged-out, foreign-process and shutdown sessions are rejected")
    void rejectsUnissuedAndRevokedSessions() {
        assertThrows(AuthorizationException.class, () -> fixture.auth.currentUser(new AuthenticatedSession(1, 0)));
        var session = fixture.auth.login("owner", "password".toCharArray());
        var restarted = new AuthenticationService(fixture.store, new SessionManager(), Clock.systemUTC());
        assertThrows(AuthorizationException.class, () -> restarted.currentUser(session));
        fixture.auth.logout(session);
        assertThrows(AuthorizationException.class, () -> fixture.requester.listOwnRequests(session));
        var manager = fixture.auth.login("manager", "password".toCharArray());
        fixture.sessions.close();
        assertThrows(AuthorizationException.class, () -> fixture.manager.listAllRequests(manager));
    }

    @Test
    @DisplayName("AUT-022 session stays revoked after deactivation is observed, even after reactivation")
    void revokesDeactivatedSession() throws Exception {
        var session = fixture.auth.login("owner", "password".toCharArray());
        fixture.execute("UPDATE user_accounts SET active = 0 WHERE id = 1");
        assertThrows(AuthorizationException.class, () -> fixture.requester.listOwnRequests(session));
        fixture.execute("UPDATE user_accounts SET active = 1 WHERE id = 1");
        assertThrows(AuthorizationException.class, () -> fixture.auth.currentUser(session));
        assertEquals(1, fixture.auth.login("owner", "password".toCharArray()).accountId());
    }

    @Test
    @DisplayName("AUT-032 role changes retain the session but immediately reject old-role operations")
    void retainsSessionAcrossRoleChange() throws Exception {
        var session = fixture.auth.login("owner", "password".toCharArray());
        fixture.execute("UPDATE user_accounts SET role = 'FACILITIES_MANAGER' WHERE id = 1");
        assertThrows(AuthorizationException.class, () -> fixture.requester.listOwnRequests(session));
        assertEquals(Role.FACILITIES_MANAGER, fixture.auth.currentUser(session).role());
        assertTrue(fixture.manager.listAllRequests(session).isEmpty());
    }

    @Test
    @DisplayName("AUT-030/033 own-password change retains the session; Manager reset invalidates all target sessions")
    void changesAndResetsPasswords() throws Exception {
        var first = fixture.auth.login("owner", "password".toCharArray());
        var second = fixture.auth.login("owner", "password".toCharArray());
        char[] replacement = "new-password".toCharArray();
        fixture.auth.changePassword(first, "password".toCharArray(), replacement);
        assertEquals("\0".repeat(12), new String(replacement));
        assertEquals(1, fixture.auth.currentUser(first).id());
        assertThrows(AuthorizationException.class, () -> fixture.auth.login("owner", "password".toCharArray()));
        var manager = fixture.auth.login("manager", "password".toCharArray());
        fixture.auth.resetPassword(manager, 1, "reset-password".toCharArray());
        assertThrows(AuthorizationException.class, () -> fixture.auth.currentUser(first));
        assertThrows(AuthorizationException.class, () -> fixture.requester.listOwnRequests(second));
        assertEquals(1, fixture.auth.login("owner", "reset-password".toCharArray()).accountId());
        assertEquals(1, fixture.scalar("SELECT session_version FROM user_accounts WHERE id = 1"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'PASSWORD_CHANGED'"));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'PASSWORD_RESET'"));
    }

    @Test
    @DisplayName("AUT-018/030 rejects wrong-role resets, own resets, bad current password and invalid replacements")
    void rejectsForbiddenPasswordChanges() {
        var owner = fixture.auth.login("owner", "password".toCharArray());
        var manager = fixture.auth.login("manager", "password".toCharArray());
        assertThrows(AuthorizationException.class,
                () -> fixture.auth.resetPassword(owner, 2, "replacement".toCharArray()));
        assertThrows(ValidationException.class,
                () -> fixture.auth.resetPassword(manager, 3, "replacement".toCharArray()));
        assertThrows(ValidationException.class,
                () -> fixture.auth.changePassword(owner, "incorrect".toCharArray(), "replacement".toCharArray()));
        assertThrows(ValidationException.class,
                () -> fixture.auth.resetPassword(manager, 1, "short".toCharArray()));
        assertEquals(1, fixture.auth.login("owner", "password".toCharArray()).accountId());
    }

    @Test
    @DisplayName("DAT-007 AUT-030 login/reset audits must commit before issuing or invalidating sessions")
    void rollsBackAuditFailures() throws Exception {
        var owner = fixture.auth.login("owner", "password".toCharArray());
        var manager = fixture.auth.login("manager", "password".toCharArray());
        fixture.execute("CREATE TRIGGER reject_audit BEFORE INSERT ON audit_events BEGIN SELECT RAISE(ABORT, 'fail'); END");
        char[] password = "password".toCharArray();
        assertThrows(StorageException.class, () -> fixture.auth.login("other", password));
        assertEquals("\0".repeat(8), new String(password));
        assertThrows(StorageException.class,
                () -> fixture.auth.resetPassword(manager, 1, "replacement".toCharArray()));
        assertThrows(StorageException.class,
                () -> fixture.auth.changePassword(owner, "password".toCharArray(), "replacement".toCharArray()));
        assertEquals(0, fixture.scalar("SELECT session_version FROM user_accounts WHERE id = 1"));
        assertEquals(1, fixture.auth.currentUser(owner).id());
        fixture.execute("DROP TRIGGER reject_audit");
        assertEquals(1, fixture.auth.login("owner", "password".toCharArray()).accountId());
    }

    @Test
    @DisplayName("AUT-004/005 hashes have independent salts, boundaries allow no composition rule, malformed hashes fail")
    void verifiesPasswordPolicyAndHashing() {
        var first = PasswordHasher.hash("a".repeat(8).toCharArray());
        var second = PasswordHasher.hash("a".repeat(8).toCharArray());
        assertNotEquals(first, second);
        assertTrue(PasswordHasher.matches("a".repeat(8).toCharArray(), first));
        assertFalse(PasswordHasher.matches("wrong-password".toCharArray(), first));
        assertFalse(PasswordHasher.matches("password".toCharArray(), "malformed"));
        assertTrue(PasswordHasher.matches("😀".repeat(24).toCharArray(), PasswordHasher.hash("😀".repeat(24).toCharArray())));
        assertThrows(ValidationException.class, () -> PasswordHasher.hash("a".repeat(7).toCharArray()));
        assertThrows(ValidationException.class, () -> PasswordHasher.hash("a".repeat(25).toCharArray()));
    }
}
