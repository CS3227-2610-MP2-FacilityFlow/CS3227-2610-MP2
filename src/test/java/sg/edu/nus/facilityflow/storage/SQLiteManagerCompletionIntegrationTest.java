package sg.edu.nus.facilityflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sg.edu.nus.facilityflow.auth.TestSessions;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.service.ManagerAccountService;
import sg.edu.nus.facilityflow.service.ManagerRequestService;

class SQLiteManagerCompletionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @TempDir
    Path directory;

    private String url;
    private ManagerRequestService requests;
    private ManagerAccountService accounts;

    @BeforeEach
    void setUp() throws Exception {
        url = "jdbc:sqlite:" + directory.resolve("manager-completion.db");
        var store = new SQLiteManagerAssignmentStore(url);
        store.initializeSchema();
        insertAccount(1, "manager-a", "FACILITIES_MANAGER", true);
        insertAccount(2, "technician-a", "TECHNICIAN", true);
        insertAccount(3, "requester-a", "REQUESTER", true);
        insertAccount(4, "manager-b", "FACILITIES_MANAGER", true);
        requests = new ManagerRequestService(
                store, Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER);
        accounts = new ManagerAccountService(
                store, Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER);
    }

    @Test
    @DisplayName("MGR-007 LIF-012 DAT-007 commits close and exactly one transition audit atomically")
    void commitsCloseAndAuditTogether() throws Exception {
        insertRequest(10, RequestStatus.COMPLETED, 2L, "Repair completed safely.", NOW.minusSeconds(60));

        requests.closeCompletedRequest(TestSessions.issue(1), 10);

        assertEquals("CLOSED", text("SELECT status FROM maintenance_requests WHERE id=10"));
        assertEquals("Repair completed safely.",
                text("SELECT resolution_summary FROM maintenance_requests WHERE id=10"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events "
                + "WHERE request_id=10 AND action='REQUEST_CLOSED' AND actor_id=1"));
    }

    @Test
    @DisplayName("MGR-008 LIF-012 DAT-007 rolls back return when its audit insert fails")
    void rollsBackReturnWhenAuditInsertFails() throws Exception {
        insertRequest(10, RequestStatus.COMPLETED, 2L, "Repair completed safely.", NOW.minusSeconds(60));
        execute("CREATE TRIGGER reject_return_audit BEFORE INSERT ON audit_events "
                + "WHEN NEW.action='REQUEST_RETURNED' BEGIN SELECT RAISE(ABORT, 'fail'); END");

        assertThrows(StorageException.class, () -> requests.returnCompletedRequest(
                TestSessions.issue(1), 10, 2, "Further inspection required"));

        assertEquals("COMPLETED", text("SELECT status FROM maintenance_requests WHERE id=10"));
        assertEquals(2, scalar("SELECT assignee_id FROM maintenance_requests WHERE id=10"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("MGR-019 DAT-016/019 records ownership and a safe creation audit in one transaction")
    void recordsRequestOnBehalfWithSafeAudit() throws Exception {
        RequestDraft draft = new RequestDraft(
                "Private leak title", "Private description of the reported leak.",
                "Private office 12", "Plumbing", ReportedUrgency.HIGH);

        var created = requests.recordOnBehalf(TestSessions.issue(1), 3, draft);

        assertEquals(3, scalar("SELECT requester_id FROM maintenance_requests WHERE id=" + created.id()));
        assertEquals("OPEN", text("SELECT status FROM maintenance_requests WHERE id=" + created.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE request_id=" + created.id()
                + " AND actor_id=1 AND action='REQUEST_RECORDED_ON_BEHALF'"));
        String detail = text("SELECT detail FROM audit_events WHERE request_id=" + created.id());
        assertTrue(detail.contains("\"ownerId\":3"), detail);
        assertFalse(detail.contains(draft.title()), detail);
        assertFalse(detail.contains(draft.description()), detail);
        assertFalse(detail.contains(draft.location()), detail);
    }

    @Test
    @DisplayName("MGR-020 LIF-013/023 persists terminal corrections without changing protected fields")
    void persistsTerminalCorrectionAndSafeAudit() throws Exception {
        insertRequest(10, RequestStatus.CLOSED, 2L, "Repair completed safely.", NOW.minusSeconds(60));
        RequestDraft correction = new RequestDraft(
                "Corrected leak title", "Corrected description of the reported leak.",
                "Room 22", "Electrical", ReportedUrgency.NORMAL);

        var corrected = requests.correctRequest(
                TestSessions.issue(1), 10, correction, ManagerPriority.CRITICAL);

        assertEquals(RequestStatus.CLOSED, corrected.status());
        assertEquals("CLOSED", text("SELECT status FROM maintenance_requests WHERE id=10"));
        assertEquals("FF-000010", text("SELECT display_id FROM maintenance_requests WHERE id=10"));
        assertEquals(3, scalar("SELECT requester_id FROM maintenance_requests WHERE id=10"));
        assertEquals(2, scalar("SELECT assignee_id FROM maintenance_requests WHERE id=10"));
        assertEquals("Repair completed safely.",
                text("SELECT resolution_summary FROM maintenance_requests WHERE id=10"));
        String detail = text("SELECT detail FROM audit_events WHERE request_id=10");
        assertTrue(detail.contains("title"), detail);
        assertFalse(detail.contains(correction.title()), detail);
        assertFalse(detail.contains(correction.description()), detail);
    }

    @Test
    @DisplayName("MGR-001 LIF-010 DAT-018 combines and deterministically orders all Manager history types")
    void returnsCompleteManagerHistoryInChronologicalOrder() throws Exception {
        insertRequest(10, RequestStatus.IN_PROGRESS, 2L, null, null);
        execute("INSERT INTO audit_events(request_id,actor_id,action,detail,occurred_at,target_type,target_id) "
                + "VALUES(10,1,'REQUEST_ASSIGNED','{}','2026-09-22T09:00:00Z','REQUEST',10)");
        execute("INSERT INTO requester_updates(request_id,author_id,text,created_at) "
                + "VALUES(10,3,'Please call before entry.','2026-09-22T10:00:00Z')");
        execute("INSERT INTO work_logs(request_id,author_id,note,minutes_spent,created_at) "
                + "VALUES(10,2,'Inspected the equipment.',20,'2026-09-22T11:00:00Z')");

        var history = requests.getRequestHistory(TestSessions.issue(1), 10);

        assertEquals(List.of("Audit", "Requester update", "Work log"),
                history.stream().map(entry -> entry.type()).toList());
        assertTrue(history.get(1).description().contains("Please call before entry."));
        assertTrue(history.get(2).description().contains("20 min"));
    }

    @Test
    @DisplayName("AUT-028 DAT-007/016 account role and audit changes roll back together")
    void rollsBackAccountRoleWhenAuditInsertFails() throws Exception {
        execute("CREATE TRIGGER reject_role_audit BEFORE INSERT ON audit_events "
                + "WHEN NEW.action='ACCOUNT_ROLE_CHANGED' BEGIN SELECT RAISE(ABORT, 'fail'); END");

        assertThrows(StorageException.class,
                () -> accounts.changeRole(TestSessions.issue(1), 3, Role.TECHNICIAN));

        assertEquals("REQUESTER", text("SELECT role FROM user_accounts WHERE id=3"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("AUT-028/031 DAT-019 role-change audit identifies the changed field without sensitive data")
    void roleChangeAuditDescribesChangedField() throws Exception {
        accounts.changeRole(TestSessions.issue(1), 3, Role.TECHNICIAN);

        String detail = text("SELECT detail FROM audit_events WHERE action='ACCOUNT_ROLE_CHANGED'");
        assertTrue(detail.toLowerCase(java.util.Locale.ROOT).contains("role"), detail);
        assertFalse(detail.toLowerCase(java.util.Locale.ROOT).contains("password"), detail);
    }

    private void insertAccount(long id, String username, String role, boolean active) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO user_accounts(
                            id, username, display_name, role, password_hash, active, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'test-hash', ?, ?, ?)
                        """)) {
            statement.setLong(1, id);
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setString(4, role);
            statement.setInt(5, active ? 1 : 0);
            statement.setString(6, NOW.minusSeconds(3600).toString());
            statement.setString(7, NOW.minusSeconds(3600).toString());
            statement.executeUpdate();
        }
    }

    private void insertRequest(
            long id,
            RequestStatus status,
            Long assignee,
            String resolution,
            Instant completedAt) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO maintenance_requests(
                            id, display_id, requester_id, title, description, location, category,
                            reported_urgency, manager_priority, status, assignee_id, assigned_at,
                            resolution_summary, completed_at, created_at, updated_at)
                        VALUES (?, ?, 3, 'Leaking pipe', 'Water is leaking under the sink.',
                            'Room 12', 'Plumbing', 'HIGH', 'HIGH', ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            Instant created = NOW.minusSeconds(7200);
            statement.setLong(1, id);
            statement.setString(2, "FF-%06d".formatted(id));
            statement.setString(3, status.name());
            if (assignee == null) {
                statement.setObject(4, null);
                statement.setObject(5, null);
            } else {
                statement.setLong(4, assignee);
                statement.setString(5, created.plusSeconds(60).toString());
            }
            statement.setString(6, resolution);
            statement.setString(7, completedAt == null ? null : completedAt.toString());
            statement.setString(8, created.toString());
            statement.setString(9, created.plusSeconds(120).toString());
            statement.executeUpdate();
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long scalar(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url);
                var statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private String text(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url);
                var statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
