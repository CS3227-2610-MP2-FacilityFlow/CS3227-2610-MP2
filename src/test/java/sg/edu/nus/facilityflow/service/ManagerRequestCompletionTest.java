package sg.edu.nus.facilityflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import sg.edu.nus.facilityflow.auth.TestSessions;
import sg.edu.nus.facilityflow.model.AccountCredentials;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.AuditFilter;
import sg.edu.nus.facilityflow.model.AuditRecord;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerHistoryEntry;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ManagerRequestFilter;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.TechnicianDashboardCounts;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

class ManagerRequestCompletionTest {
    private static final Instant CREATED = Instant.parse("2026-09-20T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");
    private static final RequestDraft CORRECTION = new RequestDraft(
            "Corrected title", "Corrected request description.", "Room 22",
            "Electrical", ReportedUrgency.NORMAL);

    private FakeStore store;
    private ManagerRequestService service;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        store.accounts.put(1L, account(1, "manager", "Manager", Role.FACILITIES_MANAGER, true));
        store.accounts.put(2L, account(2, "tech-a", "Technician A", Role.TECHNICIAN, true));
        store.accounts.put(3L, account(3, "requester-a", "Requester A", Role.REQUESTER, true));
        store.accounts.put(4L, account(4, "tech-b", "Technician B", Role.TECHNICIAN, true));
        store.accounts.put(5L, account(5, "inactive", "Inactive Technician", Role.TECHNICIAN, false));
        store.accounts.put(6L, account(6, "inactive-requester", "Inactive Requester", Role.REQUESTER, false));
        service = new ManagerRequestService(
                store, Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER);
    }

    @Test
    @DisplayName("MGR-007 LIF-011 closes completed work while retaining completion metadata")
    void closesCompletedRequestAndAuditsTransition() {
        MaintenanceRequest completed = request(10, RequestStatus.COMPLETED, 2L,
                ManagerPriority.HIGH, ReportedUrgency.HIGH, CREATED, CREATED.plusSeconds(100),
                "Repair completed safely.", CREATED.plusSeconds(100));
        store.requests.put(10L, completed);

        MaintenanceRequest result = service.closeCompletedRequest(TestSessions.issue(1), 10);

        assertEquals(RequestStatus.CLOSED, result.status());
        assertEquals(completed.resolutionSummary(), result.resolutionSummary());
        assertEquals(completed.completedAt(), result.completedAt());
        assertEquals(2L, result.assigneeId());
        assertEquals(NOW, result.updatedAt());
        assertAudit("REQUEST_CLOSED", "{}");
    }

    @Test
    @DisplayName("MGR-008 LIF-014 returns completed work and preserves completion history")
    void returnsCompletedRequestForRework() {
        MaintenanceRequest completed = request(10, RequestStatus.COMPLETED, 2L,
                ManagerPriority.HIGH, ReportedUrgency.HIGH, CREATED, CREATED.plusSeconds(100),
                "Repair completed safely.", CREATED.plusSeconds(100));
        store.requests.put(10L, completed);

        MaintenanceRequest result = service.returnCompletedRequest(
                TestSessions.issue(1), 10, 4, "Further inspection required");

        assertEquals(RequestStatus.ASSIGNED, result.status());
        assertEquals(4L, result.assigneeId());
        assertEquals(NOW, result.assignedAt());
        assertEquals(completed.resolutionSummary(), result.resolutionSummary());
        assertEquals(completed.completedAt(), result.completedAt());
        assertAudit("REQUEST_RETURNED", "Further inspection required");
    }

    @Test
    @DisplayName("MGR-009 LIF-014 reopens closed work and preserves completion history")
    void reopensClosedRequest() {
        MaintenanceRequest closed = request(10, RequestStatus.CLOSED, 2L,
                ManagerPriority.MEDIUM, ReportedUrgency.NORMAL, CREATED, CREATED.plusSeconds(200),
                "Repair completed safely.", CREATED.plusSeconds(100));
        store.requests.put(10L, closed);

        MaintenanceRequest result = service.reopenClosedRequest(
                TestSessions.issue(1), 10, 4, "Problem has happened again");

        assertEquals(RequestStatus.ASSIGNED, result.status());
        assertEquals(4L, result.assigneeId());
        assertEquals(closed.resolutionSummary(), result.resolutionSummary());
        assertEquals(closed.completedAt(), result.completedAt());
        assertAudit("REQUEST_REOPENED", "Problem has happened again");
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"OPEN", "ASSIGNED", "IN_PROGRESS"})
    @DisplayName("MGR-010 LIF-011 cancels each permitted request state")
    void cancelsEachPermittedState(RequestStatus status) {
        Long assignee = status == RequestStatus.OPEN ? null : 2L;
        ManagerPriority priority = status == RequestStatus.OPEN ? null : ManagerPriority.MEDIUM;
        store.requests.put(10L, request(10, status, assignee, priority,
                ReportedUrgency.NORMAL, CREATED, CREATED, null, null));

        MaintenanceRequest result = service.cancelRequest(
                TestSessions.issue(1), 10, "Work is no longer required");

        assertEquals(RequestStatus.CANCELLED, result.status());
        assertEquals(assignee, result.assigneeId());
        assertAudit("REQUEST_CANCELLED", "Work is no longer required");
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"COMPLETED", "CLOSED", "CANCELLED"})
    @DisplayName("MGR-010 LIF-015 rejects cancellation from review or terminal states")
    void rejectsCancellationFromForbiddenStates(RequestStatus status) {
        String summary = status == RequestStatus.CANCELLED ? null : "Repair completed safely.";
        Instant completed = status == RequestStatus.CANCELLED ? null : CREATED.plusSeconds(100);
        store.requests.put(10L, request(10, status, 2L, ManagerPriority.MEDIUM,
                ReportedUrgency.NORMAL, CREATED, CREATED, summary, completed));

        assertThrows(ValidationException.class, () -> service.cancelRequest(
                TestSessions.issue(1), 10, "Work is no longer required"));

        assertEquals(status, store.requests.get(10L).status());
        assertTrue(store.auditEvents.isEmpty());
    }

    @Test
    @DisplayName("MGR-008/009 LIF-005 rejects invalid reason boundaries and inactive assignees")
    void rejectsInvalidResumeInputsWithoutWriting() {
        store.requests.put(10L, request(10, RequestStatus.COMPLETED, 2L,
                ManagerPriority.HIGH, ReportedUrgency.HIGH, CREATED, CREATED,
                "Repair completed safely.", CREATED));

        assertThrows(ValidationException.class, () -> service.returnCompletedRequest(
                TestSessions.issue(1), 10, 4, "four"));
        assertThrows(ValidationException.class, () -> service.returnCompletedRequest(
                TestSessions.issue(1), 10, 4, "x".repeat(501)));
        assertThrows(ValidationException.class, () -> service.returnCompletedRequest(
                TestSessions.issue(1), 10, 5, "Further inspection required"));

        assertEquals(RequestStatus.COMPLETED, store.requests.get(10L).status());
        assertTrue(store.auditEvents.isEmpty());
    }

    @Test
    @DisplayName("LIF-016 rejects a stale Manager transition before its audit write")
    void rejectsStaleTransitionWithoutAudit() {
        store.requests.put(10L, request(10, RequestStatus.COMPLETED, 2L,
                ManagerPriority.HIGH, ReportedUrgency.HIGH, CREATED, CREATED,
                "Repair completed safely.", CREATED));
        store.acceptUpdates = false;

        assertThrows(ValidationException.class,
                () -> service.closeCompletedRequest(TestSessions.issue(1), 10));

        assertEquals(RequestStatus.COMPLETED, store.requests.get(10L).status());
        assertTrue(store.auditEvents.isEmpty());
    }

    @Test
    @DisplayName("MGR-019 records an audited OPEN request for an existing Requester regardless of active state")
    void recordsRequestOnBehalfOfExistingRequester() {
        RequestDraft draft = new RequestDraft(
                "Leaking pipe", "Water is leaking under the sink.", "Room 12",
                "Plumbing", ReportedUrgency.HIGH);

        MaintenanceRequest active = service.recordOnBehalf(TestSessions.issue(1), 3, draft);
        MaintenanceRequest inactive = service.recordOnBehalf(TestSessions.issue(1), 6, draft);

        assertEquals(3L, active.requesterId());
        assertEquals(6L, inactive.requesterId());
        assertEquals(RequestStatus.OPEN, active.status());
        assertEquals(RequestStatus.OPEN, inactive.status());
        assertNull(active.managerPriority());
        assertNull(active.assigneeId());
        assertEquals(List.of("REQUEST_RECORDED_ON_BEHALF", "REQUEST_RECORDED_ON_BEHALF"),
                store.auditEvents.stream().map(AuditEvent::action).toList());
        assertTrue(store.auditEvents.get(0).detail().contains("\"ownerId\":3"));
        assertTrue(store.auditEvents.get(1).detail().contains("\"ownerId\":6"));
    }

    @Test
    @DisplayName("MGR-019 rejects a non-Requester owner without creating data")
    void rejectsInvalidOwnerForRecordOnBehalf() {
        RequestDraft draft = new RequestDraft(
                "Leaking pipe", "Water is leaking under the sink.", "Room 12",
                "Plumbing", ReportedUrgency.HIGH);

        assertThrows(ValidationException.class,
                () -> service.recordOnBehalf(TestSessions.issue(1), 2, draft));

        assertTrue(store.requests.isEmpty());
        assertTrue(store.auditEvents.isEmpty());
    }

    @Test
    @DisplayName("MGR-019 lists both active and inactive existing Requester owners")
    void listsExistingRequestersForRecordOnBehalf() {
        assertEquals(List.of(6L, 3L), service.listRequesters(TestSessions.issue(1)).stream()
                .map(UserAccount::id).toList());
    }

    @ParameterizedTest
    @EnumSource(RequestStatus.class)
    @DisplayName("MGR-020 LIF-023 corrects permitted fields in every state without lifecycle changes")
    void correctsPermittedFieldsInEveryState(RequestStatus status) {
        Long assignee = status == RequestStatus.OPEN ? null : 2L;
        ManagerPriority oldPriority = status == RequestStatus.OPEN ? null : ManagerPriority.LOW;
        String summary = status == RequestStatus.COMPLETED || status == RequestStatus.CLOSED
                ? "Repair completed safely." : null;
        Instant completed = summary == null ? null : CREATED.plusSeconds(100);
        MaintenanceRequest original = request(10, status, assignee, oldPriority,
                ReportedUrgency.LOW, CREATED, CREATED.plusSeconds(200), summary, completed);
        store.requests.put(10L, original);

        MaintenanceRequest result = service.correctRequest(
                TestSessions.issue(1), 10, CORRECTION, ManagerPriority.CRITICAL);

        assertEquals(status, result.status());
        assertEquals(original.displayId(), result.displayId());
        assertEquals(original.requesterId(), result.requesterId());
        assertEquals(original.assigneeId(), result.assigneeId());
        assertEquals(original.assignedAt(), result.assignedAt());
        assertEquals(original.resolutionSummary(), result.resolutionSummary());
        assertEquals(original.completedAt(), result.completedAt());
        assertEquals(original.createdAt(), result.createdAt());
        assertEquals(CORRECTION.title(), result.title());
        assertEquals(ManagerPriority.CRITICAL, result.managerPriority());
        assertAudit("REQUEST_CORRECTED", "\"fields\"");
    }

    @Test
    @DisplayName("MGR-020 LIF-023 permits null priority when correcting CANCELLED-from-OPEN work")
    void correctsCancelledFromOpenRequestWithoutInventingPriority() {
        store.requests.put(10L, request(10, RequestStatus.CANCELLED, null, null,
                ReportedUrgency.LOW, CREATED, CREATED, null, null));

        MaintenanceRequest corrected = service.correctRequest(
                TestSessions.issue(1), 10, CORRECTION, null);

        assertEquals(RequestStatus.CANCELLED, corrected.status());
        assertNull(corrected.managerPriority());
        assertAudit("REQUEST_CORRECTED", "managerPriority");
    }

    @Test
    @DisplayName("MGR-020 LIF-023 rejects clearing a priority previously set by triage")
    void rejectsClearingPreviouslySetManagerPriority() {
        store.requests.put(10L, request(10, RequestStatus.CANCELLED, 2L, ManagerPriority.HIGH,
                ReportedUrgency.LOW, CREATED, CREATED, null, null));

        assertThrows(ValidationException.class,
                () -> service.correctRequest(TestSessions.issue(1), 10, CORRECTION, null));

        assertEquals(ManagerPriority.HIGH, store.requests.get(10L).managerPriority());
        assertTrue(store.auditEvents.isEmpty());
    }

    @Test
    @DisplayName("MGR-002/003 LIF-017–019 combines case-insensitive search and stable filters")
    void searchesAndFiltersManagerQueue() {
        store.requests.put(10L, request(10, RequestStatus.OPEN, null, null,
                ReportedUrgency.HIGH, Instant.parse("2026-09-21T08:00:00Z"), CREATED, null, null));
        store.requests.put(11L, titledRequest(11, "Lift motor fault", "East Tower", 3, 2L,
                RequestStatus.ASSIGNED, "HVAC", ManagerPriority.HIGH,
                Instant.parse("2026-09-22T08:00:00Z")));

        ManagerRequestFilter matching = new ManagerRequestFilter(
                "  TECH-A ", RequestStatus.ASSIGNED, "HVAC", ManagerPriority.HIGH, 2L,
                LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 22));
        ManagerRequestFilter empty = new ManagerRequestFilter(
                "no match", null, null, null, null, null, null);

        assertEquals(List.of("FF-000011"), service.listAllRequests(
                TestSessions.issue(1), matching).stream().map(MaintenanceRequest::displayId).toList());
        assertTrue(service.listAllRequests(TestSessions.issue(1), empty).isEmpty());
    }

    @Test
    @DisplayName("MGR-002 LIF-018 searches ID, title, location, Requester, and Technician")
    void searchesEverySpecifiedManagerQueueField() {
        store.requests.put(11L, titledRequest(11, "Lift motor fault", "East Tower", 3, 2L,
                RequestStatus.ASSIGNED, "HVAC", ManagerPriority.HIGH,
                Instant.parse("2026-09-22T08:00:00Z")));
        var session = TestSessions.issue(1);

        for (String query : List.of(
                "ff-000011", "MOTOR", " east tower ", "requester-a", "TECHNICIAN A")) {
            List<Long> result = service.listAllRequests(
                    session,
                    new ManagerRequestFilter(query, null, null, null, null, null, null)).stream()
                    .map(MaintenanceRequest::id)
                    .toList();
            assertEquals(List.of(11L), result, query);
        }
    }

    @Test
    @DisplayName("MGR-015 dashboard counts statuses, priorities, and active Technician work")
    void summarizesManagerDashboard() {
        store.requests.put(10L, request(10, RequestStatus.ASSIGNED, 2L,
                ManagerPriority.CRITICAL, ReportedUrgency.HIGH, CREATED, CREATED, null, null));
        store.requests.put(11L, request(11, RequestStatus.IN_PROGRESS, 2L,
                ManagerPriority.HIGH, ReportedUrgency.NORMAL, CREATED, CREATED, null, null));
        store.requests.put(12L, request(12, RequestStatus.COMPLETED, 4L,
                ManagerPriority.HIGH, ReportedUrgency.LOW, CREATED, CREATED,
                "Repair completed safely.", CREATED));

        var summary = service.getDashboardSummary(TestSessions.issue(1));

        assertEquals(1, summary.statusCounts().get(RequestStatus.ASSIGNED));
        assertEquals(1, summary.statusCounts().get(RequestStatus.IN_PROGRESS));
        assertEquals(1, summary.statusCounts().get(RequestStatus.COMPLETED));
        assertEquals(0, summary.statusCounts().get(RequestStatus.OPEN));
        assertEquals(2, summary.priorityCounts().get(ManagerPriority.HIGH));
        assertEquals(1, summary.priorityCounts().get(ManagerPriority.CRITICAL));
        assertEquals(List.of(2, 0), summary.technicianWorkloads().stream()
                .map(workload -> workload.activeAssignments()).toList());
    }

    @Test
    @DisplayName("MGR-001/018 returns complete history and filters immutable audit records")
    void readsHistoryAndFiltersAuditRecords() {
        store.requests.put(10L, request(10, RequestStatus.OPEN, null, null,
                ReportedUrgency.HIGH, CREATED, CREATED, null, null));
        store.history.add(new ManagerHistoryEntry(
                "Work log", "Technician A", "Inspected equipment (20 min)", NOW.minusSeconds(60)));
        store.auditRecords.add(new AuditRecord(
                1, 10L, "FF-000010", 1, "Manager", "REQUEST_ASSIGNED",
                "REQUEST", 10, "{}", NOW.minusSeconds(60)));
        store.auditRecords.add(new AuditRecord(
                2, null, null, 3, "Requester A", "LOGIN_SUCCEEDED",
                "ACCOUNT", 3, "{}", NOW.minusSeconds(90_000)));

        assertEquals(store.history, service.getRequestHistory(TestSessions.issue(1), 10));
        List<AuditRecord> records = service.listAuditRecords(TestSessions.issue(1),
                new AuditFilter(" ff-000010 ", "manager", "assigned",
                        LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 22)));

        assertEquals(List.of(1L), records.stream().map(AuditRecord::id).toList());
    }

    @Test
    @DisplayName("MGR-018 request audit filter does not treat an account target ID as a request")
    void requestAuditFilterExcludesAccountTargetsWithSameNumericId() {
        store.auditRecords.add(new AuditRecord(
                1, 10L, "FF-000010", 1, "Manager", "REQUEST_ASSIGNED",
                "REQUEST", 10, "{}", NOW));
        store.auditRecords.add(new AuditRecord(
                2, null, null, 1, "Manager", "ACCOUNT_ROLE_CHANGED",
                "ACCOUNT", 10, "{}", NOW));

        List<AuditRecord> result = service.listAuditRecords(
                TestSessions.issue(1), new AuditFilter("10", null, null, null, null));

        assertEquals(List.of(1L), result.stream().map(AuditRecord::id).toList());
    }

    @Test
    @DisplayName("AUT-018–021 protects all new Manager reads and writes at the service boundary")
    void rejectsUnauthorizedManagerOperationsWithoutChanges() {
        store.requests.put(10L, request(10, RequestStatus.OPEN, null, null,
                ReportedUrgency.HIGH, CREATED, CREATED, null, null));

        assertThrows(AuthorizationException.class,
                () -> service.getDashboardSummary(TestSessions.issue(3)));
        assertThrows(AuthorizationException.class,
                () -> service.listAuditRecords(TestSessions.issue(3), AuditFilter.empty()));
        assertThrows(AuthorizationException.class,
                () -> service.cancelRequest(TestSessions.issue(3), 10, "Not required now"));

        assertEquals(RequestStatus.OPEN, store.requests.get(10L).status());
        assertTrue(store.auditEvents.isEmpty());
    }

    private void assertAudit(String action, String detailPart) {
        assertEquals(1, store.auditEvents.size());
        AuditEvent audit = store.auditEvents.getFirst();
        assertEquals(action, audit.action());
        assertEquals(1L, audit.actorId());
        assertEquals(NOW, audit.occurredAt());
        assertTrue(audit.detail().contains(detailPart), audit.detail());
    }

    private static UserAccount account(
            long id, String username, String name, Role role, boolean active) {
        return new UserAccount(id, username, name, role, active, CREATED, CREATED);
    }

    private static MaintenanceRequest request(
            long id,
            RequestStatus status,
            Long assignee,
            ManagerPriority priority,
            ReportedUrgency urgency,
            Instant created,
            Instant updated,
            String summary,
            Instant completed) {
        return new MaintenanceRequest(
                id, "FF-%06d".formatted(id), 3, "Leaking pipe",
                "Water is leaking under the sink.", "Room 12", "Plumbing", urgency,
                priority, status, assignee, assignee == null ? null : created.plusSeconds(10),
                summary, completed, created, updated);
    }

    private static MaintenanceRequest titledRequest(
            long id,
            String title,
            String location,
            long requester,
            Long assignee,
            RequestStatus status,
            String category,
            ManagerPriority priority,
            Instant created) {
        return new MaintenanceRequest(
                id, "FF-%06d".formatted(id), requester, title,
                "A sufficiently detailed description.", location, category,
                ReportedUrgency.NORMAL, priority, status, assignee,
                assignee == null ? null : created, null, null, created, created);
    }

    private static final class FakeStore implements ManagerAssignmentStore {
        private final Map<Long, UserAccount> accounts = new HashMap<>();
        private final Map<Long, MaintenanceRequest> requests = new HashMap<>();
        private final List<AuditEvent> auditEvents = new ArrayList<>();
        private final List<ManagerHistoryEntry> history = new ArrayList<>();
        private final List<AuditRecord> auditRecords = new ArrayList<>();
        private boolean acceptUpdates = true;
        private long nextRequestId = 100;

        @Override
        public <T> T inTransaction(TransactionWork<T> work) {
            return work.execute(new TransactionContext() {
                @Override
                public Optional<UserAccount> findAccount(long accountId) {
                    return Optional.ofNullable(accounts.get(accountId));
                }

                @Override
                public List<UserAccount> listAccounts() {
                    return List.copyOf(accounts.values());
                }

                @Override
                public Optional<AccountCredentials> findCredentials(String username) {
                    throw new AssertionError("Manager request operations must not read credentials");
                }

                @Override
                public void updatePassword(long accountId, String hash, boolean invalidate, Instant at) {
                    throw new AssertionError("Manager request operations must not change passwords");
                }

                @Override
                public void appendAccountAudit(long actor, long target, String action, Instant at) {
                    throw new AssertionError("Manager request operations must not write account audits");
                }

                @Override
                public Optional<MaintenanceRequest> findRequest(long requestId) {
                    return Optional.ofNullable(requests.get(requestId));
                }

                @Override
                public List<MaintenanceRequest> listRequests() {
                    return List.copyOf(requests.values());
                }

                @Override
                public MaintenanceRequest createOpenRequest(
                        long requesterId, RequestDraft draft, Instant createdAt) {
                    long id = nextRequestId++;
                    MaintenanceRequest created = new MaintenanceRequest(
                            id, "FF-%06d".formatted(id), requesterId, draft.title(),
                            draft.description(), draft.location(), draft.category(), draft.urgency(),
                            null, RequestStatus.OPEN, null, createdAt, createdAt);
                    requests.put(id, created);
                    return created;
                }

                @Override
                public List<MaintenanceRequest> listOwnRequests(long requesterId) {
                    throw new AssertionError("Manager reads must not use Requester storage");
                }

                @Override
                public Optional<MaintenanceRequest> findOwnRequest(long requesterId, long requestId) {
                    throw new AssertionError("Manager reads must not use Requester storage");
                }

                @Override
                public List<MaintenanceRequest> listAssignedRequests(long technicianId) {
                    throw new AssertionError("Manager reads must not use Technician storage");
                }

                @Override
                public TechnicianDashboardCounts getTechnicianDashboardCounts(long technicianId) {
                    throw new AssertionError("Manager reads must not use Technician storage");
                }

                @Override
                public Optional<MaintenanceRequest> findAssignedRequest(long technicianId, long requestId) {
                    throw new AssertionError("Manager reads must not use Technician storage");
                }

                @Override
                public List<WorkLog> listWorkLogs(long requestId) {
                    throw new AssertionError("Manager history uses its dedicated storage operation");
                }

                @Override
                public WorkLog appendWorkLog(
                        long requestId, long authorId, String note, int minutes, Instant at) {
                    throw new AssertionError("Manager operations must not append work logs");
                }

                @Override
                public boolean updateTechnicianRequest(
                        MaintenanceRequest request, long technicianId, RequestStatus expected) {
                    throw new AssertionError("Manager operations must use Manager updates");
                }

                @Override
                public List<UserAccount> listActiveTechnicians() {
                    return accounts.values().stream()
                            .filter(UserAccount::active)
                            .filter(account -> account.role() == Role.TECHNICIAN)
                            .toList();
                }

                @Override
                public void updateRequest(MaintenanceRequest request) {
                    requests.put(request.id(), request);
                }

                @Override
                public boolean updateManagerRequest(
                        MaintenanceRequest request, RequestStatus expectedStatus) {
                    MaintenanceRequest current = requests.get(request.id());
                    if (!acceptUpdates || current == null || current.status() != expectedStatus) {
                        return false;
                    }
                    requests.put(request.id(), request);
                    return true;
                }

                @Override
                public boolean updateManagerCorrection(
                        MaintenanceRequest request, RequestStatus expectedStatus) {
                    return updateManagerRequest(request, expectedStatus);
                }

                @Override
                public void appendAuditEvent(AuditEvent event) {
                    auditEvents.add(event);
                }

                @Override
                public List<ManagerHistoryEntry> listManagerHistory(long requestId) {
                    return List.copyOf(history);
                }

                @Override
                public List<AuditRecord> listAuditRecords() {
                    return List.copyOf(auditRecords);
                }
            });
        }
    }
}
