package sg.edu.nus.facilityflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sg.edu.nus.facilityflow.auth.PasswordHasher;
import sg.edu.nus.facilityflow.auth.TestSessions;
import sg.edu.nus.facilityflow.model.AccountCredentials;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.TechnicianDashboardCounts;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

class ManagerAccountServiceTest {
    private static final Instant CREATED = Instant.parse("2026-09-20T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    private FakeStore store;
    private ManagerAccountService service;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        store.add(account(1, "manager-a", Role.FACILITIES_MANAGER, true), "unused");
        store.add(account(2, "manager-b", Role.FACILITIES_MANAGER, true), "unused");
        store.add(account(3, "tech-a", Role.TECHNICIAN, true), "unused");
        store.add(account(4, "requester-a", Role.REQUESTER, true), "unused");
        service = new ManagerAccountService(
                store, Clock.fixed(NOW, ZoneOffset.UTC), TestSessions.MANAGER);
    }

    @Test
    @DisplayName("MGR-012 AUT-023 creates a validated salted account, audits it, and clears the password")
    void createsAccountAndClearsPassword() {
        char[] password = "abcdefgh".toCharArray();

        UserAccount created = service.createAccount(
                TestSessions.issue(1), "  new.user  ", "  New User  ", Role.REQUESTER, password);

        assertEquals("new.user", created.username());
        assertEquals("New User", created.displayName());
        assertEquals(Role.REQUESTER, created.role());
        assertTrue(created.active());
        assertEquals(NOW, created.createdAt());
        String storedHash = store.hashes.get(created.id());
        assertFalse(storedHash.contains("abcdefgh"));
        assertTrue(PasswordHasher.matches("abcdefgh".toCharArray(), storedHash));
        assertEquals(List.of(new AccountAudit(
                1, created.id(), "ACCOUNT_CREATED",
                "{\"fields\":[\"username\",\"display_name\",\"role\",\"active\"]}", NOW)),
                store.accountAudits);
        assertEquals("\u0000".repeat(8), new String(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "bad user", "x!y", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    @DisplayName("AUT-002 rejects invalid username partitions before hashing or writing")
    void rejectsInvalidUsername(String username) {
        char[] password = "abcdefgh".toCharArray();

        assertThrows(ValidationException.class, () -> service.createAccount(
                TestSessions.issue(1), username, "New User", Role.REQUESTER, password));

        assertEquals(4, store.accounts.size());
        assertTrue(store.accountAudits.isEmpty());
        assertEquals("\u0000".repeat(8), new String(password));
    }

    @Test
    @DisplayName("AUT-002 rejects case-insensitive duplicate usernames without writing")
    void rejectsCaseInsensitiveDuplicateUsername() {
        char[] password = "abcdefgh".toCharArray();

        assertThrows(ValidationException.class, () -> service.createAccount(
                TestSessions.issue(1), "TECH-A", "Another Tech", Role.TECHNICIAN, password));

        assertEquals(4, store.accounts.size());
        assertTrue(store.accountAudits.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"X", " ",
        "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    @DisplayName("AUT-003 rejects display names outside the 2–80 code-point range")
    void rejectsInvalidDisplayName(String displayName) {
        assertThrows(ValidationException.class, () -> service.createAccount(
                TestSessions.issue(1), "valid-user", displayName,
                Role.REQUESTER, "abcdefgh".toCharArray()));

        assertEquals(4, store.accounts.size());
        assertTrue(store.accountAudits.isEmpty());
    }

    @Test
    @DisplayName("MGR-013 AUT-025/026 rejects self-deactivation and preserves an active Manager")
    void rejectsSelfDeactivation() {
        assertThrows(ValidationException.class,
                () -> service.setActive(TestSessions.issue(1), 1, false));

        assertTrue(store.accounts.get(1L).active());
        assertTrue(store.accountAudits.isEmpty());
    }

    @Test
    @DisplayName("MGR-012 AUT-024 deactivates and reactivates accounts with audit events")
    void changesAccountActiveStateAndAuditsIt() {
        UserAccount inactive = service.setActive(TestSessions.issue(1), 4, false);
        UserAccount active = service.setActive(TestSessions.issue(1), 4, true);

        assertFalse(inactive.active());
        assertTrue(active.active());
        assertEquals(List.of("ACCOUNT_DEACTIVATED", "ACCOUNT_REACTIVATED"),
                store.accountAudits.stream().map(AccountAudit::action).toList());
        assertEquals("{\"field\":\"active\",\"old\":true,\"new\":false}",
                store.accountAudits.get(0).detail());
        assertEquals("{\"field\":\"active\",\"old\":false,\"new\":true}",
                store.accountAudits.get(1).detail());
    }

    @Test
    @DisplayName("AUT-028 rejects a Manager changing their own role")
    void rejectsSelfRoleChange() {
        assertThrows(ValidationException.class,
                () -> service.changeRole(TestSessions.issue(1), 1, Role.REQUESTER));

        assertEquals(Role.FACILITIES_MANAGER, store.accounts.get(1L).role());
        assertTrue(store.accountAudits.isEmpty());
    }

    @Test
    @DisplayName("AUT-029 rejects changing a Technician role while active work exists")
    void rejectsTechnicianRoleChangeWithActiveWork() {
        store.activeWork = true;

        assertThrows(ValidationException.class,
                () -> service.changeRole(TestSessions.issue(1), 3, Role.REQUESTER));

        assertEquals(Role.TECHNICIAN, store.accounts.get(3L).role());
        assertTrue(store.accountAudits.isEmpty());
    }

    @Test
    @DisplayName("MGR-012 AUT-028 changes another account role and audits it")
    void changesAnotherAccountRole() {
        UserAccount changed = service.changeRole(
                TestSessions.issue(1), 4, Role.TECHNICIAN);

        assertEquals(Role.TECHNICIAN, changed.role());
        assertEquals(NOW, changed.updatedAt());
        assertEquals(List.of(new AccountAudit(
                1, 4, "ACCOUNT_ROLE_CHANGED",
                "{\"field\":\"role\",\"old\":\"REQUESTER\",\"new\":\"TECHNICIAN\"}", NOW)),
                store.accountAudits);
    }

    @Test
    @DisplayName("AUT-018–021 rejects non-Manager account administration without changes")
    void rejectsUnauthorizedAccountAdministration() {
        assertThrows(AuthorizationException.class,
                () -> service.listAccounts(TestSessions.issue(4)));
        assertThrows(AuthorizationException.class,
                () -> service.setActive(TestSessions.issue(4), 3, false));

        assertTrue(store.accounts.get(3L).active());
        assertTrue(store.accountAudits.isEmpty());
    }

    private static UserAccount account(long id, String username, Role role, boolean active) {
        return new UserAccount(id, username, "User " + id, role, active, CREATED, CREATED);
    }

    private record AccountAudit(
            long actorId, long targetId, String action, String detail, Instant occurredAt) {
    }

    private static final class FakeStore implements ManagerAssignmentStore {
        private final Map<Long, UserAccount> accounts = new HashMap<>();
        private final Map<Long, String> hashes = new HashMap<>();
        private final List<AccountAudit> accountAudits = new ArrayList<>();
        private boolean activeWork;
        private long nextId = 10;

        void add(UserAccount account, String hash) {
            accounts.put(account.id(), account);
            hashes.put(account.id(), hash);
        }

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
                public UserAccount createAccount(
                        String username,
                        String displayName,
                        Role role,
                        String passwordHash,
                        Instant createdAt) {
                    UserAccount account = new UserAccount(
                            nextId++, username, displayName, role, true, createdAt, createdAt);
                    add(account, passwordHash);
                    return account;
                }

                @Override
                public boolean updateAccountActive(long accountId, boolean active, Instant updatedAt) {
                    UserAccount current = accounts.get(accountId);
                    if (current == null) {
                        return false;
                    }
                    accounts.put(accountId, new UserAccount(
                            current.id(), current.username(), current.displayName(), current.role(), active,
                            current.createdAt(), updatedAt, current.sessionVersion() + (active ? 0 : 1)));
                    return true;
                }

                @Override
                public boolean updateAccountRole(long accountId, Role role, Instant updatedAt) {
                    UserAccount current = accounts.get(accountId);
                    if (current == null) {
                        return false;
                    }
                    accounts.put(accountId, new UserAccount(
                            current.id(), current.username(), current.displayName(), role, current.active(),
                            current.createdAt(), updatedAt, current.sessionVersion()));
                    return true;
                }

                @Override
                public boolean hasActiveTechnicianWork(long technicianId) {
                    return activeWork;
                }

                @Override
                public Optional<AccountCredentials> findCredentials(String username) {
                    return accounts.values().stream()
                            .filter(account -> account.username().equalsIgnoreCase(username))
                            .findFirst()
                            .map(account -> new AccountCredentials(account, hashes.get(account.id())));
                }

                @Override
                public void updatePassword(long accountId, String hash, boolean invalidate, Instant at) {
                    throw new AssertionError("Account administration must not reset passwords here");
                }

                @Override
                public void appendAccountAudit(
                        long actorId, long targetId, String action, Instant occurredAt) {
                    accountAudits.add(new AccountAudit(actorId, targetId, action, "{}", occurredAt));
                }

                @Override
                public void appendAccountAudit(
                        long actorId,
                        long targetId,
                        String action,
                        String detail,
                        Instant occurredAt) {
                    accountAudits.add(new AccountAudit(actorId, targetId, action, detail, occurredAt));
                }

                @Override
                public Optional<MaintenanceRequest> findRequest(long requestId) {
                    throw new AssertionError("Account administration must not read requests directly");
                }

                @Override
                public List<MaintenanceRequest> listRequests() {
                    throw new AssertionError("Account administration must not list requests");
                }

                @Override
                public MaintenanceRequest createOpenRequest(
                        long requesterId, RequestDraft draft, Instant createdAt) {
                    throw new AssertionError("Account administration must not create requests");
                }

                @Override
                public List<MaintenanceRequest> listOwnRequests(long requesterId) {
                    throw new AssertionError("Account administration must not list own requests");
                }

                @Override
                public Optional<MaintenanceRequest> findOwnRequest(long requesterId, long requestId) {
                    throw new AssertionError("Account administration must not read own requests");
                }

                @Override
                public List<MaintenanceRequest> listAssignedRequests(long technicianId) {
                    throw new AssertionError("Account administration must not list assigned requests");
                }

                @Override
                public TechnicianDashboardCounts getTechnicianDashboardCounts(long technicianId) {
                    throw new AssertionError("Account administration must not read dashboard counts");
                }

                @Override
                public Optional<MaintenanceRequest> findAssignedRequest(
                        long technicianId, long requestId) {
                    throw new AssertionError("Account administration must not read assignments");
                }

                @Override
                public List<WorkLog> listWorkLogs(long requestId) {
                    throw new AssertionError("Account administration must not read work logs");
                }

                @Override
                public WorkLog appendWorkLog(
                        long requestId, long authorId, String note, int minutes, Instant at) {
                    throw new AssertionError("Account administration must not write work logs");
                }

                @Override
                public boolean updateTechnicianRequest(
                        MaintenanceRequest request, long technicianId, RequestStatus expected) {
                    throw new AssertionError("Account administration must not update requests");
                }

                @Override
                public List<UserAccount> listActiveTechnicians() {
                    throw new AssertionError("Account administration must not list Technicians");
                }

                @Override
                public void updateRequest(MaintenanceRequest request) {
                    throw new AssertionError("Account administration must not update requests");
                }

                @Override
                public void appendAuditEvent(AuditEvent event) {
                    throw new AssertionError("Account administration must use account audit storage");
                }
            });
        }
    }
}
