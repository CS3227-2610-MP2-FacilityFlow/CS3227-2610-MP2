package sg.edu.nus.facilityflow.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.TestSessions;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.RequesterFilter;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.service.ManagerRequestService;
import sg.edu.nus.facilityflow.service.RequesterRequestService;
import sg.edu.nus.facilityflow.service.RequestValidator;
import sg.edu.nus.facilityflow.service.ValidationException;

class SQLiteRequesterRequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
    // Test fixtures exercise service authorization, not real login.
    private AuthenticatedSession OWNER;
    private AuthenticatedSession OTHER;
    private static final RequestDraft DRAFT = new RequestDraft(
            "  Leaking pipe  ", "  Water leaks.\nPlease inspect.  ", "  Block A  pantry  ",
            " Plumbing ", ReportedUrgency.HIGH);

    @TempDir
    Path directory;
    private String jdbcUrl;
    private SQLiteManagerAssignmentStore store;
    private RequesterRequestService service;

    @BeforeEach
    void setUp() throws SQLException {
        OWNER = TestSessions.issue(1);
        OTHER = TestSessions.issue(2);
        jdbcUrl = "jdbc:sqlite:" + directory.resolve("requester.db");
        store = new SQLiteManagerAssignmentStore(jdbcUrl);
        store.initializeSchema();
        execute("""
                INSERT INTO user_accounts
                    (id, username, display_name, role, password_hash, active, created_at, updated_at)
                VALUES
                    (1, 'owner', 'Owner', 'REQUESTER', 'test-only', 1, '2026-09-22T00:00:00Z', '2026-09-22T00:00:00Z'),
                    (2, 'other', 'Other', 'REQUESTER', 'test-only', 1, '2026-09-22T00:00:00Z', '2026-09-22T00:00:00Z'),
                    (3, 'manager', 'Manager', 'FACILITIES_MANAGER', 'test-only', 1,
                        '2026-09-22T00:00:00Z', '2026-09-22T00:00:00Z'),
                    (4, 'tech', 'Technician', 'TECHNICIAN', 'test-only', 1,
                        '2026-09-22T00:00:00Z', '2026-09-22T00:00:00Z'),
                    (5, 'inactive', 'Inactive', 'REQUESTER', 'test-only', 0,
                        '2026-09-22T00:00:00Z', '2026-09-22T00:00:00Z')
                """);
        service = requesterService(store, NOW);
    }

    @Test
    @DisplayName("REQ-002/003 LIF-001–005 DAT-015/019 creates normalized OPEN request and safe audit")
    void createsRequestAndAuditWithSystemOwnedFields() throws SQLException {
        MaintenanceRequest created = service.createRequest(OWNER, DRAFT);
        assertEquals(1, created.id());
        assertEquals("FF-000001", created.displayId());
        assertEquals(OWNER.accountId(), created.requesterId());
        assertEquals("Leaking pipe", created.title());
        assertEquals("Water leaks.\nPlease inspect.", created.description());
        assertEquals("Block A  pantry", created.location());
        assertEquals("Plumbing", created.category());
        assertEquals(ReportedUrgency.HIGH, created.reportedUrgency());
        assertEquals(RequestStatus.OPEN, created.status());
        assertNull(created.managerPriority());
        assertNull(created.assigneeId());
        assertEquals(NOW, created.createdAt());
        assertEquals(NOW, created.updatedAt());
        assertEquals(created, service.getOwnRequest(OWNER, created.id()));
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement();
                ResultSet audit = statement.executeQuery("SELECT * FROM audit_events")) {
            assertTrue(audit.next());
            assertTrue(audit.getLong("id") > 0);
            assertEquals(created.id(), audit.getLong("request_id"));
            assertEquals(created.id(), audit.getLong("target_id"));
            assertEquals("REQUEST", audit.getString("target_type"));
            assertEquals(OWNER.accountId(), audit.getLong("actor_id"));
            assertEquals("REQUEST_CREATED", audit.getString("action"));
            assertEquals(NOW.toString(), audit.getString("occurred_at"));
            assertEquals("{\"status\":\"OPEN\"}", audit.getString("detail"));
            assertFalse(audit.next());
        }
        assertEquals("FF-000002", service.createRequest(OWNER, DRAFT).displayId());
    }

    static Stream<RequestDraft> invalidDrafts() {
        return Stream.of(null,
                new RequestDraft("bad", DRAFT.description(), DRAFT.location(), "Plumbing", ReportedUrgency.LOW),
                new RequestDraft("x".repeat(101), DRAFT.description(), DRAFT.location(), "Plumbing", ReportedUrgency.LOW),
                new RequestDraft(DRAFT.title(), "short", DRAFT.location(), "Plumbing", ReportedUrgency.LOW),
                new RequestDraft(DRAFT.title(), "x".repeat(2001), DRAFT.location(), "Plumbing", ReportedUrgency.LOW),
                new RequestDraft(DRAFT.title(), DRAFT.description(), " ", "Plumbing", ReportedUrgency.LOW),
                new RequestDraft(DRAFT.title(), DRAFT.description(), "x".repeat(121), "Plumbing", ReportedUrgency.LOW),
                new RequestDraft(DRAFT.title(), DRAFT.description(), DRAFT.location(), "Unknown", ReportedUrgency.LOW),
                new RequestDraft(DRAFT.title(), DRAFT.description(), DRAFT.location(), "Plumbing", null));
    }

    @ParameterizedTest
    @MethodSource("invalidDrafts")
    @DisplayName("REQ-002/012 LIF-004/005 rejects invalid input without allocating or writing")
    void rejectsInvalidDrafts(RequestDraft draft) throws SQLException {
        assertThrows(ValidationException.class, () -> service.createRequest(OWNER, draft));
        assertEmptyBusinessData();
        assertEquals(1, scalar("SELECT next_value FROM request_identity_sequence"));
    }

    static Stream<AuthenticatedSession> unauthorizedSessions() {
        return Stream.of(null, TestSessions.issue(3), TestSessions.issue(4),
                TestSessions.issue(5), TestSessions.issue(999));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedSessions")
    @DisplayName("AUT-018–022 rejects absent, wrong-role, inactive and unknown sessions for every operation")
    void rejectsUnauthorizedCalls(AuthenticatedSession session) throws SQLException {
        assertThrows(AuthorizationException.class, () -> service.createRequest(session, DRAFT));
        assertThrows(AuthorizationException.class, () -> service.listOwnRequests(session));
        assertThrows(AuthorizationException.class, () -> service.getOwnRequest(session, 1));
        assertEmptyBusinessData();
    }

    @Test
    @DisplayName("AUT-022/032 rechecks persisted active flag and role on the next operation")
    void rechecksExistingSession() throws SQLException {
        MaintenanceRequest created = service.createRequest(OWNER, DRAFT);
        execute("UPDATE user_accounts SET active = 0 WHERE id = 1");
        assertThrows(AuthorizationException.class, () -> service.getOwnRequest(OWNER, created.id()));
        assertThrows(AuthorizationException.class, () -> service.listOwnRequests(OWNER));
        assertThrows(AuthorizationException.class, () -> service.createRequest(OWNER, DRAFT));
        execute("UPDATE user_accounts SET active = 1, role = 'TECHNICIAN' WHERE id = 1");
        assertThrows(AuthorizationException.class, () -> service.getOwnRequest(OWNER, created.id()));
        assertThrows(AuthorizationException.class, () -> service.listOwnRequests(OWNER));
        assertThrows(AuthorizationException.class, () -> service.createRequest(OWNER, DRAFT));
        assertEquals(1, scalar("SELECT COUNT(*) FROM maintenance_requests"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("REQ-A03/A07 AUT-019/021 scopes storage and services to the owner without disclosing existence")
    void deniesCrossOwnerAccess() {
        var own = service.createRequest(OWNER, DRAFT);
        var other = service.createRequest(OTHER, DRAFT);
        assertEquals(List.of(own), service.listOwnRequests(OWNER));
        assertEquals(List.of(other), service.listOwnRequests(OTHER));
        var forbidden = assertThrows(AuthorizationException.class,
                () -> service.getOwnRequest(OWNER, other.id()));
        var missing = assertThrows(AuthorizationException.class,
                () -> service.getOwnRequest(OWNER, 999));
        assertEquals(missing.getMessage(), forbidden.getMessage());
        assertEquals(other, service.getOwnRequest(OTHER, other.id()));
        store.inTransaction(transaction -> {
            assertTrue(transaction.findOwnRequest(OWNER.accountId(), other.id()).isEmpty());
            assertEquals(List.of(own), transaction.listOwnRequests(OWNER.accountId()));
            return null;
        });
    }

    @Test
    @DisplayName("REQ-015 orders by actual creation Instant descending, then display ID ascending")
    void ordersOwnRequestsDeterministically() {
        assertTrue(service.listOwnRequests(OWNER).isEmpty());
        var first = service.createRequest(OWNER, DRAFT);
        var second = service.createRequest(OWNER, DRAFT);
        var newer = requesterService(store, NOW.plusNanos(100_000_000)).createRequest(OWNER, DRAFT);
        service.createRequest(OTHER, DRAFT);
        assertEquals(List.of(newer, first, second), service.listOwnRequests(OWNER));
    }

    @Test
    @DisplayName("REQ-004 LIF-018 searches display ID, title and location without case or surrounding whitespace")
    void searchesOwnRequestsAcrossSupportedFields() {
        var request = service.createRequest(OWNER, DRAFT);
        for (String query : List.of(" ff-000001 ", "LEAKING", "PANTRY")) {
            assertEquals(List.of(request), service.listOwnRequests(OWNER,
                    new RequesterFilter(query, null, null, null, null)));
        }
        assertTrue(service.listOwnRequests(OWNER,
                new RequesterFilter("no match", null, null, null, null)).isEmpty());
    }

    @Test
    @DisplayName("REQ-005/016 LIF-017/019 filters status, category, and inclusive local dates")
    void filtersByEnumsCategoryAndInclusiveLocalDates() {
        var zone = ZoneId.systemDefault();
        var localToday = LocalDate.ofInstant(NOW, zone);
        var dayStart = localToday.atStartOfDay(zone).toInstant();
        var previous = requesterService(store, dayStart.minusNanos(1)).createRequest(OWNER, DRAFT);
        var first = requesterService(store, dayStart).createRequest(OWNER, DRAFT);
        var last = requesterService(store, localToday.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1))
                .createRequest(OWNER, DRAFT);
        var next = requesterService(store, localToday.plusDays(1).atStartOfDay(zone).toInstant())
                .createRequest(OWNER, DRAFT);
        var manager = new ManagerRequestService(store, Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER);
        var assignedLast = manager.assignOpenRequest(TestSessions.issue(3), last.id(), 4, ManagerPriority.HIGH);

        var dateFilter = new RequesterFilter(null, null, null, localToday, localToday);
        assertEquals(List.of(assignedLast, first), service.listOwnRequests(OWNER, dateFilter));
        assertEquals(List.of(assignedLast), service.listOwnRequests(OWNER,
                new RequesterFilter(null, RequestStatus.ASSIGNED, "Plumbing", null, null)));
        assertTrue(service.listOwnRequests(OWNER,
                new RequesterFilter(null, null, "Electrical", null, null)).isEmpty());
        assertTrue(service.listOwnRequests(OWNER,
                new RequesterFilter(null, null, null, localToday.plusDays(5), localToday.plusDays(6))).isEmpty());
        assertTrue(service.listOwnRequests(OWNER).containsAll(List.of(previous, first, assignedLast, next)));
    }

    @Test
    @DisplayName("REQ-007/013 REQ-A04 permits audited owner edits only in OPEN")
    void editsOnlyOwnedOpenRequests() throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        var edited = service.editOpenRequest(OWNER, original.id(),
                new RequestDraft("  New title  ", "  Updated description text  ", "  Room 2  ", "Plumbing", ReportedUrgency.LOW));
        assertEquals("New title", edited.title());
        assertEquals("Updated description text", edited.description());
        assertEquals(RequestStatus.OPEN, edited.status());
        assertEquals(NOW, edited.updatedAt());
        assertEquals(2, scalar("SELECT COUNT(*) FROM audit_events"));
        assertEquals("REQUEST_EDITED", stringScalar("SELECT action FROM audit_events ORDER BY id DESC LIMIT 1"));

        var assigned = new ManagerRequestService(store, Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER)
                .assignOpenRequest(TestSessions.issue(3), original.id(), 4, ManagerPriority.HIGH);
        assertThrows(ValidationException.class, () -> service.editOpenRequest(OWNER, original.id(), DRAFT));
        assertThrows(ValidationException.class,
                () -> service.cancelOpenRequest(OWNER, original.id(), "valid cancellation reason"));
        assertEquals(assigned, service.getOwnRequest(OWNER, original.id()));
        assertThrows(AuthorizationException.class, () -> service.editOpenRequest(OTHER, original.id(), DRAFT));
        assertEquals(3, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("REQ-008 REQ-A05 LIF-015 cancellation needs a valid reason and stores terminal state atomically")
    void cancelsOpenRequestWithReasonAndHistory() throws SQLException {
        var created = service.createRequest(OWNER, DRAFT);
        for (String reason : java.util.Arrays.asList(null, "  ", "four", "x".repeat(501))) {
            assertThrows(ValidationException.class, () -> service.cancelOpenRequest(OWNER, created.id(), reason));
        }
        assertEquals(created, service.getOwnRequest(OWNER, created.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));

        var cancelled = service.cancelOpenRequest(OWNER, created.id(), "  No longer needed  ");
        assertEquals(RequestStatus.CANCELLED, cancelled.status());
        assertEquals("No longer needed", stringScalar("SELECT detail FROM audit_events WHERE action='REQUEST_CANCELLED'"));
        assertTrue(service.getVisibleHistory(OWNER, created.id()).stream()
                .anyMatch(entry -> entry.text().contains("No longer needed")));
        assertThrows(ValidationException.class, () -> service.cancelOpenRequest(OWNER, created.id(), "again valid"));
        assertThrows(ValidationException.class, () -> service.addFollowUp(OWNER, created.id(), "Too late update"));
        assertThrows(AuthorizationException.class, () -> service.cancelOpenRequest(OTHER, created.id(), "valid reason"));
        assertThrows(AuthorizationException.class, () -> service.addFollowUp(OTHER, created.id(), "Too late update"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM audit_events"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM requester_updates"));
    }

    @Test
    @DisplayName("REQ-009 LIF-006/009/015 accepts follow-ups through COMPLETED and rejects terminal requests")
    void addsOwnerFollowUpsOnlyBeforeTerminalState() throws SQLException {
        var open = service.createRequest(OWNER, DRAFT);
        for (String text : java.util.Arrays.asList(null, "", "  ", "x".repeat(1001))) {
            assertThrows(ValidationException.class, () -> service.addFollowUp(OWNER, open.id(), text));
        }
        service.addFollowUp(OWNER, open.id(), "  Please call before entry.  ");
        var updates = store.inTransaction(tx -> tx.listRequesterUpdates(open.id()));
        assertEquals(1, updates.size());
        assertEquals("Please call before entry.", updates.get(0).text());
        assertEquals(OWNER.accountId(), updates.get(0).authorId());
        assertTrue(service.getVisibleHistory(OWNER, open.id()).stream()
                .anyMatch(entry -> entry.kind().equals("Follow-up") && entry.text().equals("Please call before entry.")));
        assertThrows(AuthorizationException.class, () -> service.addFollowUp(OTHER, open.id(), "update"));

        var manager = new ManagerRequestService(store, Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER);
        var assigned = manager.assignOpenRequest(TestSessions.issue(3), open.id(), 4, ManagerPriority.HIGH);
        var tech = new sg.edu.nus.facilityflow.service.TechnicianRequestService(store,
                Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER);
        var inProgress = tech.startWork(TestSessions.issue(4), assigned.id());
        tech.addWorkLog(TestSessions.issue(4), inProgress.id(), "Inspected and repaired pipe", 15);
        var completed = tech.completeWork(TestSessions.issue(4), inProgress.id(), "Replaced the leaking pipe.");
        assertEquals(RequestStatus.COMPLETED, completed.status());
        service.addFollowUp(OWNER, open.id(), "Thank you for the update.");
        execute("UPDATE maintenance_requests SET status='CLOSED' WHERE id=" + open.id());
        assertThrows(ValidationException.class, () -> service.addFollowUp(OWNER, open.id(), "Closed update"));
        assertEquals(2, store.inTransaction(tx -> tx.listRequesterUpdates(open.id())).size());
    }

    @Test
    @DisplayName("REQ-006/010/017 AUT-019 history includes visible events and follow-ups without internal notes or work logs")
    void visibleHistoryExcludesInternalRecords() {
        var created = service.createRequest(OWNER, DRAFT);
        store.inTransaction(tx -> {
            tx.appendAuditEvent(new AuditEvent(created.id(), 3, "REQUEST_ASSIGNED", "{\"status\":\"ASSIGNED\"}", NOW.plusSeconds(1)));
            tx.appendAuditEvent(new AuditEvent(created.id(), 3, "PRIVATE_MANAGER_NOTE", "private manager text", NOW.plusSeconds(2)));
            return null;
        });
        service.addFollowUp(OWNER, created.id(), "Owner follow-up");
        var history = service.getVisibleHistory(OWNER, created.id());
        assertTrue(history.stream().anyMatch(entry -> entry.text().contains("ASSIGNED")));
        assertTrue(history.stream().anyMatch(entry -> entry.text().equals("Owner follow-up")));
        assertFalse(history.stream().anyMatch(entry -> entry.text().contains("private manager text")));
        assertFalse(history.stream().anyMatch(entry -> entry.kind().toLowerCase().contains("work log")));
        assertEquals(history.stream().sorted(java.util.Comparator.comparing(sg.edu.nus.facilityflow.model.RequesterHistoryEntry::occurredAt)).toList(), history);
        assertThrows(AuthorizationException.class, () -> service.getVisibleHistory(OTHER, created.id()));
    }

    @Test
    @DisplayName("REQ-A07 DAT-001/010 request identity and data survive reopening the database")
    void survivesRestart() {
        var created = service.createRequest(OWNER, DRAFT);
        var reopened = new SQLiteManagerAssignmentStore(jdbcUrl);
        reopened.initializeSchema();
        var afterRestart = requesterService(reopened, NOW);
        assertEquals(created, afterRestart.getOwnRequest(TestSessions.issue(1), created.id()));
        assertEquals(List.of(created), afterRestart.listOwnRequests(TestSessions.issue(1)));
        assertEquals("FF-000002", afterRestart.createRequest(TestSessions.issue(1), DRAFT).displayId());
    }

    @Test
    @DisplayName("REQ-A08 MGR-005 LIF-012 same-database handoff preserves owner, identity and audit actors")
    void handsOffToManager() throws SQLException {
        var created = service.createRequest(OWNER, DRAFT);
        var manager = new ManagerRequestService(store, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC), TestSessions.MANAGER);
        var managerSession = TestSessions.issue(3);
        assertEquals(List.of(created), manager.listAllRequests(managerSession));
        var assigned = manager.assignOpenRequest(managerSession, created.id(), 4, ManagerPriority.HIGH);
        assertEquals(assigned, service.getOwnRequest(OWNER, created.id()));
        assertEquals(RequestStatus.ASSIGNED, assigned.status());
        assertEquals(ManagerPriority.HIGH, assigned.managerPriority());
        assertEquals(created.displayId(), assigned.displayId());
        assertEquals(created.requesterId(), assigned.requesterId());
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement();
                ResultSet audit = statement.executeQuery("SELECT action, actor_id FROM audit_events ORDER BY id")) {
            assertTrue(audit.next());
            assertEquals("REQUEST_CREATED", audit.getString("action"));
            assertEquals(1, audit.getLong("actor_id"));
            assertTrue(audit.next());
            assertEquals("REQUEST_ASSIGNED", audit.getString("action"));
            assertEquals(3, audit.getLong("actor_id"));
            assertFalse(audit.next());
        }
    }

    @Test
    @DisplayName("REQ-014/A04/A05 LIF-013 Manager-recorded OPEN request retains owner edit and cancellation rights")
    void managerRecordedRequestHasOwnerEditAndCancelRights() throws SQLException {
        // Fixture for a Manager-recorded request; the Manager creation UI/service is separate work.
        var recorded = store.inTransaction(transaction -> {
            var request = transaction.createOpenRequest(OWNER.accountId(), DRAFT, NOW);
            transaction.appendAuditEvent(new AuditEvent(request.id(), 3, "REQUEST_CREATED",
                    "{\"status\":\"OPEN\"}", NOW));
            transaction.appendAuditEvent(new AuditEvent(request.id(), 3, "PRIVATE_MANAGER_NOTE",
                    "Internal-only information", NOW));
            return request;
        });
        assertEquals(recorded, service.getOwnRequest(OWNER, recorded.id()));
        assertEquals(List.of(recorded), service.listOwnRequests(OWNER));
        assertTrue(service.listOwnRequests(OTHER).isEmpty());

        var edited = service.editOpenRequest(OWNER, recorded.id(), new RequestDraft(
                "Corrected manager report", "The manager recorded the same issue for the requester.",
                "Block B", "Plumbing", ReportedUrgency.HIGH));
        assertEquals("Corrected manager report", edited.title());
        assertEquals(RequestStatus.OPEN, edited.status());
        assertEquals(edited, service.getOwnRequest(OWNER, recorded.id()));

        var cancelled = service.cancelOpenRequest(OWNER, recorded.id(), "Duplicate request from manager");
        assertEquals(RequestStatus.CANCELLED, cancelled.status());
        assertEquals("REQUEST_EDITED", stringScalar("SELECT action FROM audit_events WHERE request_id="
                + recorded.id() + " AND action='REQUEST_EDITED'"));
        assertEquals(OWNER.accountId(), scalar("SELECT actor_id FROM audit_events WHERE request_id="
                + recorded.id() + " AND action='REQUEST_EDITED'"));
        assertEquals(OWNER.accountId(), scalar("SELECT actor_id FROM audit_events WHERE request_id="
                + recorded.id() + " AND action='REQUEST_CANCELLED'"));
        assertTrue(service.getVisibleHistory(OWNER, recorded.id()).stream()
                .anyMatch(entry -> entry.text().contains("Duplicate request from manager")));
    }

    @Test
    @DisplayName("REQ-A09 DAT-007 audit failure rolls back request, audit and sequence; retry creates one")
    void rollsBackAuditFailureAndAllowsRetry() throws SQLException {
        execute("""
                CREATE TRIGGER reject_creation_audit BEFORE INSERT ON audit_events
                BEGIN SELECT RAISE(ABORT, 'injected private database error'); END
                """);
        var failure = assertThrows(StorageException.class, () -> service.createRequest(OWNER, DRAFT));
        assertFalse(failure.getMessage().contains("injected"));
        assertEmptyBusinessData();
        assertEquals(1, scalar("SELECT next_value FROM request_identity_sequence"));
        execute("DROP TRIGGER reject_creation_audit");
        assertEquals("FF-000001", service.createRequest(OWNER, DRAFT).displayId());
        assertEquals(1, scalar("SELECT COUNT(*) FROM maintenance_requests"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("DAT-007 request insertion failure rolls back allocated identity")
    void rollsBackRequestInsertFailure() throws SQLException {
        execute("""
                CREATE TRIGGER reject_creation BEFORE INSERT ON maintenance_requests
                BEGIN SELECT RAISE(ABORT, 'injected insertion failure'); END
                """);
        assertThrows(StorageException.class, () -> service.createRequest(OWNER, DRAFT));
        assertEmptyBusinessData();
        assertEquals(1, scalar("SELECT next_value FROM request_identity_sequence"));
    }

    @Test
    @DisplayName("LIF-002 E2E-033 FF-999999 is the last ID; exhaustion does not commit partial writes")
    void rejectsExhaustedIdentityRange() throws SQLException {
        execute("UPDATE request_identity_sequence SET next_value = 999999 WHERE singleton = 1");
        assertEquals("FF-999999", service.createRequest(OWNER, DRAFT).displayId());
        assertThrows(StorageException.class, () -> service.createRequest(OWNER, DRAFT));
        assertEquals(1, scalar("SELECT COUNT(*) FROM maintenance_requests"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
        assertEquals(1000000, scalar("SELECT next_value FROM request_identity_sequence"));
    }

    @Test
    @DisplayName("DAT-002/007 foreign keys reject invalid owners and roll back identity allocation")
    void enforcesForeignKeys() throws SQLException {
        assertThrows(StorageException.class, () -> store.inTransaction(transaction ->
                transaction.createOpenRequest(999, DRAFT, NOW)));
        assertEmptyBusinessData();
        assertEquals(1, scalar("SELECT next_value FROM request_identity_sequence"));
    }

    @Test
    @DisplayName("DAT-009 read failures return a safe storage error without exposing raw SQL")
    void handlesReadFailure() throws SQLException {
        execute("DROP TABLE maintenance_requests");
        var failure = assertThrows(StorageException.class, () -> service.listOwnRequests(OWNER));
        assertEquals("The local database operation failed", failure.getMessage());
        assertThrows(StorageException.class, () -> service.getOwnRequest(OWNER, 1));
    }

    private static RequesterRequestService requesterService(SQLiteManagerAssignmentStore source, Instant time) {
        return new RequesterRequestService(source, new RequestValidator(Set.of("Plumbing")),
                Clock.fixed(time, ZoneOffset.UTC), TestSessions.MANAGER);
    }

    private void assertEmptyBusinessData() throws SQLException {
        assertEquals(0, scalar("SELECT COUNT(*) FROM maintenance_requests"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    private long scalar(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private String stringScalar(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
