package sg.edu.nus.facilityflow.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.TechnicianQueueFilter;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.model.WorkLog;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** TEC-001/003–013: authorized Technician queue and progress operations. */
public final class TechnicianRequestService {
    private final ManagerAssignmentStore store;
    private final Clock clock;
    private final SessionManager sessions;

    public TechnicianRequestService(ManagerAssignmentStore store, Clock clock, SessionManager sessions) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public List<MaintenanceRequest> listAssignedRequests(AuthenticatedSession session) {
        return listAssignedRequests(session, TechnicianQueueFilter.none());
    }

    public List<MaintenanceRequest> listAssignedRequests(
            AuthenticatedSession session, TechnicianQueueFilter filter) {
        TechnicianQueueFilter effectiveFilter = filter == null
                ? TechnicianQueueFilter.none() : filter;
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.TECHNICIAN);
            return transaction.listAssignedRequests(actor.id()).stream()
                    .filter(request -> Objects.equals(request.assigneeId(), actor.id()))
                    .filter(request -> matches(request, effectiveFilter))
                    .sorted(queueOrder())
                    .toList();
        });
    }

    public MaintenanceRequest getAssignedRequest(AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.TECHNICIAN);
            return requireAssignedRequest(transaction, actor.id(), requestId);
        });
    }

    public List<WorkLog> listWorkLogs(AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.TECHNICIAN);
            requireAssignedRequest(transaction, actor.id(), requestId);
            return List.copyOf(transaction.listWorkLogs(requestId));
        });
    }

    public MaintenanceRequest startWork(AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.TECHNICIAN);
            MaintenanceRequest request = requireAssignedRequest(transaction, actor.id(), requestId);
            if (request.status() != RequestStatus.ASSIGNED) {
                throw new ValidationException("Request must be ASSIGNED before work can start.");
            }
            Instant startedAt = clock.instant();
            MaintenanceRequest started = request.startWork(startedAt);
            requireCurrentAssignment(transaction, started, actor.id(), RequestStatus.ASSIGNED);
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(),
                    actor.id(),
                    "REQUEST_STARTED",
                    "{\"oldStatus\":\"ASSIGNED\",\"newStatus\":\"IN_PROGRESS\"}",
                    startedAt));
            return started;
        });
    }

    public WorkLog addWorkLog(
            AuthenticatedSession session, long requestId, String note, int minutesSpent) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.TECHNICIAN);
            MaintenanceRequest request = requireAssignedRequest(transaction, actor.id(), requestId);
            String normalizedNote = normalizeWorkLog(note);
            validateMinutes(minutesSpent);
            if (request.status() != RequestStatus.IN_PROGRESS) {
                throw new ValidationException("Work logs can be added only while a request is IN_PROGRESS.");
            }
            Instant loggedAt = clock.instant();
            MaintenanceRequest updated = request.recordWorkAt(loggedAt);
            requireCurrentAssignment(transaction, updated, actor.id(), RequestStatus.IN_PROGRESS);
            WorkLog workLog = transaction.appendWorkLog(
                    request.id(), actor.id(), normalizedNote, minutesSpent, loggedAt);
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(),
                    actor.id(),
                    "WORK_LOG_ADDED",
                    "{\"workLogId\":" + workLog.id() + ",\"minutesSpent\":" + minutesSpent + "}",
                    loggedAt));
            return workLog;
        });
    }

    public MaintenanceRequest completeWork(
            AuthenticatedSession session, long requestId, String resolutionSummary) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.TECHNICIAN);
            MaintenanceRequest request = requireAssignedRequest(transaction, actor.id(), requestId);
            String normalizedSummary = normalizeResolution(resolutionSummary);
            if (request.status() != RequestStatus.IN_PROGRESS) {
                throw new ValidationException("Request must be IN_PROGRESS before it can be completed.");
            }
            if (transaction.listWorkLogs(request.id()).isEmpty()) {
                throw new ValidationException("Add at least one work log before completing the request.");
            }
            Instant completedAt = clock.instant();
            MaintenanceRequest completed = request.complete(normalizedSummary, completedAt);
            requireCurrentAssignment(transaction, completed, actor.id(), RequestStatus.IN_PROGRESS);
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(),
                    actor.id(),
                    "REQUEST_COMPLETED",
                    "{\"oldStatus\":\"IN_PROGRESS\",\"newStatus\":\"COMPLETED\","
                            + "\"resolutionSummaryStored\":true}",
                    completedAt));
            return completed;
        });
    }

    private static MaintenanceRequest requireAssignedRequest(
            ManagerAssignmentStore.TransactionContext transaction,
            long technicianId,
            long requestId) {
        return transaction.findAssignedRequest(technicianId, requestId)
                .filter(request -> Objects.equals(request.assigneeId(), technicianId))
                .orElseThrow(() -> new AuthorizationException("Request is unavailable."));
    }

    private static void requireCurrentAssignment(
            ManagerAssignmentStore.TransactionContext transaction,
            MaintenanceRequest updated,
            long technicianId,
            RequestStatus expectedStatus) {
        if (!transaction.updateTechnicianRequest(updated, technicianId, expectedStatus)) {
            throw new AuthorizationException(
                    "The request assignment or status changed. Refresh and try again.");
        }
    }

    private static String normalizeWorkLog(String note) {
        if (note == null) {
            throw new ValidationException("Work-log note is required.");
        }
        String normalized = note.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 1_000) {
            throw new ValidationException("Work-log note must contain 1 to 1,000 characters.");
        }
        return normalized;
    }

    private static void validateMinutes(int minutesSpent) {
        if (minutesSpent < 1 || minutesSpent > 1_440) {
            throw new ValidationException("Minutes spent must be from 1 to 1,440.");
        }
    }

    private static String normalizeResolution(String summary) {
        if (summary == null) {
            throw new ValidationException("Resolution summary is required.");
        }
        String normalized = summary.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 10 || length > 2_000) {
            throw new ValidationException("Resolution summary must contain 10 to 2,000 characters.");
        }
        return normalized;
    }

    private static Comparator<MaintenanceRequest> queueOrder() {
        return Comparator.comparingInt(TechnicianRequestService::priorityOrder)
                .thenComparingInt(TechnicianRequestService::urgencyOrder)
                .thenComparing(MaintenanceRequest::assignedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MaintenanceRequest::displayId);
    }

    private static boolean matches(
            MaintenanceRequest request, TechnicianQueueFilter filter) {
        if (filter.status() != null && request.status() != filter.status()) {
            return false;
        }
        if (filter.category() != null && !request.category().equals(filter.category())) {
            return false;
        }
        if (filter.priority() != null && request.managerPriority() != filter.priority()) {
            return false;
        }
        if (filter.query() == null) {
            return true;
        }
        String query = filter.query().toLowerCase(Locale.ROOT);
        return request.displayId().toLowerCase(Locale.ROOT).contains(query)
                || request.title().toLowerCase(Locale.ROOT).contains(query)
                || request.location().toLowerCase(Locale.ROOT).contains(query);
    }

    private static int priorityOrder(MaintenanceRequest request) {
        ManagerPriority priority = request.managerPriority();
        if (priority == null) {
            return 4;
        }
        return switch (priority) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    private static int urgencyOrder(MaintenanceRequest request) {
        ReportedUrgency urgency = request.reportedUrgency();
        return switch (urgency) {
            case EMERGENCY -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
        };
    }
}
