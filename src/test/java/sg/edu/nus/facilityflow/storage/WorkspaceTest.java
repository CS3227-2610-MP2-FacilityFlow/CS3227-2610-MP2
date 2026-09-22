package sg.edu.nus.facilityflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sg.edu.nus.facilityflow.auth.AuthenticationService;
import sg.edu.nus.facilityflow.auth.SessionManager;

class WorkspaceTest {
    @TempDir
    Path directory;

    @Test
    @DisplayName("AUT-007/009/010 DAT-001 startup seeds six independently salted accounts once and supports real login")
    void seedsOnce() throws Exception {
        var workspace = Workspace.open(directory);
        var auth = new AuthenticationService(workspace.store(), new SessionManager(), Clock.systemUTC());
        for (String name : new String[] {"requester1", "requester2", "technician1", "technician2", "manager1", "manager2"}) {
            var session = auth.login(name, "Welcome123".toCharArray());
            assertEquals(name, auth.currentUser(session).username());
            auth.logout(session);
        }
        var reopened = Workspace.open(directory);
        assertEquals(workspace.categories(), reopened.categories());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*), COUNT(DISTINCT password_hash) FROM user_accounts")) {
            assertTrue(result.next());
            assertEquals(6, result.getInt(1));
            assertEquals(6, result.getInt(2));
        }
        assertEquals(6, scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'ACCOUNT_CREATED'"));
        assertEquals(6, scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'LOGIN_SUCCEEDED'"));
    }

    @Test
    @DisplayName("LIF-021/022 invalid categories stop before database creation")
    void rejectsInvalidCatalogueBeforeWritingDatabase() throws Exception {
        Files.writeString(directory.resolve("categories.properties"), "categories=Other,other\n");
        assertThrows(java.io.IOException.class, () -> Workspace.open(directory));
        assertFalse(Files.exists(directory.resolve("facilityflow.db")));
    }

    @Test
    @DisplayName("AUT-010 failed account audit leaves no partial demo accounts after startup transaction rollback")
    void rollsBackSeedFailure() throws Exception {
        var url = "jdbc:sqlite:" + directory.resolve("facilityflow.db");
        new SQLiteManagerAssignmentStore(url).initializeSchema();
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_seed_audit BEFORE INSERT ON audit_events BEGIN SELECT RAISE(ABORT, 'fail'); END");
            connection.setAutoCommit(false);
            assertThrows(java.sql.SQLException.class, () -> DemoWorkspace.seed(connection));
            connection.rollback();
        }
        assertEquals(0, scalar("SELECT COUNT(*) FROM user_accounts"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("AUT-009 an existing empty schema is not reseeded")
    void doesNotReseedExistingWorkspace() throws Exception {
        var store = new SQLiteManagerAssignmentStore("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
        store.initializeSchema();
        Workspace.open(directory);
        assertEquals(0, scalar("SELECT COUNT(*) FROM user_accounts"));
    }

    private long scalar(String sql) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
                var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
