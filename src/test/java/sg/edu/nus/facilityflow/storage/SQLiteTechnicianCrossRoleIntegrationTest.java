package sg.edu.nus.facilityflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sg.edu.nus.facilityflow.auth.AuthFixture;
import sg.edu.nus.facilityflow.auth.AuthenticationService;
import sg.edu.nus.facilityflow.auth.PasswordHasher;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.service.ManagerRequestService;
import sg.edu.nus.facilityflow.service.RequesterRequestService;
import sg.edu.nus.facilityflow.service.RequestValidator;
import sg.edu.nus.facilityflow.service.TechnicianRequestService;

class SQLiteTechnicianCrossRoleIntegrationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T08:00:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-24T08:10:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-24T08:20:00Z");
    private static final Instant LOGGED_AT = Instant.parse("2026-09-24T08:30:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-24T08:40:00Z");
    private static final RequestDraft DRAFT = new RequestDraft(
            "  Leaking pipe  ",
            "  Water is leaking below the sink. Please inspect.  ",
            "  Block A pantry  ",
            " Plumbing ",
            sg.edu.nus.facilityflow.model.ReportedUrgency.HIGH);

    @TempDir
    Path directory;

    private AuthFixture fixture;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new AuthFixture(directory);
        try (var connection = DriverManager.getConnection(fixture.url);
                var statement = connection.prepareStatement("""
                INSERT INTO user_accounts(id, username, display_name, role, password_hash,
                    active, created_at, updated_at)
                VALUES (6, 'tech-b', 'Technician B', 'TECHNICIAN', ?, 1,
                    '2026-09-24T00:00:00Z', '2026-09-24T00:00:00Z')
                """)) {
            statement.setString(1, PasswordHasher.hash("password".toCharArray()));
            statement.executeUpdate();
        }
    }

    @Test
    @DisplayName("E2E-003/005/006/007/014/015/016 REQ-A08 TEC-A01/A03/A05 persists one authenticated cross-role workflow")
    void movesRequesterWorkThroughManagerTechnicianAndRestart() throws SQLException {
        var requesterSession = fixture.auth.login("owner", "password".toCharArray());
        var managerSession = fixture.auth.login("manager", "password".toCharArray());
        var technicianSession = fixture.auth.login("tech", "password".toCharArray());
        var otherTechnicianSession = fixture.auth.login("tech-b", "password".toCharArray());

        var requester = requesterService(fixture.store, CREATED_AT, fixture.sessions);
        var manager = managerService(fixture.store, ASSIGNED_AT, fixture.sessions);
        var technician = technicianService(fixture.store, STARTED_AT, fixture.sessions);

        var created = requester.createRequest(requesterSession, DRAFT);
        var assigned = manager.assignOpenRequest(
                managerSession, created.id(), technicianSession.accountId(), ManagerPriority.CRITICAL);
        assertEquals(assigned, requester.getOwnRequest(requesterSession, created.id()));
        var started = technician.startWork(technicianSession, created.id());
        var workLog = technicianService(fixture.store, LOGGED_AT, fixture.sessions)
                .addWorkLog(technicianSession, created.id(), "  Replaced the damaged valve.  ", 35);
        var completed = technicianService(fixture.store, COMPLETED_AT, fixture.sessions)
                .completeWork(technicianSession, created.id(),
                        "  Replaced valve and verified normal flow.  ");

        assertEquals("FF-000001", created.displayId());
        assertEquals(1L, created.requesterId());
        assertEquals(RequestStatus.OPEN, created.status());
        assertEquals(RequestStatus.ASSIGNED, assigned.status());
        assertEquals(ManagerPriority.CRITICAL, assigned.managerPriority());
        assertEquals(technicianSession.accountId(), assigned.assigneeId());
        assertEquals(ASSIGNED_AT, assigned.assignedAt());
        assertEquals(ASSIGNED_AT, assigned.updatedAt());
        assertEquals(RequestStatus.IN_PROGRESS, started.status());
        assertEquals(STARTED_AT, started.updatedAt());
        assertEquals(RequestStatus.COMPLETED, completed.status());
        assertEquals(technicianSession.accountId(), completed.assigneeId());
        assertEquals("Replaced valve and verified normal flow.", completed.resolutionSummary());
        assertEquals(COMPLETED_AT, completed.completedAt());
        assertEquals(COMPLETED_AT, completed.updatedAt());
        assertEquals("Replaced the damaged valve.", workLog.note());
        assertEquals(35, workLog.minutesSpent());
        assertEquals(LOGGED_AT, workLog.createdAt());
        assertEquals(completed, requester.getOwnRequest(requesterSession, created.id()));
        assertEquals(List.of(completed), manager.listAllRequests(managerSession));
        assertEquals(List.of(completed), technician.listAssignedRequests(technicianSession));
        assertEquals(List.of(workLog), technicianService(fixture.store, COMPLETED_AT, fixture.sessions)
                .listWorkLogs(technicianSession, created.id()));

        assertTrue(technician.listAssignedRequests(otherTechnicianSession).isEmpty());
        assertThrows(AuthorizationException.class,
                () -> technician.getAssignedRequest(otherTechnicianSession, created.id()));
        assertThrows(AuthorizationException.class,
                () -> technician.listWorkLogs(otherTechnicianSession, created.id()));
        assertThrows(AuthorizationException.class,
                () -> technician.startWork(otherTechnicianSession, created.id()));
        assertThrows(AuthorizationException.class,
                () -> technician.listAssignedRequests(requesterSession));
        assertThrows(AuthorizationException.class,
                () -> manager.assignOpenRequest(
                        technicianSession, created.id(), otherTechnicianSession.accountId(),
                        ManagerPriority.HIGH));

        assertEquals(expectedAuditEntries(), requestAuditEntries(created.id()));
        assertEquals(1, fixture.scalar("SELECT COUNT(*) FROM work_logs WHERE request_id = " + created.id()));

        var reopenedStore = new SQLiteManagerAssignmentStore(fixture.url);
        reopenedStore.initializeSchema();
        var restartedSessions = new SessionManager();
        var restartedAuth = new AuthenticationService(
                reopenedStore, restartedSessions, fixedClock(COMPLETED_AT));
        assertThrows(AuthorizationException.class,
                () -> restartedAuth.currentUser(technicianSession));

        var restartedRequesterSession = restartedAuth.login("owner", "password".toCharArray());
        var restartedManagerSession = restartedAuth.login("manager", "password".toCharArray());
        var restartedTechnicianSession = restartedAuth.login("tech", "password".toCharArray());
        var restartedRequester = requesterService(reopenedStore, COMPLETED_AT, restartedSessions);
        var restartedManager = managerService(reopenedStore, COMPLETED_AT, restartedSessions);
        var restartedTechnician = technicianService(reopenedStore, COMPLETED_AT, restartedSessions);

        assertEquals(completed,
                restartedRequester.getOwnRequest(restartedRequesterSession, created.id()));
        assertEquals(List.of(completed), restartedManager.listAllRequests(restartedManagerSession));
        assertEquals(List.of(completed),
                restartedTechnician.listAssignedRequests(restartedTechnicianSession));
        assertEquals(List.of(workLog),
                restartedTechnician.listWorkLogs(restartedTechnicianSession, created.id()));
        assertEquals(expectedAuditEntries(), requestAuditEntries(created.id()));
    }

    private List<AuditEntry> requestAuditEntries(long requestId) throws SQLException {
        try (var connection = DriverManager.getConnection(fixture.url);
                var statement = connection.prepareStatement(
                        "SELECT action, actor_id, occurred_at FROM audit_events "
                                + "WHERE request_id = ? ORDER BY id")) {
            statement.setLong(1, requestId);
            try (var result = statement.executeQuery()) {
                var entries = new java.util.ArrayList<AuditEntry>();
                while (result.next()) {
                    entries.add(new AuditEntry(
                            result.getString("action"), result.getLong("actor_id"),
                            result.getString("occurred_at")));
                }
                return entries;
            }
        }
    }

    private static List<AuditEntry> expectedAuditEntries() {
        return List.of(
                new AuditEntry("REQUEST_CREATED", 1, CREATED_AT.toString()),
                new AuditEntry("REQUEST_ASSIGNED", 3, ASSIGNED_AT.toString()),
                new AuditEntry("REQUEST_STARTED", 4, STARTED_AT.toString()),
                new AuditEntry("WORK_LOG_ADDED", 4, LOGGED_AT.toString()),
                new AuditEntry("REQUEST_COMPLETED", 4, COMPLETED_AT.toString()));
    }

    private static RequesterRequestService requesterService(
            SQLiteManagerAssignmentStore store, Instant time, SessionManager sessions) {
        return new RequesterRequestService(store,
                new RequestValidator(Set.of("Plumbing")), fixedClock(time), sessions);
    }

    private static ManagerRequestService managerService(
            SQLiteManagerAssignmentStore store, Instant time, SessionManager sessions) {
        return new ManagerRequestService(store, fixedClock(time), sessions);
    }

    private static TechnicianRequestService technicianService(
            SQLiteManagerAssignmentStore store, Instant time, SessionManager sessions) {
        return new TechnicianRequestService(store, fixedClock(time), sessions);
    }

    private static Clock fixedClock(Instant time) {
        return Clock.fixed(time, ZoneOffset.UTC);
    }

    private record AuditEntry(String action, long actorId, String occurredAt) { }
}
