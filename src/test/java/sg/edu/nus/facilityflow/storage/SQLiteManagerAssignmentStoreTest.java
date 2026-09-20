package sg.edu.nus.facilityflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.service.ManagerRequestService;

class SQLiteManagerAssignmentStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    private String jdbcUrl;
    private SQLiteManagerAssignmentStore store;

    @BeforeEach
    void setUp() throws SQLException {
        jdbcUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("facilityflow-test.db");
        store = new SQLiteManagerAssignmentStore(jdbcUrl);
        store.initializeSchema();
        insertAccount(1, "manager", "Manager", "FACILITIES_MANAGER", true);
        insertAccount(2, "technician", "Technician", "TECHNICIAN", true);
        insertAccount(3, "requester", "Requester", "REQUESTER", true);
        insertOpenRequest();
    }

    @Test
    @DisplayName("LIF-012 QLT-003 rolls back request update when audit insertion fails")
    void rollsBackAssignmentWhenAuditInsertFails() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER reject_audit_insert
                    BEFORE INSERT ON audit_events
                    BEGIN
                        SELECT RAISE(ABORT, 'injected audit failure');
                    END
                    """);
        }
        ManagerRequestService service = new ManagerRequestService(
                store, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(
                StorageException.class,
                () -> service.assignOpenRequest(
                        new AuthenticatedSession(1), 10, 2, ManagerPriority.CRITICAL));

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement request = connection.prepareStatement(
                        "SELECT status, manager_priority, assignee_id FROM maintenance_requests WHERE id = 10");
                ResultSet result = request.executeQuery()) {
            assertEquals("OPEN", result.getString("status"));
            assertNull(result.getString("manager_priority"));
            assertNull(result.getObject("assignee_id"));
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement audit = connection.prepareStatement(
                        "SELECT COUNT(*) FROM audit_events");
                ResultSet result = audit.executeQuery()) {
            assertEquals(0, result.getInt(1));
        }
    }

    @Test
    @DisplayName("MGR-005 LIF-012 QLT-003 commits assignment and one audit event")
    void commitsAssignmentAndAuditTogether() throws SQLException {
        ManagerRequestService service = new ManagerRequestService(
                store, Clock.fixed(NOW, ZoneOffset.UTC));

        service.assignOpenRequest(
                new AuthenticatedSession(1), 10, 2, ManagerPriority.HIGH);

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement request = connection.prepareStatement(
                        "SELECT status, manager_priority, assignee_id FROM maintenance_requests WHERE id = 10");
                ResultSet result = request.executeQuery()) {
            assertEquals("ASSIGNED", result.getString("status"));
            assertEquals("HIGH", result.getString("manager_priority"));
            assertEquals(2, result.getLong("assignee_id"));
        }
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement audit = connection.prepareStatement(
                        "SELECT action, actor_id FROM audit_events");
                ResultSet result = audit.executeQuery()) {
            assertEquals(true, result.next());
            assertEquals("REQUEST_ASSIGNED", result.getString("action"));
            assertEquals(1, result.getLong("actor_id"));
            assertEquals(false, result.next());
        }
    }

    private void insertAccount(long id, String username, String displayName, String role, boolean active)
            throws SQLException {
        String sql = """
                INSERT INTO user_accounts(
                    id, username, display_name, role, password_hash, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'test-hash', ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, username);
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.setInt(5, active ? 1 : 0);
            statement.setString(6, NOW.minusSeconds(3600).toString());
            statement.setString(7, NOW.minusSeconds(3600).toString());
            statement.executeUpdate();
        }
    }

    private void insertOpenRequest() throws SQLException {
        String sql = """
                INSERT INTO maintenance_requests(
                    id, display_id, requester_id, title, description, location, category,
                    reported_urgency, manager_priority, status, assignee_id, created_at, updated_at)
                VALUES (10, 'FF-000010', 3, 'Leaking pipe',
                    'Water is leaking below the sink.', 'Block A pantry', 'Plumbing',
                    'HIGH', NULL, 'OPEN', NULL, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, NOW.minusSeconds(1800).toString());
            statement.setString(2, NOW.minusSeconds(1800).toString());
            statement.executeUpdate();
        }
    }
}
