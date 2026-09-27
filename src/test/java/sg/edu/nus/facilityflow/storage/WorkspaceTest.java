package sg.edu.nus.facilityflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.List;
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
    @DisplayName("AUT-008/010 initial workspace atomically seeds every lifecycle state with request audits")
    void seedsRepresentativeRequestsAcrossEveryLifecycleState() throws Exception {
        Workspace.open(directory);

        assertEquals(6, scalar("SELECT COUNT(DISTINCT status) FROM maintenance_requests"));
        for (String status : List.of(
                "OPEN", "ASSIGNED", "IN_PROGRESS", "COMPLETED", "CLOSED", "CANCELLED")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM maintenance_requests WHERE status='"
                    + status + "'"), status);
        }
        assertEquals(6, scalar("SELECT COUNT(*) FROM audit_events WHERE action='REQUEST_CREATED'"));
        assertEquals(6, scalar("SELECT COUNT(*) FROM maintenance_requests request "
                + "WHERE EXISTS (SELECT 1 FROM audit_events event "
                + "WHERE event.request_id=request.id AND event.target_type='REQUEST' "
                + "AND event.target_id=request.id)"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM work_logs"));

        Workspace.open(directory);
        assertEquals(6, scalar("SELECT COUNT(*) FROM maintenance_requests"));
        assertEquals(6, scalar("SELECT COUNT(*) FROM audit_events WHERE action='REQUEST_CREATED'"));
    }

    @Test
    @DisplayName("LIF-021/022 invalid categories stop before database creation")
    void rejectsInvalidCatalogueBeforeWritingDatabase() throws Exception {
        Files.writeString(directory.resolve("categories.properties"), "categories=Other,other\n");
        assertThrows(java.io.IOException.class, () -> Workspace.open(directory));
        assertFalse(Files.exists(directory.resolve("facilityflow.db")));
    }

    @Test
    @DisplayName("LIF-021/022 rename and removal mappings migrate requests with audit events")
    void migratesRenamedAndRemovedCategories() throws Exception {
        Workspace.open(directory);
        insertRequest("Electrical");
        insertRequest("Plumbing");
        Files.writeString(directory.resolve("categories.properties"), """
                categories=HVAC,Other
                renames=Electrical>HVAC
                removals=Plumbing
                """);

        var migrated = Workspace.open(directory);

        assertEquals(List.of("HVAC", "Other"), migrated.categories());
        assertEquals("HVAC", category(7));
        assertEquals("Other", category(8));
        assertEquals(2, scalar("SELECT COUNT(*) FROM audit_events WHERE action='CATEGORY_MIGRATED'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM audit_events WHERE action='CATEGORY_MIGRATED' AND actor_id=5"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM audit_events WHERE action='CATEGORY_MIGRATED' "
                + "AND target_type='REQUEST' AND request_id=target_id AND detail LIKE 'Category changed from %'"));
    }

    @Test
    @DisplayName("LIF-022 an invalid rename target leaves stored requests unchanged")
    void rejectsInvalidMappingWithoutChangingDatabase() throws Exception {
        Workspace.open(directory);
        insertRequest("Electrical");
        Files.writeString(directory.resolve("categories.properties"), """
                categories=Other
                renames=Electrical>Missing
                """);

        assertThrows(java.io.IOException.class, () -> Workspace.open(directory));
        assertEquals("Electrical", category(7));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events WHERE action='CATEGORY_MIGRATED'"));
    }

    @Test
    @DisplayName("LIF-022 an unmapped stored category fails startup without changing requests or audits")
    void rejectsUnmappedStoredCategoryWithoutChangingDatabase() throws Exception {
        Workspace.open(directory);
        insertRequest("Electrical");
        Files.writeString(directory.resolve("categories.properties"), "categories=Other\n");

        assertThrows(StorageException.class, () -> Workspace.open(directory));
        assertEquals("Electrical", category(7));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events WHERE action='CATEGORY_MIGRATED'"));
    }

    @Test
    @DisplayName("LIF-021 malformed mappings fail before changing an existing database")
    void rejectsMalformedMappingsWithoutChangingDatabase() throws Exception {
        Workspace.open(directory);
        insertRequest("Electrical");
        Files.writeString(directory.resolve("categories.properties"), """
                categories=Other
                renames=Electrical>Other>Extra
                """);

        assertThrows(java.io.IOException.class, () -> Workspace.open(directory));
        assertEquals("Electrical", category(7));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events WHERE action='CATEGORY_MIGRATED'"));
    }

    @Test
    @DisplayName("LIF-022 an audit failure rolls back all requests in a multi-request category migration")
    void rollsBackAllRequestsWhenMigrationAuditFails() throws Exception {
        Workspace.open(directory);
        insertRequest("Electrical");
        insertRequest("Electrical");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
                var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_second_category_audit BEFORE INSERT ON audit_events "
                    + "WHEN NEW.action='CATEGORY_MIGRATED' AND NEW.request_id=8 "
                    + "BEGIN SELECT RAISE(ABORT, 'fail'); END");
        }
        Files.writeString(directory.resolve("categories.properties"), """
                categories=HVAC,Other
                renames=Electrical>HVAC
                """);

        assertThrows(StorageException.class, () -> Workspace.open(directory));
        assertEquals(2, scalar("SELECT COUNT(*) FROM maintenance_requests WHERE category='Electrical'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events WHERE action='CATEGORY_MIGRATED'"));
    }

    @Test
    @DisplayName("LIF-022 category migration requires an active manager and rolls back when none exists")
    void rollsBackMigrationWhenNoActiveManagerCanAuditIt() throws Exception {
        Workspace.open(directory);
        insertRequest("Electrical");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
                var statement = connection.createStatement()) {
            statement.execute("UPDATE user_accounts SET active=0 WHERE role='FACILITIES_MANAGER'");
        }
        Files.writeString(directory.resolve("categories.properties"), """
                categories=HVAC,Other
                renames=Electrical>HVAC
                """);

        assertThrows(StorageException.class, () -> Workspace.open(directory));
        assertEquals("Electrical", category(7));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events WHERE action='CATEGORY_MIGRATED'"));
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

    private void insertRequest(String category) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
                var statement = connection.prepareStatement("""
                        INSERT INTO maintenance_requests(display_id, requester_id, title, description, location,
                            category, reported_urgency, status, created_at, updated_at)
                        VALUES (?, 1, 'Title', 'Description', 'Room 1', ?, 'MEDIUM', 'OPEN', '2026-09-26T00:00:00Z', '2026-09-26T00:00:00Z')
                        """)) {
            statement.setString(1, "FF-" + category + "-" + System.nanoTime());
            statement.setString(2, category);
            statement.executeUpdate();
        }
    }

    private String category(long requestId) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("facilityflow.db"));
                var statement = connection.prepareStatement("SELECT category FROM maintenance_requests WHERE id=?")) {
            statement.setLong(1, requestId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }
}
