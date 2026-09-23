package sg.edu.nus.facilityflow.auth;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.service.AuthorizationException;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore.TransactionContext;

/** AUT-015–022/030–033: issued sessions, live account checks and explicit revocation. */
public final class SessionManager {
    private final Set<AuthenticatedSession> issued = ConcurrentHashMap.newKeySet();
    private boolean closed;

    synchronized AuthenticatedSession open(UserAccount account) {
        if (closed) {
            throw denied();
        }
        var session = new AuthenticatedSession(account.id(), account.sessionVersion());
        issued.add(session);
        return session;
    }

    public UserAccount requireAccount(TransactionContext transaction, AuthenticatedSession session) {
        if (session == null || !issued.contains(session)) {
            throw denied();
        }
        var account = transaction.findAccount(session.accountId());
        if (account.isEmpty() || !account.get().active()
                || account.get().sessionVersion() != session.version()) {
            logout(session);
            throw denied();
        }
        return account.get();
    }

    public UserAccount requireRole(TransactionContext transaction, AuthenticatedSession session, Role role) {
        var account = requireAccount(transaction, session);
        if (account.role() != role) {
            throw new AuthorizationException("Your current role cannot perform this action.");
        }
        return account;
    }

    public void logout(AuthenticatedSession session) {
        if (session != null) {
            issued.remove(session);
        }
    }

    public void clear() {
        issued.clear();
    }

    public synchronized void close() {
        closed = true;
        clear();
    }

    private static AuthorizationException denied() {
        return new AuthorizationException("Your session has ended. Please sign in again.");
    }
}
