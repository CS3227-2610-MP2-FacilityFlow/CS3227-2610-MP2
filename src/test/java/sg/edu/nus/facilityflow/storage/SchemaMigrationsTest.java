package sg.edu.nus.facilityflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sg.edu.nus.facilityflow.auth.TestSessions;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.service.RequesterRequestService;
import sg.edu.nus.facilityflow.service.RequestValidator;

class SchemaMigrationsTest {
    @TempDir
    Path directory;
    private String jdbcUrl;
    private SQLiteManagerAssignmentStore store;

    @BeforeEach
    void setUp() {
        jdbcUrl = "jdbc:sqlite:" + directory.resolve("migration.db");
        store = new SQLiteManagerAssignmentStore(jdbcUrl);
    }

    @Test
    @DisplayName("DAT-003/004 creates schema version 5 and repeated initialization is idempotent")
    void initializesNewDatabase() throws SQLException {
        store.initializeSchema();
        store.initializeSchema();
        assertEquals(5, scalar("PRAGMA user_version"));
        assertEquals(1, scalar("SELECT next_value FROM request_identity_sequence"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM maintenance_requests"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'work_logs'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'requester_updates'"));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'index' AND name = 'maintenance_requests_technician_queue'
                """));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("DAT-003/004/012/015 migrates legacy Manager records without changing identities or audits")
    void preservesLegacyData(int version) throws SQLException, IOException {
        createLegacyWorkspace();
        if (version == 1) {
            execute("PRAGMA user_version = 1");
        }
        store.initializeSchema();
        store.initializeSchema();
        assertEquals(5, scalar("PRAGMA user_version"));
        assertEquals(21, scalar("SELECT next_value FROM request_identity_sequence"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM user_accounts WHERE password_hash = 'unchanged-hash'"));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM maintenance_requests
                WHERE id = 10 AND display_id = 'FF-000020' AND requester_id = 1
                    AND title = 'Leaking pipe' AND description = 'Water leaks below the sink.'
                    AND status = 'OPEN'
                """));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM audit_events
                WHERE id = 7 AND request_id = 10 AND actor_id = 1 AND action = 'LEGACY_EVENT'
                    AND detail = 'Historical detail preserved' AND occurred_at = '2026-09-18T00:00:00Z'
                    AND target_type = 'REQUEST' AND target_id = 10
                """));
        var service = new RequesterRequestService(store, new RequestValidator(Set.of("Plumbing")),
                Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC), TestSessions.MANAGER);
        var created = service.createRequest(TestSessions.issue(1), new RequestDraft(
                "Leaking again", "Water leaks below another sink.", "Room 12", "Plumbing", ReportedUrgency.HIGH));
        assertEquals("FF-000021", created.displayId());
        assertEquals(21, created.id());
        assertEquals(2, service.listOwnRequests(TestSessions.issue(1)).size());
    }

    @Test
    @DisplayName("DAT-003/004/015 version 2 upgrades to account audits and session versions without losing prior data")
    void upgradesVersionTwo() throws Exception {
        createLegacyWorkspace();
        try (var resource = getClass().getResourceAsStream("/version-two-migration.sql")) {
            if (resource == null) {
                throw new IOException("Missing version-two fixture");
            }
            for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split("-- migration-statement")) {
                execute(sql);
            }
        }
        store.initializeSchema();
        assertEquals(5, scalar("PRAGMA user_version"));
        assertEquals(0, scalar("SELECT session_version FROM user_accounts WHERE id = 1"));
        assertEquals(21, scalar("SELECT next_value FROM request_identity_sequence"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE id = 7 AND target_id = 10"));
        store.inTransaction(transaction -> {
            transaction.appendAccountAudit(1, 1, "LOGIN_SUCCEEDED", Instant.parse("2026-09-22T00:00:00Z"));
            return null;
        });
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE target_type = 'ACCOUNT' AND request_id IS NULL"));
    }

    @Test
    @DisplayName("DAT-003/004 TEC-003 version 3 upgrade preserves unknown assignment time as NULL")
    void upgradesVersionThree() throws Exception {
        createVersionThreeWorkspace();
        execute("""
                UPDATE maintenance_requests
                SET manager_priority = 'HIGH', status = 'ASSIGNED', assignee_id = 2,
                    updated_at = '2026-09-18T01:00:00Z'
                WHERE id = 10
                """);

        store.initializeSchema();

        assertEquals(5, scalar("PRAGMA user_version"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM maintenance_requests WHERE id = 10"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE id = 7"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'work_logs'"));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM pragma_table_info('maintenance_requests')
                WHERE name = 'resolution_summary'
                """));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM pragma_table_info('maintenance_requests')
                WHERE name = 'completed_at'
                """));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM maintenance_requests
                WHERE id = 10 AND assigned_at IS NULL
                    AND updated_at = '2026-09-18T01:00:00Z'
                """));
    }

    @Test
    @DisplayName("DAT-003/007 failed version 4 migration rolls back columns, tables and version")
    void rollsBackFailedVersionFourMigration() throws Exception {
        createVersionThreeWorkspace();
        execute("CREATE TABLE work_logs (id INTEGER PRIMARY KEY)");

        assertThrows(StorageException.class, store::initializeSchema);

        assertEquals(3, scalar("PRAGMA user_version"));
        assertEquals(0, scalar("""
                SELECT COUNT(*) FROM pragma_table_info('maintenance_requests')
                WHERE name IN ('assigned_at', 'resolution_summary', 'completed_at')
                """));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'work_logs'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM maintenance_requests WHERE id = 10"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE id = 7"));
    }

    @Test
    @DisplayName("LIF-002 migration sequence follows explicit demo IDs inserted after startup")
    void advancesSequenceForDemoInserts() throws SQLException, IOException {
        createLegacyWorkspace();
        store.initializeSchema();
        execute("""
                INSERT INTO maintenance_requests(
                    id, display_id, requester_id, title, description, location, category,
                    reported_urgency, manager_priority, status, assignee_id, created_at, updated_at)
                SELECT 50, 'FF-000075', requester_id, title, description, location, category,
                        reported_urgency, manager_priority, status, assignee_id, created_at, updated_at
                FROM maintenance_requests WHERE id = 10
                """);
        assertEquals(76, scalar("SELECT next_value FROM request_identity_sequence"));
        store.initializeSchema();
        assertEquals(76, scalar("SELECT next_value FROM request_identity_sequence"));
    }

    @Test
    @DisplayName("DAT-004 rejects a future schema before changing existing data or version")
    void rejectsUnknownVersion() throws SQLException {
        execute("PRAGMA user_version = 99");
        var failure = assertThrows(StorageException.class, store::initializeSchema);
        assertTrue(failure.getMessage().contains("compatible app version"));
        assertEquals(99, scalar("PRAGMA user_version"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'"));
    }

    @Test
    @DisplayName("DAT-003/007 failed migration rolls back DDL and version while preserving legacy rows")
    void rollsBackFailedMigration() throws SQLException, IOException {
        createLegacyWorkspace();
        execute("ALTER TABLE audit_events ADD COLUMN target_type TEXT");
        assertThrows(StorageException.class, store::initializeSchema);
        assertEquals(0, scalar("PRAGMA user_version"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'request_identity_sequence'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'advance_request_identity'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM maintenance_requests WHERE id = 10"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE id = 7"));
    }

    @Test
    @DisplayName("DAT-004 rejects an incompatible legacy schema and does not stamp it as current")
    void rejectsIncompatibleLegacySchema() throws SQLException {
        execute("CREATE TABLE user_accounts (id INTEGER PRIMARY KEY)");
        assertThrows(StorageException.class, store::initializeSchema);
        assertEquals(0, scalar("PRAGMA user_version"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'"));
    }

    @Test
    @DisplayName("DAT-004 rejects a damaged current schema instead of resetting its sequence")
    void rejectsMissingSequence() throws SQLException {
        store.initializeSchema();
        execute("DELETE FROM request_identity_sequence");
        assertThrows(StorageException.class, store::initializeSchema);
        assertEquals(0, scalar("SELECT COUNT(*) FROM request_identity_sequence"));
        assertEquals(5, scalar("PRAGMA user_version"));
    }

    private void createVersionThreeWorkspace() throws Exception {
        createLegacyWorkspace();
        applyFixture("/version-two-migration.sql");
        applyFixture("/version-three-migration.sql");
        execute("""
                INSERT INTO user_accounts VALUES
                    (2, 'technician', 'Technician', 'TECHNICIAN', 'unchanged-hash', 1,
                        '2026-09-18T00:00:00Z', '2026-09-18T00:00:00Z', 0)
                """);
    }

    private void applyFixture(String resourceName) throws Exception {
        try (var resource = getClass().getResourceAsStream(resourceName)) {
            if (resource == null) {
                throw new IOException("Missing migration fixture " + resourceName);
            }
            for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                    .split("-- migration-statement")) {
                execute(sql);
            }
        }
    }

    private void createLegacyWorkspace() throws IOException, SQLException {
        try (var resource = getClass().getResourceAsStream("/legacy-manager-schema.sql")) {
            if (resource == null) {
                throw new IOException("Missing legacy schema fixture");
            }
            for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) {
                    execute(sql);
                }
            }
        }
        execute("""
                INSERT INTO user_accounts VALUES
                    (1, 'owner', 'Owner', 'REQUESTER', 'unchanged-hash', 1,
                        '2026-09-18T00:00:00Z', '2026-09-18T00:00:00Z')
                """);
        execute("""
                INSERT INTO maintenance_requests VALUES
                    (10, 'FF-000020', 1, 'Leaking pipe', 'Water leaks below the sink.', 'Room 12',
                        'Plumbing', 'HIGH', NULL, 'OPEN', NULL, '2026-09-18T00:00:00Z', '2026-09-18T00:00:00Z')
                """);
        execute("""
                INSERT INTO audit_events VALUES
                    (7, 10, 1, 'LEGACY_EVENT', 'Historical detail preserved', '2026-09-18T00:00:00Z')
                """);
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long scalar(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
