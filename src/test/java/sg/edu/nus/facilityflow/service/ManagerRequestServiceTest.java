package sg.edu.nus.facilityflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

class ManagerRequestServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-09-18T10:00:00Z");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-18T12:00:00Z");

    private FakeStore store;
    private ManagerRequestService service;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        store.accounts.put(1L, account(1, Role.FACILITIES_MANAGER, true));
        store.accounts.put(2L, account(2, Role.TECHNICIAN, true));
        store.accounts.put(3L, account(3, Role.REQUESTER, true));
        store.accounts.put(4L, account(4, Role.TECHNICIAN, false));
        store.requests.put(10L, openRequest(10));
        store.requests.put(11L, openRequest(11).assignTo(2, ManagerPriority.MEDIUM, CREATED_AT));
        service = new ManagerRequestService(
                store, Clock.fixed(ASSIGNED_AT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("MGR-004 MGR-005 LIF-011 assigns an OPEN request and audits it")
    void assignsOpenRequestAndAuditsTransition() {
        MaintenanceRequest result = service.assignOpenRequest(
                new AuthenticatedSession(1), 10, 2, ManagerPriority.HIGH);

        assertEquals(RequestStatus.ASSIGNED, result.status());
        assertEquals(ManagerPriority.HIGH, result.managerPriority());
        assertEquals(2L, result.assigneeId());
        assertEquals(ASSIGNED_AT, result.updatedAt());
        assertEquals(result, store.requests.get(10L));
        assertEquals(1, store.auditEvents.size());
        assertEquals("REQUEST_ASSIGNED", store.auditEvents.getFirst().action());
        assertEquals(1L, store.auditEvents.getFirst().actorId());
    }

    @Test
    @DisplayName("MGR-014 rejects assignment to a non-Technician without changes")
    void rejectsNonTechnicianAssignee() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> service.assignOpenRequest(
                        new AuthenticatedSession(1), 10, 3, ManagerPriority.HIGH));

        assertEquals("Assignee must be an active Technician.", error.getMessage());
        assertRequestRemainsOpen();
    }

    @Test
    @DisplayName("MGR-014 AUT-027 rejects assignment to an inactive Technician")
    void rejectsInactiveTechnician() {
        assertThrows(
                ValidationException.class,
                () -> service.assignOpenRequest(
                        new AuthenticatedSession(1), 10, 4, ManagerPriority.HIGH));

        assertRequestRemainsOpen();
    }

    @Test
    @DisplayName("AUT-018 AUT-020 AUT-021 rejects a forged Manager action")
    void rejectsUnauthorizedActorBeforeWriting() {
        AuthorizationException error = assertThrows(
                AuthorizationException.class,
                () -> service.assignOpenRequest(
                        new AuthenticatedSession(3), 10, 2, ManagerPriority.HIGH));

        assertEquals(
                "Your session is not authorized for this operation. Please sign in again.",
                error.getMessage());
        assertRequestRemainsOpen();
    }

    @Test
    @DisplayName("MGR-005 LIF-011 rejects assignment unless persisted state is OPEN")
    void rejectsInvalidPersistedState() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> service.assignOpenRequest(
                        new AuthenticatedSession(1), 11, 2, ManagerPriority.HIGH));

        assertEquals("Request must be OPEN before it can be assigned.", error.getMessage());
        assertEquals(RequestStatus.ASSIGNED, store.requests.get(11L).status());
        assertEquals(0, store.auditEvents.size());
    }

    @Test
    @DisplayName("MGR-004 rejects assignment without a Manager priority")
    void rejectsMissingPriority() {
        ValidationException error = assertThrows(
                ValidationException.class,
                () -> service.assignOpenRequest(new AuthenticatedSession(1), 10, 2, null));

        assertEquals("Manager priority is required before assignment.", error.getMessage());
        assertRequestRemainsOpen();
    }

    @Test
    @DisplayName("MGR-021 orders the Manager queue deterministically")
    void ordersManagerQueueByOperationalRules() {
        store.requests.put(12L, request(
                12, RequestStatus.OPEN, ReportedUrgency.EMERGENCY, null, null,
                CREATED_AT.plusSeconds(600), CREATED_AT.plusSeconds(600)));
        store.requests.put(13L, request(
                13, RequestStatus.ASSIGNED, ReportedUrgency.NORMAL, ManagerPriority.CRITICAL, 2L,
                CREATED_AT, CREATED_AT.plusSeconds(300)));
        store.requests.put(14L, request(
                14, RequestStatus.CLOSED, ReportedUrgency.NORMAL, ManagerPriority.LOW, 2L,
                CREATED_AT, CREATED_AT.plusSeconds(1800)));
        store.requests.put(15L, request(
                15, RequestStatus.COMPLETED, ReportedUrgency.NORMAL, ManagerPriority.LOW, 2L,
                CREATED_AT, CREATED_AT.plusSeconds(2400)));

        String orderedIds = service.listAllRequests(new AuthenticatedSession(1)).stream()
                .map(MaintenanceRequest::displayId)
                .collect(Collectors.joining(","));

        assertEquals(
                "FF-000012,FF-000010,FF-000013,FF-000011,FF-000015,FF-000014",
                orderedIds);
    }

    private void assertRequestRemainsOpen() {
        MaintenanceRequest request = store.requests.get(10L);
        assertEquals(RequestStatus.OPEN, request.status());
        assertNull(request.managerPriority());
        assertNull(request.assigneeId());
        assertEquals(0, store.auditEvents.size());
    }

    private static UserAccount account(long id, Role role, boolean active) {
        return new UserAccount(
                id, "user" + id, "User " + id, role, active, CREATED_AT, CREATED_AT);
    }

    private static MaintenanceRequest openRequest(long id) {
        return request(
                id,
                RequestStatus.OPEN,
                ReportedUrgency.HIGH,
                null,
                null,
                CREATED_AT,
                CREATED_AT);
    }

    private static MaintenanceRequest request(
            long id,
            RequestStatus status,
            ReportedUrgency urgency,
            ManagerPriority priority,
            Long assigneeId,
            Instant createdAt,
            Instant updatedAt) {
        return new MaintenanceRequest(
                id,
                "FF-%06d".formatted(id),
                3,
                "Leaking pipe",
                "Water is leaking below the sink.",
                "Block A pantry",
                "Plumbing",
                urgency,
                priority,
                status,
                assigneeId,
                createdAt,
                updatedAt);
    }

    private static final class FakeStore implements ManagerAssignmentStore {
        private final Map<Long, UserAccount> accounts = new HashMap<>();
        private final Map<Long, MaintenanceRequest> requests = new HashMap<>();
        private final List<AuditEvent> auditEvents = new ArrayList<>();

        @Override
        public <T> T inTransaction(TransactionWork<T> work) {
            return work.execute(new TransactionContext() {
                @Override
                public Optional<UserAccount> findAccount(long accountId) {
                    return Optional.ofNullable(accounts.get(accountId));
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
                public MaintenanceRequest createOpenRequest(long requesterId, RequestDraft draft, Instant createdAt) {
                    throw new AssertionError("Manager assignment must not create requests");
                }

                @Override
                public List<MaintenanceRequest> listOwnRequests(long requesterId) {
                    throw new AssertionError("Manager assignment must not use Requester reads");
                }

                @Override
                public Optional<MaintenanceRequest> findOwnRequest(long requesterId, long requestId) {
                    throw new AssertionError("Manager assignment must not use Requester reads");
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
                public void appendAuditEvent(AuditEvent event) {
                    auditEvents.add(event);
                }
            });
        }
    }
}
