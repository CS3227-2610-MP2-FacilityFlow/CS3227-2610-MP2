package sg.edu.nus.facilityflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.TestSessions;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.service.ManagerRequestService;
import sg.edu.nus.facilityflow.service.TechnicianRequestService;

class SQLiteTechnicianRequestServiceTest {
    private static final Instant ASSIGNMENT_TIME = Instant.parse("2026-09-24T09:00:00Z");
    private static final Instant START_TIME = Instant.parse("2026-09-24T09:10:00Z");
    private static final Instant LOG_TIME = Instant.parse("2026-09-24T09:20:00Z");
    private static final Instant COMPLETION_TIME = Instant.parse("2026-09-24T09:30:00Z");

    @TempDir
    Path directory;

    private String jdbcUrl;
    private SQLiteManagerAssignmentStore store;
    private AuthenticatedSession managerSession;
    private AuthenticatedSession firstTechnicianSession;
    private AuthenticatedSession secondTechnicianSession;

    @BeforeEach
    void setUp() throws SQLException {
        jdbcUrl = "jdbc:sqlite:" + directory.resolve("technician.db");
        store = new SQLiteManagerAssignmentStore(jdbcUrl);
        store.initializeSchema();
        execute("""
                INSERT INTO user_accounts
                    (id, username, display_name, role, password_hash, active, created_at, updated_at)
                VALUES
                    (1, 'manager', 'Manager', 'FACILITIES_MANAGER', 'test-only', 1,
                        '2026-09-24T08:00:00Z', '2026-09-24T08:00:00Z'),
                    (2, 'tech-a', 'Technician A', 'TECHNICIAN', 'test-only', 1,
                        '2026-09-24T08:00:00Z', '2026-09-24T08:00:00Z'),
                    (3, 'tech-b', 'Technician B', 'TECHNICIAN', 'test-only', 1,
                        '2026-09-24T08:00:00Z', '2026-09-24T08:00:00Z'),
                    (4, 'owner', 'Requester', 'REQUESTER', 'test-only', 1,
                        '2026-09-24T08:00:00Z', '2026-09-24T08:00:00Z')
                """);
        execute("""
                INSERT INTO maintenance_requests(
                    id, display_id, requester_id, title, description, location, category,
                    reported_urgency, manager_priority, status, assignee_id, assigned_at,
                    resolution_summary, completed_at, created_at, updated_at)
                VALUES (10, 'FF-000010', 4, 'Leaking pipe',
                    'Water is leaking below the sink.', 'Block A pantry', 'Plumbing',
                    'HIGH', NULL, 'OPEN', NULL, NULL, NULL, NULL,
                    '2026-09-24T08:30:00Z', '2026-09-24T08:30:00Z')
                """);
        managerSession = TestSessions.issue(1);
        firstTechnicianSession = TestSessions.issue(2);
        secondTechnicianSession = TestSessions.issue(3);
    }

    @Test
    @DisplayName("E2E-006/007 TEC-A01/A03/A05 persists start, work log and completion with audits")
    void persistsCompleteTechnicianWorkflow() throws SQLException {
        MaintenanceRequest assigned = managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.CRITICAL);
        MaintenanceRequest started = technicianAt(START_TIME).startWork(firstTechnicianSession, 10);
        WorkLog workLog = technicianAt(LOG_TIME).addWorkLog(
                firstTechnicianSession, 10, "  Replaced the damaged valve.  ", 35);
        MaintenanceRequest completed = technicianAt(COMPLETION_TIME).completeWork(
                firstTechnicianSession, 10, "  Replaced valve and verified normal flow.  ");

        assertEquals(RequestStatus.ASSIGNED, assigned.status());
        assertEquals(RequestStatus.IN_PROGRESS, started.status());
        assertEquals(RequestStatus.COMPLETED, completed.status());
        assertEquals(2L, completed.assigneeId());
        assertEquals(ASSIGNMENT_TIME, completed.assignedAt());
        assertEquals("Replaced valve and verified normal flow.", completed.resolutionSummary());
        assertEquals(COMPLETION_TIME, completed.completedAt());
        assertEquals(COMPLETION_TIME, completed.updatedAt());
        assertEquals("Replaced the damaged valve.", workLog.note());
        assertEquals(35, workLog.minutesSpent());
        assertEquals(LOG_TIME, workLog.createdAt());
        assertEquals(List.of(workLog), technicianAt(COMPLETION_TIME)
                .listWorkLogs(firstTechnicianSession, 10));

        assertEquals(1, scalar("SELECT COUNT(*) FROM work_logs WHERE request_id = 10"));
        assertEquals(4, scalar("SELECT COUNT(*) FROM audit_events WHERE request_id = 10"));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM maintenance_requests
                WHERE id = 10 AND status = 'COMPLETED' AND assignee_id = 2
                    AND assigned_at = '2026-09-24T09:00:00Z'
                    AND resolution_summary = 'Replaced valve and verified normal flow.'
                    AND completed_at = '2026-09-24T09:30:00Z'
                """));
        assertEquals(
                "REQUEST_ASSIGNED,REQUEST_STARTED,WORK_LOG_ADDED,REQUEST_COMPLETED",
                joinedAuditActions());
    }

    @Test
    @DisplayName("MGR-006/MGR-A03 TEC-011/012/TEC-A06 E2E-012 reassigns and rejects former writer")
    void reassignsWorkAndRejectsFormerTechnicianNextWrite() throws SQLException {
        managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.HIGH);
        technicianAt(START_TIME).startWork(firstTechnicianSession, 10);
        WorkLog historical = technicianAt(LOG_TIME).addWorkLog(
                firstTechnicianSession, 10, "Initial inspection completed.", 20);
        Instant reassignedAt = LOG_TIME.plusSeconds(60);

        MaintenanceRequest reassigned = managerAt(reassignedAt).reassignRequest(
                managerSession, 10, 3, "  Shift handover required  ");

        assertEquals(RequestStatus.ASSIGNED, reassigned.status());
        assertEquals(3L, reassigned.assigneeId());
        assertEquals(reassignedAt, reassigned.assignedAt());
        assertEquals(reassignedAt, reassigned.updatedAt());
        assertEquals(ManagerPriority.HIGH, reassigned.managerPriority());
        assertEquals(List.of(historical), technicianAt(reassignedAt)
                .listWorkLogs(secondTechnicianSession, 10));
        assertThrows(AuthorizationException.class, () -> technicianAt(reassignedAt)
                .addWorkLog(firstTechnicianSession, 10, "Stale screen update", 5));
        assertThrows(AuthorizationException.class, () -> technicianAt(reassignedAt)
                .getAssignedRequest(firstTechnicianSession, 10));
        assertEquals(1, scalar("SELECT COUNT(*) FROM work_logs"));
        assertEquals(4, scalar("SELECT COUNT(*) FROM audit_events"));
        assertEquals(1, scalar("""
                SELECT COUNT(*) FROM audit_events
                WHERE action = 'REQUEST_REASSIGNED'
                    AND detail = '{"oldAssigneeId":2,"newAssigneeId":3,"reason":"Shift handover required"}'
                """));

        Instant secondStart = reassignedAt.plusSeconds(60);
        Instant secondLogTime = reassignedAt.plusSeconds(120);
        technicianAt(secondStart).startWork(secondTechnicianSession, 10);
        WorkLog current = technicianAt(secondLogTime).addWorkLog(
                secondTechnicianSession, 10, "Continued repair after handover.", 15);
        List<WorkLog> history = technicianAt(secondLogTime)
                .listWorkLogs(secondTechnicianSession, 10);
        assertEquals(List.of(historical, current), history);
        assertEquals(2L, history.get(0).authorId());
        assertEquals(3L, history.get(1).authorId());
    }

    @Test
    @DisplayName("LIF-012 DAT-007 E2E-017 rolls back start-work when transition audit fails")
    void rollsBackStartWhenAuditFails() throws SQLException {
        MaintenanceRequest assigned = managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.HIGH);
        rejectAuditInserts();

        assertThrows(StorageException.class,
                () -> technicianAt(START_TIME).startWork(firstTechnicianSession, 10));

        MaintenanceRequest persisted = technicianAt(START_TIME)
                .getAssignedRequest(firstTechnicianSession, 10);
        assertEquals(assigned, persisted);
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("LIF-012 DAT-007 E2E-017 rolls back work-log and updatedAt when its audit fails")
    void rollsBackWorkLogWhenAuditFails() throws SQLException {
        managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.HIGH);
        MaintenanceRequest started = technicianAt(START_TIME)
                .startWork(firstTechnicianSession, 10);
        rejectAuditInserts();

        assertThrows(StorageException.class, () -> technicianAt(LOG_TIME)
                .addWorkLog(firstTechnicianSession, 10, "Inspected the valve.", 10));

        MaintenanceRequest persisted = technicianAt(LOG_TIME)
                .getAssignedRequest(firstTechnicianSession, 10);
        assertEquals(started, persisted);
        assertEquals(0, scalar("SELECT COUNT(*) FROM work_logs"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("LIF-012 DAT-007 E2E-017 rolls back completion metadata when audit fails")
    void rollsBackCompletionWhenAuditFails() throws SQLException {
        managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.HIGH);
        technicianAt(START_TIME).startWork(firstTechnicianSession, 10);
        technicianAt(LOG_TIME).addWorkLog(
                firstTechnicianSession, 10, "Replaced the damaged valve.", 35);
        MaintenanceRequest beforeCompletion = technicianAt(LOG_TIME)
                .getAssignedRequest(firstTechnicianSession, 10);
        rejectAuditInserts();

        assertThrows(StorageException.class, () -> technicianAt(COMPLETION_TIME)
                .completeWork(firstTechnicianSession, 10, "Replaced valve and tested flow."));

        MaintenanceRequest persisted = technicianAt(COMPLETION_TIME)
                .getAssignedRequest(firstTechnicianSession, 10);
        assertEquals(beforeCompletion, persisted);
        assertEquals(RequestStatus.IN_PROGRESS, persisted.status());
        assertNull(persisted.resolutionSummary());
        assertNull(persisted.completedAt());
        assertEquals(1, scalar("SELECT COUNT(*) FROM work_logs"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("MGR-006 LIF-012 DAT-007 rolls back reassignment when its audit fails")
    void rollsBackReassignmentWhenAuditFails() throws SQLException {
        MaintenanceRequest assigned = managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.HIGH);
        rejectAuditInserts();

        assertThrows(StorageException.class, () -> managerAt(START_TIME)
                .reassignRequest(managerSession, 10, 3, "Shift handover required"));

        MaintenanceRequest persisted = technicianAt(START_TIME)
                .getAssignedRequest(firstTechnicianSession, 10);
        assertEquals(assigned, persisted);
        assertEquals(2L, persisted.assigneeId());
        assertEquals(ASSIGNMENT_TIME, persisted.assignedAt());
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("DAT-002/005 work-log foreign keys reject unknown authors and roll back")
    void enforcesWorkLogForeignKeys() throws SQLException {
        managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.HIGH);
        technicianAt(START_TIME).startWork(firstTechnicianSession, 10);

        assertThrows(StorageException.class, () -> store.inTransaction(transaction -> {
            transaction.appendWorkLog(10, 999, "Invalid author", 5, LOG_TIME);
            return null;
        }));
        assertThrows(StorageException.class, () -> store.inTransaction(transaction -> {
            transaction.appendWorkLog(999, 2, "Invalid request", 5, LOG_TIME);
            return null;
        }));

        assertEquals(0, scalar("SELECT COUNT(*) FROM work_logs"));
    }

    @Test
    @DisplayName("DAT-002 LIF-007 SQLite constraints reject minutes outside the allowed range")
    void enforcesWorkLogMinuteConstraints() throws SQLException {
        assertThrows(StorageException.class, () -> store.inTransaction(transaction -> {
            transaction.appendWorkLog(10, 2, "Zero minutes", 0, LOG_TIME);
            return null;
        }));
        assertThrows(StorageException.class, () -> store.inTransaction(transaction -> {
            transaction.appendWorkLog(10, 2, "Too many minutes", 1_441, LOG_TIME);
            return null;
        }));

        assertEquals(0, scalar("SELECT COUNT(*) FROM work_logs"));
    }

    @Test
    @DisplayName("DAT-001/010 LIF-007 reloads work logs and request metadata from a reopened store")
    void reloadsPersistedTechnicianData() throws SQLException {
        managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.CRITICAL);
        technicianAt(START_TIME).startWork(firstTechnicianSession, 10);
        WorkLog expectedLog = technicianAt(LOG_TIME).addWorkLog(
                firstTechnicianSession, 10, "Persisted inspection note.", 25);
        MaintenanceRequest expectedRequest = technicianAt(COMPLETION_TIME).completeWork(
                firstTechnicianSession, 10, "Repair completed and tested successfully.");

        SQLiteManagerAssignmentStore reopenedStore = new SQLiteManagerAssignmentStore(jdbcUrl);
        reopenedStore.initializeSchema();
        TechnicianRequestService reopenedTechnician = new TechnicianRequestService(
                reopenedStore, Clock.fixed(COMPLETION_TIME, ZoneOffset.UTC), TestSessions.MANAGER);

        assertEquals(expectedRequest,
                reopenedTechnician.getAssignedRequest(firstTechnicianSession, 10));
        assertEquals(List.of(expectedLog),
                reopenedTechnician.listWorkLogs(firstTechnicianSession, 10));
    }

    private ManagerRequestService managerAt(Instant time) {
        return new ManagerRequestService(
                store, Clock.fixed(time, ZoneOffset.UTC), TestSessions.MANAGER);
    }

    private TechnicianRequestService technicianAt(Instant time) {
        return new TechnicianRequestService(
                store, Clock.fixed(time, ZoneOffset.UTC), TestSessions.MANAGER);
    }

    private void rejectAuditInserts() throws SQLException {
        execute("""
                CREATE TRIGGER reject_technician_audit
                BEFORE INSERT ON audit_events
                BEGIN
                    SELECT RAISE(ABORT, 'injected audit failure');
                END
                """);
    }

    private String joinedAuditActions() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT action FROM audit_events ORDER BY occurred_at, id")) {
            StringBuilder actions = new StringBuilder();
            while (rows.next()) {
                if (!actions.isEmpty()) {
                    actions.append(',');
                }
                actions.append(rows.getString(1));
            }
            return actions.toString();
        }
    }

    private long scalar(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
