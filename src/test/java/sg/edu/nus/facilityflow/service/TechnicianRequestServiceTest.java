package sg.edu.nus.facilityflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.TestSessions;
import sg.edu.nus.facilityflow.model.AccountCredentials;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.TechnicianDashboardCounts;
import sg.edu.nus.facilityflow.model.TechnicianQueueFilter;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;
import sg.edu.nus.facilityflow.storage.StorageException;

class TechnicianRequestServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T08:00:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-24T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private FakeStore store;
    private TechnicianRequestService service;
    private AuthenticatedSession technicianSession;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        store.accounts.put(1L, account(1, Role.FACILITIES_MANAGER, true));
        store.accounts.put(2L, account(2, Role.TECHNICIAN, true));
        store.accounts.put(3L, account(3, Role.TECHNICIAN, true));
        store.accounts.put(4L, account(4, Role.REQUESTER, true));
        store.accounts.put(5L, account(5, Role.TECHNICIAN, false));
        store.requests.put(10L, request(10, RequestStatus.ASSIGNED, 2L,
                ManagerPriority.HIGH, ReportedUrgency.NORMAL, ASSIGNED_AT));
        store.requests.put(11L, request(11, RequestStatus.IN_PROGRESS, 2L,
                ManagerPriority.CRITICAL, ReportedUrgency.HIGH, ASSIGNED_AT));
        store.requests.put(12L, request(12, RequestStatus.COMPLETED, 2L,
                ManagerPriority.MEDIUM, ReportedUrgency.LOW, ASSIGNED_AT));
        store.requests.put(13L, request(13, RequestStatus.ASSIGNED, 3L,
                ManagerPriority.CRITICAL, ReportedUrgency.EMERGENCY, ASSIGNED_AT));
        technicianSession = TestSessions.issue(2);
        service = new TechnicianRequestService(
                store, Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER);
    }

    @Test
    @DisplayName("TEC-001/003 orders known assignment times before unknown times then display ID")
    void listsOnlyAssignedQueueInRequiredOrder() {
        store.requests.clear();
        store.requests.put(20L, request(20, RequestStatus.ASSIGNED, 2L,
                ManagerPriority.CRITICAL, ReportedUrgency.HIGH, ASSIGNED_AT.plusSeconds(300)));
        store.requests.put(21L, request(21, RequestStatus.IN_PROGRESS, 2L,
                ManagerPriority.CRITICAL, ReportedUrgency.EMERGENCY, ASSIGNED_AT.plusSeconds(600)));
        store.requests.put(25L, request(25, RequestStatus.COMPLETED, 2L,
                ManagerPriority.CRITICAL, ReportedUrgency.EMERGENCY, ASSIGNED_AT.plusSeconds(600)));
        store.requests.put(26L, request(26, RequestStatus.ASSIGNED, 2L,
                ManagerPriority.CRITICAL, ReportedUrgency.EMERGENCY, null));
        store.requests.put(27L, request(27, RequestStatus.ASSIGNED, 2L,
                ManagerPriority.CRITICAL, ReportedUrgency.EMERGENCY, null));
        store.requests.put(22L, request(22, RequestStatus.ASSIGNED, 2L,
                ManagerPriority.HIGH, ReportedUrgency.EMERGENCY, ASSIGNED_AT));
        store.requests.put(23L, request(23, RequestStatus.ASSIGNED, 2L,
                ManagerPriority.LOW, ReportedUrgency.LOW, ASSIGNED_AT.minusSeconds(600)));
        store.requests.put(24L, request(24, RequestStatus.ASSIGNED, 3L,
                ManagerPriority.CRITICAL, ReportedUrgency.EMERGENCY, ASSIGNED_AT.minusSeconds(900)));

        List<Long> ids = service.listAssignedRequests(technicianSession).stream()
                .map(MaintenanceRequest::id)
                .toList();

        assertEquals(List.of(21L, 25L, 26L, 27L, 20L, 22L, 23L), ids);
    }

    @Test
    @DisplayName("TEC-001 counts only the logged-in Technician's current queue states")
    void countsOnlyCurrentTechnicianQueueStates() {
        store.requests.put(14L, request(14, RequestStatus.OPEN, 2L,
                ManagerPriority.LOW, ReportedUrgency.LOW, null));
        store.requests.put(15L, request(15, RequestStatus.CLOSED, 2L,
                ManagerPriority.CRITICAL, ReportedUrgency.EMERGENCY, ASSIGNED_AT));

        assertEquals(new TechnicianDashboardCounts(1, 1, 1),
                service.getDashboardCounts(technicianSession));
        assertEquals(new TechnicianDashboardCounts(1, 0, 0),
                service.getDashboardCounts(TestSessions.issue(3)));
    }

    static Stream<Arguments> queueSearchQueries() {
        return Stream.of(
                Arguments.of("  ff-000010  "),
                Arguments.of("  BOILER leak  "),
                Arguments.of("  east WING  "));
    }

    @ParameterizedTest
    @MethodSource("queueSearchQueries")
    @DisplayName("TEC-002 LIF-018 performs trimmed case-insensitive display ID, title and location search")
    void searchesDisplayIdTitleAndLocation(String query) {
        store.requests.put(10L, withQueueFields(
                store.requests.get(10L), "Boiler Leak", "East Wing Plant Room", "HVAC"));

        List<Long> ids = service.listAssignedRequests(
                        technicianSession,
                        new TechnicianQueueFilter(query, null, null, null)).stream()
                .map(MaintenanceRequest::id)
                .toList();

        assertEquals(List.of(10L), ids);
    }

    @Test
    @DisplayName("TEC-002 LIF-017 filters independently with stable status and priority enums")
    void filtersByStatusCategoryAndPriorityIndependently() {
        store.requests.put(10L, withQueueFields(
                store.requests.get(10L), "Boiler Leak", "East Wing", "Electrical"));
        store.requests.put(12L, withQueueFields(
                store.requests.get(12L), "Light replacement", "West Wing", "Electrical"));

        assertEquals(List.of(11L), filteredIds(
                new TechnicianQueueFilter(null, RequestStatus.IN_PROGRESS, null, null)));
        assertEquals(List.of(10L), filteredIds(
                new TechnicianQueueFilter(null, null, null, ManagerPriority.HIGH)));
        assertEquals(List.of(10L, 12L), filteredIds(
                new TechnicianQueueFilter(null, null, "Electrical", null)));
    }

    @Test
    @DisplayName("TEC-002 applies search, status, category and priority filters together")
    void combinesAllQueueFilters() {
        store.requests.put(10L, withQueueFields(
                store.requests.get(10L), "Boiler Leak", "East Wing", "HVAC"));
        store.requests.put(11L, withQueueFields(
                store.requests.get(11L), "Boiler Leak", "East Wing", "HVAC"));

        List<Long> ids = filteredIds(new TechnicianQueueFilter(
                " boiler ", RequestStatus.ASSIGNED, "HVAC", ManagerPriority.HIGH));

        assertEquals(List.of(10L), ids);
    }

    @Test
    @DisplayName("TEC-002 LIF-019 returns an empty list when no assigned request matches")
    void returnsEmptyQueueForNoMatches() {
        List<MaintenanceRequest> result = service.listAssignedRequests(
                technicianSession,
                new TechnicianQueueFilter(
                        "no such request", RequestStatus.IN_PROGRESS, "Safety", ManagerPriority.LOW));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("TEC-002 treats blank optional search and category values as no filter")
    void treatsBlankOptionalFilterValuesAsAbsent() {
        List<Long> unfiltered = service.listAssignedRequests(technicianSession).stream()
                .map(MaintenanceRequest::id)
                .toList();

        List<Long> blankFiltered = filteredIds(
                new TechnicianQueueFilter(" \n ", null, "  ", null));

        assertEquals(unfiltered, blankFiltered);
    }

    static Stream<AuthenticatedSession> unauthorizedSessions() {
        return Stream.of(
                null,
                TestSessions.issue(1),
                TestSessions.issue(4),
                TestSessions.issue(5),
                TestSessions.issue(999));
    }

    @ParameterizedTest
    @MethodSource("unauthorizedSessions")
    @DisplayName("AUT-018/020/021 rejects absent, wrong-role, inactive and unknown Technician sessions")
    void rejectsUnauthorizedSessions(AuthenticatedSession session) {
        assertThrows(AuthorizationException.class, () -> service.listAssignedRequests(session));
        assertThrows(AuthorizationException.class, () -> service.getDashboardCounts(session));
        assertThrows(AuthorizationException.class, () -> service.getAssignedRequest(session, 10));
        assertThrows(AuthorizationException.class, () -> service.listWorkLogs(session, 11));
        assertThrows(AuthorizationException.class, () -> service.startWork(session, 10));
        assertThrows(AuthorizationException.class, () -> service.addWorkLog(session, 11, "Inspected pipe", 5));
        assertThrows(AuthorizationException.class,
                () -> service.completeWork(session, 11, "Repaired the leaking pipe."));
        assertNoProgressWrites();
    }

    @Test
    @DisplayName("TEC-011/TEC-A02 E2E-014 denies every read and write for another Technician's request")
    void deniesCrossTechnicianReadsAndWritesWithoutDisclosure() {
        AuthorizationException forbidden = assertThrows(
                AuthorizationException.class,
                () -> service.getAssignedRequest(technicianSession, 13));
        AuthorizationException missing = assertThrows(
                AuthorizationException.class,
                () -> service.getAssignedRequest(technicianSession, 999));
        assertEquals(missing.getMessage(), forbidden.getMessage());

        assertThrows(AuthorizationException.class,
                () -> service.listWorkLogs(technicianSession, 13));
        assertThrows(AuthorizationException.class,
                () -> service.startWork(technicianSession, 13));
        assertThrows(AuthorizationException.class,
                () -> service.addWorkLog(technicianSession, 13, "Inspected pipe", 5));
        assertThrows(AuthorizationException.class,
                () -> service.completeWork(technicianSession, 13, "Repaired the leaking pipe."));
        assertEquals(RequestStatus.ASSIGNED, store.requests.get(13L).status());
        assertEquals(3L, store.requests.get(13L).assigneeId());
        assertNoProgressWrites();
    }

    @Test
    @DisplayName("TEC-004/TEC-A01 LIF-011 starts assigned work and writes one transition audit")
    void startsAssignedWorkAndAuditsTransition() {
        MaintenanceRequest started = service.startWork(technicianSession, 10);

        assertEquals(RequestStatus.IN_PROGRESS, started.status());
        assertEquals(2L, started.assigneeId());
        assertEquals(ASSIGNED_AT, started.assignedAt());
        assertEquals(NOW, started.updatedAt());
        assertEquals(started, store.requests.get(10L));
        assertEquals(1, store.auditEvents.size());
        AuditEvent audit = store.auditEvents.getFirst();
        assertEquals("REQUEST_STARTED", audit.action());
        assertEquals(2L, audit.actorId());
        assertEquals("{\"oldStatus\":\"ASSIGNED\",\"newStatus\":\"IN_PROGRESS\"}", audit.detail());
        assertEquals(NOW, audit.occurredAt());
    }

    @Test
    @DisplayName("TEC-004 LIF-011 rejects start-work unless persisted state is ASSIGNED")
    void rejectsStartingFromInvalidStates() {
        assertThrows(ValidationException.class, () -> service.startWork(technicianSession, 11));
        assertThrows(ValidationException.class, () -> service.startWork(technicianSession, 12));

        assertEquals(RequestStatus.IN_PROGRESS, store.requests.get(11L).status());
        assertEquals(RequestStatus.COMPLETED, store.requests.get(12L).status());
        assertNoProgressWrites();
    }

    @Test
    @DisplayName("LIF-016 rejects a stale start when the conditional storage update fails")
    void rejectsStaleStartBeforeAudit() {
        store.rejectTechnicianUpdate = true;

        AuthorizationException error = assertThrows(
                AuthorizationException.class,
                () -> service.startWork(technicianSession, 10));

        assertTrue(error.getMessage().contains("changed"));
        assertEquals(RequestStatus.ASSIGNED, store.requests.get(10L).status());
        assertNoProgressWrites();
    }

    @Test
    @DisplayName("LIF-016 rejects stale completion before changing state or writing its audit")
    void rejectsStaleCompletionBeforeAudit() {
        WorkLog evidence = new WorkLog(
                1, 11, 2, "Replaced damaged fitting.", 45, NOW.minusSeconds(60));
        store.workLogs.add(evidence);
        store.rejectTechnicianUpdate = true;

        AuthorizationException error = assertThrows(
                AuthorizationException.class,
                () -> service.completeWork(
                        technicianSession, 11, "Replaced fitting and tested water flow."));

        assertTrue(error.getMessage().contains("changed"));
        assertEquals(RequestStatus.IN_PROGRESS, store.requests.get(11L).status());
        assertNull(store.requests.get(11L).resolutionSummary());
        assertEquals(List.of(evidence), store.workLogs);
        assertTrue(store.auditEvents.isEmpty());
    }

    @Test
    @DisplayName("TEC-005–007/TEC-A03 LIF-007 appends trimmed work evidence and updates request time")
    void appendsWorkLogAndAudit() {
        WorkLog workLog = service.addWorkLog(
                technicianSession, 11, "  Inspected the valve.\nTightened fitting.  ", 30);

        assertEquals(1L, workLog.id());
        assertEquals(11L, workLog.requestId());
        assertEquals(2L, workLog.authorId());
        assertEquals("Inspected the valve.\nTightened fitting.", workLog.note());
        assertEquals(30, workLog.minutesSpent());
        assertEquals(NOW, workLog.createdAt());
        assertEquals(List.of(workLog), store.workLogs);
        assertEquals(NOW, store.requests.get(11L).updatedAt());
        assertEquals(RequestStatus.IN_PROGRESS, store.requests.get(11L).status());
        assertEquals(1, store.auditEvents.size());
        assertEquals("WORK_LOG_ADDED", store.auditEvents.getFirst().action());
        assertEquals("{\"workLogId\":1,\"minutesSpent\":30}", store.auditEvents.getFirst().detail());
    }

    @Test
    @DisplayName("TEC-006 LIF-007 accepts one and 1,000 Unicode code points and minute boundaries")
    void acceptsWorkLogBoundaries() {
        WorkLog minimum = service.addWorkLog(technicianSession, 11, "x", 1);
        WorkLog maximum = service.addWorkLog(technicianSession, 11, "😀".repeat(1_000), 1_440);

        assertEquals(1, minimum.note().codePointCount(0, minimum.note().length()));
        assertEquals(1, minimum.minutesSpent());
        assertEquals(1_000, maximum.note().codePointCount(0, maximum.note().length()));
        assertEquals(1_440, maximum.minutesSpent());
        assertEquals(2, store.workLogs.size());
        assertEquals(2, store.auditEvents.size());
    }

    static Stream<Arguments> invalidWorkLogs() {
        return Stream.of(
                Arguments.of(null, 1),
                Arguments.of("   \n ", 1),
                Arguments.of("😀".repeat(1_001), 1),
                Arguments.of("Valid note", 0),
                Arguments.of("Valid note", 1_441));
    }

    @ParameterizedTest
    @MethodSource("invalidWorkLogs")
    @DisplayName("TEC-006 LIF-007 rejects invalid note and minute boundaries without writes")
    void rejectsInvalidWorkLog(String note, int minutesSpent) {
        Instant priorUpdate = store.requests.get(11L).updatedAt();

        assertThrows(ValidationException.class,
                () -> service.addWorkLog(technicianSession, 11, note, minutesSpent));

        assertEquals(priorUpdate, store.requests.get(11L).updatedAt());
        assertNoProgressWrites();
    }

    @Test
    @DisplayName("TEC-005 rejects work logs outside IN_PROGRESS")
    void rejectsWorkLogsOutsideInProgress() {
        assertThrows(ValidationException.class,
                () -> service.addWorkLog(technicianSession, 10, "Inspected pipe", 5));
        assertThrows(ValidationException.class,
                () -> service.addWorkLog(technicianSession, 12, "Inspected pipe", 5));

        assertNoProgressWrites();
    }

    @Test
    @DisplayName("TEC-008/TEC-A04 requires a work log before completion")
    void rejectsCompletionWithoutWorkLog() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> service.completeWork(technicianSession, 11, "Repaired the leaking pipe."));

        assertTrue(error.getMessage().contains("at least one work log"));
        assertEquals(RequestStatus.IN_PROGRESS, store.requests.get(11L).status());
        assertNull(store.requests.get(11L).resolutionSummary());
        assertNoProgressWrites();
    }

    @Test
    @DisplayName("TEC-008/009/TEC-A05 E2E-007 completes logged work and preserves assignment")
    void completesWorkForManagerReview() {
        WorkLog evidence = new WorkLog(
                1, 11, 2, "Replaced damaged fitting.", 45, NOW.minusSeconds(60));
        store.workLogs.add(evidence);

        MaintenanceRequest completed = service.completeWork(
                technicianSession, 11, "  Replaced fitting and tested water flow.  ");

        assertEquals(RequestStatus.COMPLETED, completed.status());
        assertEquals(2L, completed.assigneeId());
        assertEquals(ASSIGNED_AT, completed.assignedAt());
        assertEquals("Replaced fitting and tested water flow.", completed.resolutionSummary());
        assertEquals(NOW, completed.completedAt());
        assertEquals(NOW, completed.updatedAt());
        assertEquals(List.of(evidence), store.workLogs);
        assertEquals(completed, store.requests.get(11L));
        assertEquals(1, store.auditEvents.size());
        assertEquals("REQUEST_COMPLETED", store.auditEvents.getFirst().action());
        assertTrue(store.auditEvents.getFirst().detail().contains("\"newStatus\":\"COMPLETED\""));
    }

    static Stream<String> invalidResolutions() {
        return Stream.of(null, "123456789", "😀".repeat(2_001));
    }

    @ParameterizedTest
    @MethodSource("invalidResolutions")
    @DisplayName("TEC-008 rejects invalid resolution boundaries without changing logged work")
    void rejectsInvalidResolution(String summary) {
        WorkLog evidence = new WorkLog(
                1, 11, 2, "Replaced damaged fitting.", 45, NOW.minusSeconds(60));
        store.workLogs.add(evidence);

        assertThrows(ValidationException.class,
                () -> service.completeWork(technicianSession, 11, summary));

        assertEquals(RequestStatus.IN_PROGRESS, store.requests.get(11L).status());
        assertNull(store.requests.get(11L).resolutionSummary());
        assertEquals(List.of(evidence), store.workLogs);
        assertTrue(store.auditEvents.isEmpty());
    }

    @Test
    @DisplayName("LIF-015 hides terminal requests and rejects normal Technician writes")
    void rejectsTerminalRequestAccess() {
        store.requests.put(14L, request(14, RequestStatus.CLOSED, 2L,
                ManagerPriority.HIGH, ReportedUrgency.NORMAL, ASSIGNED_AT));
        store.requests.put(15L, request(15, RequestStatus.CANCELLED, 2L,
                ManagerPriority.HIGH, ReportedUrgency.NORMAL, ASSIGNED_AT));

        assertThrows(AuthorizationException.class,
                () -> service.getAssignedRequest(technicianSession, 14));
        assertThrows(AuthorizationException.class,
                () -> service.addWorkLog(technicianSession, 14, "Late update", 5));
        assertThrows(AuthorizationException.class,
                () -> service.getAssignedRequest(technicianSession, 15));
        assertThrows(AuthorizationException.class,
                () -> service.addWorkLog(technicianSession, 15, "Late update", 5));
        assertNoProgressWrites();
    }

    @Test
    @DisplayName("LIF-012 DAT-007 rolls back request and work log when audit storage fails")
    void rollsBackWorkLogWhenAuditFails() {
        MaintenanceRequest before = store.requests.get(11L);
        store.failAuditInsert = true;

        assertThrows(StorageException.class,
                () -> service.addWorkLog(technicianSession, 11, "Inspected pipe", 5));

        assertEquals(before, store.requests.get(11L));
        assertNoProgressWrites();
    }

    private void assertNoProgressWrites() {
        assertTrue(store.workLogs.isEmpty());
        assertTrue(store.auditEvents.isEmpty());
    }

    private List<Long> filteredIds(TechnicianQueueFilter filter) {
        return service.listAssignedRequests(technicianSession, filter).stream()
                .map(MaintenanceRequest::id)
                .toList();
    }

    private static UserAccount account(long id, Role role, boolean active) {
        return new UserAccount(
                id, "user" + id, "User " + id, role, active, CREATED_AT, CREATED_AT);
    }

    private static MaintenanceRequest request(
            long id,
            RequestStatus status,
            Long assigneeId,
            ManagerPriority priority,
            ReportedUrgency urgency,
            Instant assignedAt) {
        String resolution = status == RequestStatus.COMPLETED || status == RequestStatus.CLOSED
                ? "Completed fixture work."
                : null;
        Instant completedAt = resolution == null || assignedAt == null
                ? null
                : assignedAt.plusSeconds(300);
        Instant updatedAt = assignedAt == null ? CREATED_AT.plusSeconds(120) : assignedAt.plusSeconds(120);
        return new MaintenanceRequest(
                id,
                "FF-%06d".formatted(id),
                4,
                "Leaking pipe",
                "Water is leaking below the sink.",
                "Block A pantry",
                "Plumbing",
                urgency,
                priority,
                status,
                assigneeId,
                assignedAt,
                resolution,
                completedAt,
                CREATED_AT,
                updatedAt);
    }

    private static MaintenanceRequest withQueueFields(
            MaintenanceRequest request, String title, String location, String category) {
        return new MaintenanceRequest(
                request.id(),
                request.displayId(),
                request.requesterId(),
                title,
                request.description(),
                location,
                category,
                request.reportedUrgency(),
                request.managerPriority(),
                request.status(),
                request.assigneeId(),
                request.assignedAt(),
                request.resolutionSummary(),
                request.completedAt(),
                request.createdAt(),
                request.updatedAt());
    }

    private static final class FakeStore implements ManagerAssignmentStore {
        private final Map<Long, UserAccount> accounts = new HashMap<>();
        private final Map<Long, MaintenanceRequest> requests = new HashMap<>();
        private final List<WorkLog> workLogs = new ArrayList<>();
        private final List<AuditEvent> auditEvents = new ArrayList<>();
        private long nextWorkLogId = 1;
        private boolean rejectTechnicianUpdate;
        private boolean failAuditInsert;

        @Override
        public <T> T inTransaction(TransactionWork<T> work) {
            Map<Long, MaintenanceRequest> requestsBefore = new HashMap<>(requests);
            List<WorkLog> workLogsBefore = new ArrayList<>(workLogs);
            List<AuditEvent> auditsBefore = new ArrayList<>(auditEvents);
            long nextWorkLogIdBefore = nextWorkLogId;
            try {
                return work.execute(new FakeTransaction());
            } catch (RuntimeException exception) {
                requests.clear();
                requests.putAll(requestsBefore);
                workLogs.clear();
                workLogs.addAll(workLogsBefore);
                auditEvents.clear();
                auditEvents.addAll(auditsBefore);
                nextWorkLogId = nextWorkLogIdBefore;
                throw exception;
            }
        }

        private final class FakeTransaction implements TransactionContext {
            @Override
            public Optional<UserAccount> findAccount(long accountId) {
                return Optional.ofNullable(accounts.get(accountId));
            }

            @Override
            public Optional<AccountCredentials> findCredentials(String username) {
                throw new AssertionError("Technician service must not read password hashes");
            }

            @Override
            public void updatePassword(
                    long accountId, String hash, boolean invalidateSessions, Instant changedAt) {
                throw new AssertionError("Technician service must not update passwords");
            }

            @Override
            public void appendAccountAudit(
                    long actorId, long targetId, String action, Instant occurredAt) {
                throw new AssertionError("Technician service must not append account audits");
            }

            @Override
            public Optional<MaintenanceRequest> findRequest(long requestId) {
                throw new AssertionError("Technician service must use assignment-scoped reads");
            }

            @Override
            public List<MaintenanceRequest> listRequests() {
                throw new AssertionError("Technician service must use assignment-scoped reads");
            }

            @Override
            public MaintenanceRequest createOpenRequest(
                    long requesterId, RequestDraft draft, Instant createdAt) {
                throw new AssertionError("Technician service must not create requests");
            }

            @Override
            public List<MaintenanceRequest> listOwnRequests(long requesterId) {
                throw new AssertionError("Technician service must not use Requester reads");
            }

            @Override
            public Optional<MaintenanceRequest> findOwnRequest(long requesterId, long requestId) {
                throw new AssertionError("Technician service must not use Requester reads");
            }

            @Override
            public List<MaintenanceRequest> listAssignedRequests(long technicianId) {
                return requests.values().stream()
                        .filter(item -> item.assigneeId() != null && item.assigneeId() == technicianId)
                        .filter(item -> item.status() == RequestStatus.ASSIGNED
                                || item.status() == RequestStatus.IN_PROGRESS
                                || item.status() == RequestStatus.COMPLETED)
                        .toList();
            }

            @Override
            public TechnicianDashboardCounts getTechnicianDashboardCounts(long technicianId) {
                int assigned = countRequests(technicianId, RequestStatus.ASSIGNED);
                int inProgress = countRequests(technicianId, RequestStatus.IN_PROGRESS);
                int completed = countRequests(technicianId, RequestStatus.COMPLETED);
                return new TechnicianDashboardCounts(assigned, inProgress, completed);
            }

            private int countRequests(long technicianId, RequestStatus status) {
                return (int) requests.values().stream()
                        .filter(item -> item.assigneeId() != null && item.assigneeId() == technicianId)
                        .filter(item -> item.status() == status)
                        .count();
            }

            @Override
            public Optional<MaintenanceRequest> findAssignedRequest(
                    long technicianId, long requestId) {
                return Optional.ofNullable(requests.get(requestId))
                        .filter(item -> item.assigneeId() != null && item.assigneeId() == technicianId)
                        .filter(item -> item.status() == RequestStatus.ASSIGNED
                                || item.status() == RequestStatus.IN_PROGRESS
                                || item.status() == RequestStatus.COMPLETED);
            }

            @Override
            public List<WorkLog> listWorkLogs(long requestId) {
                return workLogs.stream()
                        .filter(log -> log.requestId() == requestId)
                        .sorted((left, right) -> {
                            int byTime = left.createdAt().compareTo(right.createdAt());
                            return byTime == 0 ? Long.compare(left.id(), right.id()) : byTime;
                        })
                        .toList();
            }

            @Override
            public WorkLog appendWorkLog(
                    long requestId,
                    long authorId,
                    String note,
                    int minutesSpent,
                    Instant createdAt) {
                WorkLog workLog = new WorkLog(
                        nextWorkLogId++, requestId, authorId, note, minutesSpent, createdAt);
                workLogs.add(workLog);
                return workLog;
            }

            @Override
            public boolean updateTechnicianRequest(
                    MaintenanceRequest request, long technicianId, RequestStatus expectedStatus) {
                if (rejectTechnicianUpdate) {
                    return false;
                }
                MaintenanceRequest current = requests.get(request.id());
                if (current == null
                        || current.status() != expectedStatus
                        || current.assigneeId() == null
                        || current.assigneeId() != technicianId) {
                    return false;
                }
                requests.put(request.id(), request);
                return true;
            }

            @Override
            public List<UserAccount> listActiveTechnicians() {
                throw new AssertionError("Technician service must not list assignment candidates");
            }

            @Override
            public void updateRequest(MaintenanceRequest request) {
                throw new AssertionError("Technician writes must use a conditional update");
            }

            @Override
            public void appendAuditEvent(AuditEvent event) {
                if (failAuditInsert) {
                    throw new StorageException("Injected audit failure", null);
                }
                auditEvents.add(event);
            }
        }
    }
}
