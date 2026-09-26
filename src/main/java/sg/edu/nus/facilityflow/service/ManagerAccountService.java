package sg.edu.nus.facilityflow.service;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.PasswordHasher;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** MGR-012–013 and AUT-023–029 account administration rules. */
public final class ManagerAccountService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,32}");

    private final ManagerAssignmentStore store;
    private final Clock clock;
    private final SessionManager sessions;

    public ManagerAccountService(
            ManagerAssignmentStore store, Clock clock, SessionManager sessions) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public List<UserAccount> listAccounts(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            return List.copyOf(transaction.listAccounts());
        });
    }

    public UserAccount createAccount(
            AuthenticatedSession session,
            String username,
            String displayName,
            Role role,
            char[] password) {
        try {
            return store.inTransaction(transaction -> {
                UserAccount actor = sessions.requireRole(
                        transaction, session, Role.FACILITIES_MANAGER);
                String normalizedUsername = normalizeUsername(username);
                String normalizedDisplayName = normalizeDisplayName(displayName);
                if (role == null) {
                    throw new ValidationException("Account role is required.");
                }
                if (transaction.findCredentials(normalizedUsername).isPresent()) {
                    throw new ValidationException("Username is already in use.");
                }
                String passwordHash = PasswordHasher.hash(password);
                var at = clock.instant();
                UserAccount created = transaction.createAccount(
                        normalizedUsername, normalizedDisplayName, role, passwordHash, at);
                transaction.appendAccountAudit(
                        actor.id(), created.id(), "ACCOUNT_CREATED",
                        "{\"fields\":[\"username\",\"display_name\",\"role\",\"active\"]}", at);
                return created;
            });
        } finally {
            clear(password);
        }
    }

    public UserAccount setActive(
            AuthenticatedSession session, long accountId, boolean active) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(
                    transaction, session, Role.FACILITIES_MANAGER);
            UserAccount target = transaction.findAccount(accountId)
                    .orElseThrow(() -> new ValidationException("Account was not found."));
            if (!active && actor.id() == target.id()) {
                throw new ValidationException("You cannot deactivate your own active session.");
            }
            if (!active && target.active() && target.role() == Role.FACILITIES_MANAGER
                    && activeManagerCount(transaction.listAccounts()) <= 1) {
                throw new ValidationException("At least one active Facilities Manager is required.");
            }
            if (target.active() == active) {
                return target;
            }
            var at = clock.instant();
            if (!transaction.updateAccountActive(target.id(), active, at)) {
                throw new ValidationException("Account changed. Refresh and try again.");
            }
            transaction.appendAccountAudit(
                    actor.id(), target.id(),
                    active ? "ACCOUNT_REACTIVATED" : "ACCOUNT_DEACTIVATED",
                    "{\"field\":\"active\",\"old\":" + !active
                            + ",\"new\":" + active + "}",
                    at);
            return transaction.findAccount(target.id())
                    .orElseThrow(() -> new ValidationException("Account was not found."));
        });
    }

    public UserAccount changeRole(
            AuthenticatedSession session, long accountId, Role role) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(
                    transaction, session, Role.FACILITIES_MANAGER);
            if (role == null) {
                throw new ValidationException("Account role is required.");
            }
            UserAccount target = transaction.findAccount(accountId)
                    .orElseThrow(() -> new ValidationException("Account was not found."));
            if (actor.id() == target.id()) {
                throw new ValidationException("You cannot change your own role.");
            }
            if (target.role() == role) {
                return target;
            }
            if (target.active() && target.role() == Role.FACILITIES_MANAGER
                    && activeManagerCount(transaction.listAccounts()) <= 1) {
                throw new ValidationException("At least one active Facilities Manager is required.");
            }
            if (target.role() == Role.TECHNICIAN
                    && transaction.hasActiveTechnicianWork(target.id())) {
                throw new ValidationException(
                        "Reassign or cancel this Technician's active work before changing their role.");
            }
            var at = clock.instant();
            if (!transaction.updateAccountRole(target.id(), role, at)) {
                throw new ValidationException("Account changed. Refresh and try again.");
            }
            transaction.appendAccountAudit(
                    actor.id(), target.id(), "ACCOUNT_ROLE_CHANGED",
                    "{\"field\":\"role\",\"old\":\"" + target.role()
                            + "\",\"new\":\"" + role + "\"}",
                    at);
            return transaction.findAccount(target.id())
                    .orElseThrow(() -> new ValidationException("Account was not found."));
        });
    }

    private static long activeManagerCount(List<UserAccount> accounts) {
        return accounts.stream()
                .filter(UserAccount::active)
                .filter(account -> account.role() == Role.FACILITIES_MANAGER)
                .count();
    }

    private static String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.strip();
        if (!USERNAME.matcher(normalized).matches()) {
            throw new ValidationException(
                    "Username must contain 3–32 letters, digits, dots, underscores, or hyphens.");
        }
        return normalized;
    }

    private static String normalizeDisplayName(String displayName) {
        String normalized = displayName == null ? "" : displayName.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 2 || length > 80) {
            throw new ValidationException("Display name must contain 2–80 characters.");
        }
        return normalized;
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
