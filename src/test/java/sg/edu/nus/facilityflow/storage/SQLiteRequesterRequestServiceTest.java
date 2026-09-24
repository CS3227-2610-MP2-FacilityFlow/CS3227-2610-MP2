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
    @DisplayName("REQ-014/010 owner reads include Manager-recorded requests but never return raw private audits")
    void readsManagerRecordedRequestWithoutPrivateNotes() {
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


    @Test
    @DisplayName("REQ-007/011 LIF-004/013 editing preserves identity and system fields and audits once")
    void editsAllPermittedFieldsAtomically() throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        var later = new RequesterRequestService(store, new RequestValidator(Set.of("Plumbing", "Electrical")),
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC), TestSessions.MANAGER);
        var draft = new RequestDraft("  Broken faucet  ", "  Faucet no longer works.  ",
                "  Room 25  ", " Electrical ", ReportedUrgency.LOW);
        var edited = later.editRequest(OWNER, original.id(), draft);
        assertEquals("Broken faucet", edited.title());
        assertEquals("Faucet no longer works.", edited.description());
        assertEquals("Room 25", edited.location());
        assertEquals("Electrical", edited.category());
        assertEquals(ReportedUrgency.LOW, edited.reportedUrgency());
        assertEquals(original.id(), edited.id());
        assertEquals(original.displayId(), edited.displayId());
        assertEquals(original.requesterId(), edited.requesterId());
        assertEquals(original.createdAt(), edited.createdAt());
        assertEquals(NOW.plusSeconds(60), edited.updatedAt());
        assertEquals(RequestStatus.OPEN, edited.status());
        assertNull(edited.assigneeId());
        assertNull(edited.managerPriority());
        assertEquals(edited, later.getOwnRequest(OWNER, original.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'REQUEST_EDITED' AND actor_id = 1"));
    }

    @ParameterizedTest
    @MethodSource("invalidDrafts")
    @DisplayName("REQ-007/012 invalid edit leaves request and audit unchanged")
    void rejectsInvalidEdits(RequestDraft draft) throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        assertThrows(ValidationException.class, () -> service.editRequest(OWNER, original.id(), draft));
        assertEquals(original, service.getOwnRequest(OWNER, original.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RequestStatus.class, names = "OPEN",
            mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
    @DisplayName("REQ-007/008 LIF-015/016 every non-OPEN state rejects both mutations")
    void rejectsEveryNonOpenState(RequestStatus status) throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        execute("UPDATE maintenance_requests SET status = '" + status.name() + "' WHERE id = " + original.id());
        var before = service.getOwnRequest(OWNER, original.id());
        assertThrows(ValidationException.class, () -> service.editRequest(OWNER, original.id(), DRAFT));
        assertThrows(ValidationException.class, () -> service.cancelRequest(OWNER, original.id(), "No longer needed"));
        assertEquals(before, service.getOwnRequest(OWNER, original.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedSessions")
    @DisplayName("AUT-018/022 REQ-007/008 rejects unauthorized mutation and reason reads")
    void rejectsUnauthorizedMutations(AuthenticatedSession session) throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        assertThrows(AuthorizationException.class, () -> service.editRequest(session, original.id(), DRAFT));
        assertThrows(AuthorizationException.class, () -> service.cancelRequest(session, original.id(), "Not needed"));
        assertThrows(AuthorizationException.class, () -> service.getOwnCancellationReason(session, original.id()));
        assertEquals(original, service.getOwnRequest(OWNER, original.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @Test
    @DisplayName("REQ-A03 AUT-021 mutation and reason lookup never disclose other owners' requests")
    void rejectsOtherOwnerAndMissingMutationsIdentically() throws SQLException {
        var original = service.createRequest(OTHER, DRAFT);
        var editOther = assertThrows(AuthorizationException.class, () -> service.editRequest(OWNER, original.id(), DRAFT));
        var editMissing = assertThrows(AuthorizationException.class, () -> service.editRequest(OWNER, 999, DRAFT));
        assertEquals(editMissing.getMessage(), editOther.getMessage());
        var cancelOther = assertThrows(AuthorizationException.class,
                () -> service.cancelRequest(OWNER, original.id(), "Not needed"));
        var cancelMissing = assertThrows(AuthorizationException.class, () -> service.cancelRequest(OWNER, 999, "Not needed"));
        assertEquals(cancelMissing.getMessage(), cancelOther.getMessage());
        assertEquals(original, service.getOwnRequest(OTHER, original.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
        service.cancelRequest(OTHER, original.id(), "Other owner's reason");
        assertThrows(AuthorizationException.class, () -> service.getOwnCancellationReason(OWNER, original.id()));
        store.inTransaction(tx -> {
            assertTrue(tx.findOwnCancellationReason(OWNER.accountId(), original.id()).isEmpty());
            return null;
        });
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"    ", "abcd", "  abcd  "})
    @DisplayName("REQ-008 LIF-004 rejects blank and below-minimum reasons")
    void rejectsShortReason(String reason) throws SQLException {
        assertRejectedReason(reason);
    }

    @Test
    @DisplayName("REQ-008 LIF-005 rejects 501 Unicode code points")
    void rejectsTooLongReason() throws SQLException {
        assertRejectedReason("\uD83D\uDE00".repeat(501));
    }

    private void assertRejectedReason(String reason) throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        assertThrows(ValidationException.class, () -> service.cancelRequest(OWNER, original.id(), reason));
        assertEquals(original, service.getOwnRequest(OWNER, original.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {5, 500})
    @DisplayName("REQ-A05/A11 Unicode boundary reasons persist and reload after restart")
    void acceptsReasonBoundariesAndReloads(int count) throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        String reason = "\uD83D\uDE00".repeat(count);
        var cancelled = requesterService(store, NOW.plusSeconds(90)).cancelRequest(OWNER, original.id(), "  " + reason + "  ");
        assertEquals(RequestStatus.CANCELLED, cancelled.status());
        assertEquals(original.createdAt(), cancelled.createdAt());
        assertEquals(NOW.plusSeconds(90), cancelled.updatedAt());
        var reopened = requesterService(new SQLiteManagerAssignmentStore(jdbcUrl), NOW);
        assertEquals(cancelled, reopened.getOwnRequest(OWNER, original.id()));
        assertEquals(reason, reopened.getOwnCancellationReason(OWNER, original.id()).orElseThrow());
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events WHERE action = 'REQUEST_CANCELLED' AND actor_id = 1"));
    }

    @Test
    @DisplayName("REQ-008/010/014 Manager-recorded request cancellation exposes escaped reason only")
    void cancelsManagerRecordedRequestWithEscapedReason() {
        var original = store.inTransaction(tx -> {
            var created = tx.createOpenRequest(1, DRAFT, NOW);
            tx.appendAuditEvent(new AuditEvent(created.id(), 3, "REQUEST_CREATED", "{}", NOW));
            tx.appendAuditEvent(new AuditEvent(created.id(), 3, "PRIVATE_MANAGER_NOTE", "secret", NOW));
            return created;
        });
        service.editRequest(OWNER, original.id(), DRAFT);
        String reason = "Duplicate \"tap\" \\ report\nPlease\tignore\rthis\b\f\u0001.";
        service.cancelRequest(OWNER, original.id(), reason);
        assertEquals(reason, service.getOwnCancellationReason(OWNER, original.id()).orElseThrow());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("REQ-012 LIF-012 edit/cancel audit failure rolls back and retry writes exactly once")
    void rollsBackMutationsAndRetries(boolean cancel) throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        execute("CREATE TRIGGER reject_change_audit BEFORE INSERT ON audit_events BEGIN SELECT RAISE(ABORT, 'private failure'); END");
        Runnable mutation = cancel ? () -> service.cancelRequest(OWNER, original.id(), "Not needed")
                : () -> service.editRequest(OWNER, original.id(), new RequestDraft("New title", "New description", "Room 2",
                        "Plumbing", ReportedUrgency.LOW));
        var failure = assertThrows(StorageException.class, mutation::run);
        assertFalse(failure.getMessage().contains("private failure"));
        assertEquals(original, service.getOwnRequest(OWNER, original.id()));
        assertTrue(service.getOwnCancellationReason(OWNER, original.id()).isEmpty());
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
        execute("DROP TRIGGER reject_change_audit");
        mutation.run();
        assertEquals(2, scalar("SELECT COUNT(*) FROM audit_events"));
        assertEquals(cancel ? RequestStatus.CANCELLED : RequestStatus.OPEN,
                service.getOwnRequest(OWNER, original.id()).status());
    }

    @Test
    @DisplayName("REQ-A10 LIF-016 stale mutations preserve actual Manager assignment and audit")
    void rejectsMutationsAfterManagerAssignment() throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        var manager = new ManagerRequestService(store, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC), TestSessions.MANAGER);
        var assigned = manager.assignOpenRequest(TestSessions.issue(3), original.id(), 4, ManagerPriority.HIGH);
        assertThrows(ValidationException.class, () -> service.editRequest(OWNER, original.id(), DRAFT));
        assertThrows(ValidationException.class, () -> service.cancelRequest(OWNER, original.id(), "Not needed"));
        assertEquals(assigned, service.getOwnRequest(OWNER, original.id()));
        assertEquals(2, scalar("SELECT COUNT(*) FROM audit_events"));
    }


    @Test
    @DisplayName("DAT-019 REQ-007 edit audit records changed field names without sensitive values")
    void editAuditNamesOnlyChangedFields() throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        service.editRequest(OWNER, original.id(), new RequestDraft("Private revised title", original.description(),
                original.location(), original.category(), original.reportedUrgency()));
        try (var connection = DriverManager.getConnection(jdbcUrl); var statement = connection.createStatement();
                var audit = statement.executeQuery("SELECT detail FROM audit_events WHERE action = 'REQUEST_EDITED'")) {
            assertTrue(audit.next());
            assertEquals("{\"changedFields\":[\"title\"]}", audit.getString(1));
            assertFalse(audit.next());
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("AUT-022 rechecks role and active flag on existing-session edits and cancellation")
    void rechecksMutationSession(boolean inactive) throws SQLException {
        var original = service.createRequest(OWNER, DRAFT);
        execute(inactive ? "UPDATE user_accounts SET active = 0 WHERE id = 1"
                : "UPDATE user_accounts SET role = 'TECHNICIAN' WHERE id = 1");
        assertThrows(AuthorizationException.class, () -> service.editRequest(OWNER, original.id(), DRAFT));
        assertThrows(AuthorizationException.class, () -> service.cancelRequest(OWNER, original.id(), "Not needed"));
        assertThrows(AuthorizationException.class, () -> service.getOwnCancellationReason(OWNER, original.id()));
        assertEquals(1, scalar("SELECT COUNT(*) FROM audit_events"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM maintenance_requests WHERE status = 'OPEN'"));
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

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
