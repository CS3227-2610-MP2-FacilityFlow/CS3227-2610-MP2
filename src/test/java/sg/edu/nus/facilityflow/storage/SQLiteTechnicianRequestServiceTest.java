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
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.TechnicianDashboardCounts;
import sg.edu.nus.facilityflow.model.TechnicianQueueFilter;
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
    @DisplayName("TEC-004/TEC-A01 LIF-011/012 persists only the start transition and its audit atomically")
    void persistsStartWorkTransitionAndAudit() throws SQLException {
        MaintenanceRequest assigned = managerAt(ASSIGNMENT_TIME).assignOpenRequest(
                managerSession, 10, 2, ManagerPriority.HIGH);

        MaintenanceRequest started = technicianAt(START_TIME).startWork(firstTechnicianSession, 10);

        assertEquals(RequestStatus.ASSIGNED, assigned.status());
        assertEquals(RequestStatus.IN_PROGRESS, started.status());
        assertEquals(2L, started.assigneeId());
        assertEquals(ASSIGNMENT_TIME, started.assignedAt());
        assertEquals(START_TIME, started.updatedAt());
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events "
                + "WHERE request_id = 10 AND actor_id = 2 AND action = 'REQUEST_STARTED' "
                + "AND detail = '{\"oldStatus\":\"ASSIGNED\",\"newStatus\":\"IN_PROGRESS\"}' "
                + "AND occurred_at = '2026-09-24T09:10:00Z'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM work_logs WHERE request_id = 10"));
    }

    @Test
    @DisplayName("TEC-001 SQLite dashboard counts include only the current Technician's queue")
    void readsCurrentTechnicianDashboardCounts() throws SQLException {
        insertRequest(11, "FF-000011", "Assigned request", "Block A", "Plumbing",
                ReportedUrgency.LOW, ManagerPriority.LOW, RequestStatus.ASSIGNED, 2,
                ASSIGNMENT_TIME, null, null);
        insertRequest(12, "FF-000012", "Active request", "Block B", "HVAC",
                ReportedUrgency.NORMAL, ManagerPriority.MEDIUM, RequestStatus.IN_PROGRESS, 2,
                ASSIGNMENT_TIME, null, null);
        insertRequest(13, "FF-000013", "Completed request", "Block C", "Electrical",
                ReportedUrgency.HIGH, ManagerPriority.HIGH, RequestStatus.COMPLETED, 2,
                ASSIGNMENT_TIME, "Repair completed successfully.", COMPLETION_TIME);
        insertRequest(14, "FF-000014", "Other technician request", "Block D", "Safety",
                ReportedUrgency.EMERGENCY, ManagerPriority.CRITICAL, RequestStatus.ASSIGNED, 3,
                ASSIGNMENT_TIME, null, null);
        insertRequest(15, "FF-000015", "Closed request", "Block E", "Cleaning",
                ReportedUrgency.EMERGENCY, ManagerPriority.CRITICAL, RequestStatus.CLOSED, 2,
                ASSIGNMENT_TIME, "Repair completed and closed.", COMPLETION_TIME);

        assertEquals(new TechnicianDashboardCounts(1, 1, 1),
                technicianAt(COMPLETION_TIME).getDashboardCounts(firstTechnicianSession));
        assertEquals(new TechnicianDashboardCounts(1, 0, 0),
                technicianAt(COMPLETION_TIME).getDashboardCounts(secondTechnicianSession));
    }

    @Test
    @DisplayName("TEC-002/003 LIF-017–020 SQLite queue filters and orders persisted assignments")
    void readsAndOrdersPersistedTechnicianQueue() throws SQLException {
        insertRequest(20, "FF-000020", "Boiler repair", "East Wing", "HVAC",
                ReportedUrgency.HIGH, ManagerPriority.CRITICAL, RequestStatus.ASSIGNED, 2,
                ASSIGNMENT_TIME, null, null);
        insertRequest(21, "FF-000021", "Boiler inspection", "West Wing", "HVAC",
                ReportedUrgency.EMERGENCY, ManagerPriority.CRITICAL, RequestStatus.IN_PROGRESS, 2,
                ASSIGNMENT_TIME.plusSeconds(300), null, null);
        insertRequest(22, "FF-000022", "Boiler room follow-up", "North Wing", "HVAC",
                ReportedUrgency.EMERGENCY, ManagerPriority.CRITICAL, RequestStatus.COMPLETED, 2,
                null, "Boiler repair completed successfully.", COMPLETION_TIME);
        insertRequest(25, "FF-000025", "Boiler replacement", "South Wing", "HVAC",
                ReportedUrgency.HIGH, ManagerPriority.CRITICAL, RequestStatus.ASSIGNED, 2,
                ASSIGNMENT_TIME, null, null);
        insertRequest(26, "FF-000026", "Boiler repair", "East Wing", "HVAC",
                ReportedUrgency.EMERGENCY, ManagerPriority.CRITICAL, RequestStatus.ASSIGNED, 3,
                ASSIGNMENT_TIME, null, null);
        insertRequest(27, "FF-000027", "Boiler archive", "East Wing", "HVAC",
                ReportedUrgency.EMERGENCY, ManagerPriority.CRITICAL, RequestStatus.CLOSED, 2,
                ASSIGNMENT_TIME, "Boiler work closed successfully.", COMPLETION_TIME);

        List<Long> ids = technicianAt(COMPLETION_TIME)
                .listAssignedRequests(firstTechnicianSession,
                        new TechnicianQueueFilter("  bOiLeR  ", null, " HVAC ", ManagerPriority.CRITICAL))
                .stream()
                .map(MaintenanceRequest::id)
                .toList();

        assertEquals(List.of(21L, 22L, 20L, 25L), ids);
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

    private void insertRequest(
            long id,
            String displayId,
            String title,
            String location,
            String category,
            ReportedUrgency urgency,
            ManagerPriority priority,
            RequestStatus status,
            long assigneeId,
            Instant assignedAt,
            String resolutionSummary,
            Instant completedAt) throws SQLException {
        String sql = """
                INSERT INTO maintenance_requests(
                    id, display_id, requester_id, title, description, location, category,
                    reported_urgency, manager_priority, status, assignee_id, assigned_at,
                    resolution_summary, completed_at, created_at, updated_at)
                VALUES (?, ?, 4, ?, 'A persisted request fixture with sufficient detail.', ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, displayId);
            statement.setString(3, title);
            statement.setString(4, location);
            statement.setString(5, category);
            statement.setString(6, urgency.name());
            if (priority == null) {
                statement.setNull(7, java.sql.Types.VARCHAR);
            } else {
                statement.setString(7, priority.name());
            }
            statement.setString(8, status.name());
            statement.setLong(9, assigneeId);
            if (assignedAt == null) {
                statement.setNull(10, java.sql.Types.VARCHAR);
            } else {
                statement.setString(10, assignedAt.toString());
            }
            if (resolutionSummary == null) {
                statement.setNull(11, java.sql.Types.VARCHAR);
            } else {
                statement.setString(11, resolutionSummary);
            }
            if (completedAt == null) {
                statement.setNull(12, java.sql.Types.VARCHAR);
            } else {
                statement.setString(12, completedAt.toString());
            }
            statement.setString(13, ASSIGNMENT_TIME.minusSeconds(1_800).toString());
            statement.setString(14, COMPLETION_TIME.toString());
            statement.executeUpdate();
        }
    }
}
