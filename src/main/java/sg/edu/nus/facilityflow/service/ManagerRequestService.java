package sg.edu.nus.facilityflow.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import sg.edu.nus.facilityflow.auth.AuthenticatedSession;
import sg.edu.nus.facilityflow.auth.SessionManager;
import sg.edu.nus.facilityflow.model.AuditFilter;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.AuditRecord;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerDashboardSummary;
import sg.edu.nus.facilityflow.model.ManagerHistoryEntry;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ManagerRequestFilter;
import sg.edu.nus.facilityflow.model.RequestDraft;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.TechnicianWorkload;
import sg.edu.nus.facilityflow.model.UserAccount;
import sg.edu.nus.facilityflow.storage.ManagerAssignmentStore;

/** Manager request use cases. Authorization and lifecycle rules belong here, not in UI code. */
public final class ManagerRequestService {
    private final ManagerAssignmentStore store;
    private final Clock clock;
    private final SessionManager sessions;
    private final RequestValidator validator;

    public ManagerRequestService(ManagerAssignmentStore store, Clock clock, SessionManager sessions) {
        this(store, clock, sessions, new RequestValidator(Set.of(
                "Electrical", "Plumbing", "HVAC", "Structural", "Cleaning", "Safety", "Other")));
    }

    public ManagerRequestService(
            ManagerAssignmentStore store,
            Clock clock,
            SessionManager sessions,
            RequestValidator validator) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public List<MaintenanceRequest> listAllRequests(AuthenticatedSession session) {
        return listAllRequests(session, ManagerRequestFilter.empty());
    }

    public List<MaintenanceRequest> listAllRequests(
            AuthenticatedSession session, ManagerRequestFilter filter) {
        ManagerRequestFilter effective = filter == null ? ManagerRequestFilter.empty() : filter;
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            Map<Long, UserAccount> accounts = new HashMap<>();
            transaction.listAccounts().forEach(account -> accounts.put(account.id(), account));
            String query = normalizedQuery(effective.query());
            Instant from = effective.from() == null ? null
                    : effective.from().atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant until = effective.through() == null ? null
                    : effective.through().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            return transaction.listRequests().stream()
                    .filter(request -> effective.status() == null
                            || request.status() == effective.status())
                    .filter(request -> effective.category() == null || effective.category().isBlank()
                            || request.category().equals(effective.category()))
                    .filter(request -> effective.priority() == null
                            || request.managerPriority() == effective.priority())
                    .filter(request -> effective.technicianId() == null
                            || Objects.equals(request.assigneeId(), effective.technicianId()))
                    .filter(request -> from == null || !request.createdAt().isBefore(from))
                    .filter(request -> until == null || request.createdAt().isBefore(until))
                    .filter(request -> matchesQuery(request, accounts, query))
                    .sorted(managerQueueOrder())
                    .toList();
        });
    }

    public List<UserAccount> listActiveTechnicians(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            return List.copyOf(transaction.listActiveTechnicians());
        });
    }

    public List<UserAccount> listRequesters(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            return transaction.listAccounts().stream()
                    .filter(account -> account.role() == Role.REQUESTER)
                    .sorted(Comparator.comparing(UserAccount::displayName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparingLong(UserAccount::id))
                    .toList();
        });
    }

    public ManagerDashboardSummary getDashboardSummary(AuthenticatedSession session) {
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            List<MaintenanceRequest> requests = transaction.listRequests();
            Map<RequestStatus, Integer> statuses = new EnumMap<>(RequestStatus.class);
            Map<ManagerPriority, Integer> priorities = new EnumMap<>(ManagerPriority.class);
            for (RequestStatus status : RequestStatus.values()) {
                statuses.put(status, 0);
            }
            for (ManagerPriority priority : ManagerPriority.values()) {
                priorities.put(priority, 0);
            }
            for (MaintenanceRequest request : requests) {
                statuses.compute(request.status(), (key, count) -> count + 1);
                if (request.managerPriority() != null) {
                    priorities.compute(request.managerPriority(), (key, count) -> count + 1);
                }
            }
            List<TechnicianWorkload> workloads = transaction.listActiveTechnicians().stream()
                    .map(technician -> new TechnicianWorkload(
                            technician.id(),
                            technician.displayName(),
                            (int) requests.stream()
                                    .filter(request -> Objects.equals(
                                            request.assigneeId(), technician.id()))
                                    .filter(request -> request.status() == RequestStatus.ASSIGNED
                                            || request.status() == RequestStatus.IN_PROGRESS)
                                    .count()))
                    .sorted(Comparator.comparingInt(TechnicianWorkload::activeAssignments).reversed()
                            .thenComparing(TechnicianWorkload::displayName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparingLong(TechnicianWorkload::technicianId))
                    .toList();
            return new ManagerDashboardSummary(statuses, priorities, workloads);
        });
    }

    public List<ManagerHistoryEntry> getRequestHistory(
            AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            transaction.findRequest(requestId)
                    .orElseThrow(() -> new ValidationException("Request was not found."));
            return List.copyOf(transaction.listManagerHistory(requestId));
        });
    }

    public List<AuditRecord> listAuditRecords(
            AuthenticatedSession session, AuditFilter filter) {
        AuditFilter effective = filter == null ? AuditFilter.empty() : filter;
        return store.inTransaction(transaction -> {
            sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            String requestQuery = normalizedQuery(effective.requestQuery());
            String actorQuery = normalizedQuery(effective.actorQuery());
            String actionQuery = normalizedQuery(effective.actionQuery());
            Instant from = effective.from() == null ? null
                    : effective.from().atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant until = effective.through() == null ? null
                    : effective.through().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            return transaction.listAuditRecords().stream()
                    .filter(record -> requestQuery.isEmpty()
                            || lower(record.requestDisplayId()).contains(requestQuery)
                            || (record.requestId() != null
                                    && Long.toString(record.requestId()).contains(requestQuery)))
                    .filter(record -> actorQuery.isEmpty()
                            || lower(record.actorName()).contains(actorQuery)
                            || Long.toString(record.actorId()).contains(actorQuery))
                    .filter(record -> actionQuery.isEmpty()
                            || lower(record.action()).contains(actionQuery))
                    .filter(record -> from == null || !record.occurredAt().isBefore(from))
                    .filter(record -> until == null || record.occurredAt().isBefore(until))
                    .toList();
        });
    }

    public MaintenanceRequest assignOpenRequest(
            AuthenticatedSession session,
            long requestId,
            long technicianId,
            ManagerPriority priority) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            if (priority == null) {
                throw new ValidationException("Manager priority is required before assignment.");
            }
            MaintenanceRequest request = transaction.findRequest(requestId)
                    .orElseThrow(() -> new ValidationException("Request was not found."));
            if (request.status() != RequestStatus.OPEN) {
                throw new ValidationException("Request must be OPEN before it can be assigned.");
            }

            UserAccount technician = transaction.findAccount(technicianId)
                    .filter(UserAccount::active)
                    .filter(account -> account.role() == Role.TECHNICIAN)
                    .orElseThrow(() -> new ValidationException(
                            "Assignee must be an active Technician."));

            Instant assignedAt = clock.instant();
            MaintenanceRequest assigned = request.assignTo(technician.id(), priority, assignedAt);
            ensureUpdated(transaction.updateManagerRequest(assigned, RequestStatus.OPEN));
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(),
                    actor.id(),
                    "REQUEST_ASSIGNED",
                    "Assigned to account " + technician.id() + " with priority " + priority,
                    assignedAt));
            return assigned;
        });
    }

    public MaintenanceRequest reassignRequest(
            AuthenticatedSession session,
            long requestId,
            long technicianId,
            String reason) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(transaction, session, Role.FACILITIES_MANAGER);
            String normalizedReason = normalizeReason(reason);
            MaintenanceRequest request = transaction.findRequest(requestId)
                    .orElseThrow(() -> new ValidationException("Request was not found."));
            if (request.status() != RequestStatus.ASSIGNED
                    && request.status() != RequestStatus.IN_PROGRESS) {
                throw new ValidationException(
                        "Only ASSIGNED or IN_PROGRESS requests can be reassigned.");
            }
            if (Objects.equals(request.assigneeId(), technicianId)) {
                throw new ValidationException("Select a different Technician for reassignment.");
            }
            UserAccount technician = transaction.findAccount(technicianId)
                    .filter(UserAccount::active)
                    .filter(account -> account.role() == Role.TECHNICIAN)
                    .orElseThrow(() -> new ValidationException(
                            "Assignee must be an active Technician."));

            Instant reassignedAt = clock.instant();
            MaintenanceRequest reassigned = request.reassignTo(technician.id(), reassignedAt);
            ensureUpdated(transaction.updateManagerRequest(reassigned, request.status()));
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(),
                    actor.id(),
                    "REQUEST_REASSIGNED",
                    "{\"oldAssigneeId\":" + request.assigneeId()
                            + ",\"newAssigneeId\":" + technician.id()
                            + ",\"reason\":\"" + escapeJson(normalizedReason) + "\"}",
                    reassignedAt));
            return reassigned;
        });
    }

    public MaintenanceRequest closeCompletedRequest(
            AuthenticatedSession session, long requestId) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(
                    transaction, session, Role.FACILITIES_MANAGER);
            MaintenanceRequest request = requireStatus(
                    transaction, requestId, RequestStatus.COMPLETED,
                    "Only COMPLETED requests can be closed.");
            Instant at = clock.instant();
            MaintenanceRequest closed = request.close(at);
            ensureUpdated(transaction.updateManagerRequest(closed, RequestStatus.COMPLETED));
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(), actor.id(), "REQUEST_CLOSED", "{}", at));
            return closed;
        });
    }

    public MaintenanceRequest returnCompletedRequest(
            AuthenticatedSession session,
            long requestId,
            long technicianId,
            String reason) {
        return resumeRequest(
                session,
                requestId,
                technicianId,
                reason,
                RequestStatus.COMPLETED,
                "REQUEST_RETURNED");
    }

    public MaintenanceRequest reopenClosedRequest(
            AuthenticatedSession session,
            long requestId,
            long technicianId,
            String reason) {
        return resumeRequest(
                session,
                requestId,
                technicianId,
                reason,
                RequestStatus.CLOSED,
                "REQUEST_REOPENED");
    }

    public MaintenanceRequest cancelRequest(
            AuthenticatedSession session, long requestId, String reason) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(
                    transaction, session, Role.FACILITIES_MANAGER);
            String normalizedReason = normalizeReason(reason, "Cancellation");
            MaintenanceRequest request = transaction.findRequest(requestId)
                    .orElseThrow(() -> new ValidationException("Request was not found."));
            if (request.status() != RequestStatus.OPEN
                    && request.status() != RequestStatus.ASSIGNED
                    && request.status() != RequestStatus.IN_PROGRESS) {
                throw new ValidationException(
                        "Only OPEN, ASSIGNED, or IN_PROGRESS requests can be cancelled.");
            }
            Instant at = clock.instant();
            MaintenanceRequest cancelled = request.cancel(at);
            ensureUpdated(transaction.updateManagerRequest(cancelled, request.status()));
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(), actor.id(), "REQUEST_CANCELLED",
                    "{\"reason\":\"" + escapeJson(normalizedReason) + "\"}", at));
            return cancelled;
        });
    }

    public MaintenanceRequest recordOnBehalf(
            AuthenticatedSession session, long requesterId, RequestDraft draft) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(
                    transaction, session, Role.FACILITIES_MANAGER);
            UserAccount owner = transaction.findAccount(requesterId)
                    .filter(account -> account.role() == Role.REQUESTER)
                    .orElseThrow(() -> new ValidationException(
                            "Select an existing Requester account."));
            validateDraft(draft);
            Instant at = clock.instant();
            MaintenanceRequest request = transaction.createOpenRequest(owner.id(), draft, at);
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(), actor.id(), "REQUEST_RECORDED_ON_BEHALF",
                    "{\"ownerId\":" + owner.id() + ",\"status\":\"OPEN\"}", at));
            return request;
        });
    }

    public MaintenanceRequest correctRequest(
            AuthenticatedSession session,
            long requestId,
            RequestDraft draft,
            ManagerPriority priority) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(
                    transaction, session, Role.FACILITIES_MANAGER);
            MaintenanceRequest request = transaction.findRequest(requestId)
                    .orElseThrow(() -> new ValidationException("Request was not found."));
            validateDraft(draft);
            if (priority == null
                    && request.managerPriority() != null) {
                throw new ValidationException(
                        "Manager priority cannot be cleared after triage.");
            }
            if (priority == null
                    && request.status() != RequestStatus.OPEN
                    && request.status() != RequestStatus.CANCELLED) {
                throw new ValidationException(
                        "Manager priority is required for assigned or historical work.");
            }
            Instant at = clock.instant();
            MaintenanceRequest corrected = request.correct(draft, priority, at);
            if (!transaction.updateManagerCorrection(corrected, request.status())) {
                throw new ValidationException("Request changed. Refresh and try again.");
            }
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(), actor.id(), "REQUEST_CORRECTED",
                    "{\"fields\":[\"title\",\"description\",\"location\","
                            + "\"category\",\"reportedUrgency\",\"managerPriority\"]}", at));
            return corrected;
        });
    }

    private MaintenanceRequest resumeRequest(
            AuthenticatedSession session,
            long requestId,
            long technicianId,
            String reason,
            RequestStatus expected,
            String action) {
        return store.inTransaction(transaction -> {
            UserAccount actor = sessions.requireRole(
                    transaction, session, Role.FACILITIES_MANAGER);
            String normalizedReason = normalizeReason(reason, "Transition");
            MaintenanceRequest request = requireStatus(
                    transaction,
                    requestId,
                    expected,
                    expected == RequestStatus.COMPLETED
                            ? "Only COMPLETED requests can be returned."
                            : "Only CLOSED requests can be reopened.");
            UserAccount technician = requireActiveTechnician(transaction, technicianId);
            Instant at = clock.instant();
            MaintenanceRequest resumed = expected == RequestStatus.COMPLETED
                    ? request.returnForRework(technician.id(), at)
                    : request.reopen(technician.id(), at);
            ensureUpdated(transaction.updateManagerRequest(resumed, expected));
            transaction.appendAuditEvent(new AuditEvent(
                    request.id(), actor.id(), action,
                    "{\"technicianId\":" + technician.id()
                            + ",\"reason\":\"" + escapeJson(normalizedReason) + "\"}", at));
            return resumed;
        });
    }

    private void validateDraft(RequestDraft draft) {
        if (draft == null) {
            throw new ValidationException("Request details are required.");
        }
        Map<String, String> errors = validator.validate(draft);
        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(" ", errors.values()));
        }
    }

    private static UserAccount requireActiveTechnician(
            ManagerAssignmentStore.TransactionContext transaction, long technicianId) {
        return transaction.findAccount(technicianId)
                .filter(UserAccount::active)
                .filter(account -> account.role() == Role.TECHNICIAN)
                .orElseThrow(() -> new ValidationException(
                        "Assignee must be an active Technician."));
    }

    private static MaintenanceRequest requireStatus(
            ManagerAssignmentStore.TransactionContext transaction,
            long requestId,
            RequestStatus expected,
            String message) {
        MaintenanceRequest request = transaction.findRequest(requestId)
                .orElseThrow(() -> new ValidationException("Request was not found."));
        if (request.status() != expected) {
            throw new ValidationException(message);
        }
        return request;
    }

    private static void ensureUpdated(boolean updated) {
        if (!updated) {
            throw new ValidationException("Request changed. Refresh and try again.");
        }
    }

    private static String normalizeReason(String reason) {
        return normalizeReason(reason, "Reassignment");
    }

    private static String normalizeReason(String reason, String label) {
        if (reason == null) {
            throw new ValidationException(label + " reason is required.");
        }
        String normalized = reason.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 5 || length > 500) {
            throw new ValidationException(label + " reason must contain 5 to 500 characters.");
        }
        return normalized;
    }

    private static String normalizedQuery(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean matchesQuery(
            MaintenanceRequest request,
            Map<Long, UserAccount> accounts,
            String query) {
        if (query.isEmpty()) {
            return true;
        }
        UserAccount requester = accounts.get(request.requesterId());
        UserAccount technician = request.assigneeId() == null
                ? null : accounts.get(request.assigneeId());
        return lower(request.displayId()).contains(query)
                || lower(request.title()).contains(query)
                || lower(request.location()).contains(query)
                || (requester != null && (lower(requester.displayName()).contains(query)
                        || lower(requester.username()).contains(query)))
                || (technician != null && (lower(technician.displayName()).contains(query)
                        || lower(technician.username()).contains(query)));
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u%04x".formatted((int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static Comparator<MaintenanceRequest> managerQueueOrder() {
        return Comparator.comparingInt(ManagerRequestService::groupOrder)
                .thenComparingInt(ManagerRequestService::withinGroupOrder)
                .thenComparingLong(ManagerRequestService::groupTimestampOrder)
                .thenComparing(MaintenanceRequest::displayId);
    }

    private static int groupOrder(MaintenanceRequest request) {
        return switch (request.status()) {
            case OPEN -> 0;
            case ASSIGNED, IN_PROGRESS -> 1;
            case COMPLETED, CLOSED, CANCELLED -> 2;
        };
    }

    private static int withinGroupOrder(MaintenanceRequest request) {
        if (request.status() == RequestStatus.OPEN) {
            return switch (request.reportedUrgency()) {
                case EMERGENCY -> 0;
                case HIGH -> 1;
                case NORMAL -> 2;
                case LOW -> 3;
            };
        }
        if (request.status() == RequestStatus.ASSIGNED
                || request.status() == RequestStatus.IN_PROGRESS) {
            if (request.managerPriority() == null) {
                return 4;
            }
            return switch (request.managerPriority()) {
                case CRITICAL -> 0;
                case HIGH -> 1;
                case MEDIUM -> 2;
                case LOW -> 3;
            };
        }
        return 0;
    }

    private static long groupTimestampOrder(MaintenanceRequest request) {
        if (request.status() == RequestStatus.OPEN) {
            return request.createdAt().toEpochMilli();
        }
        if (request.status() == RequestStatus.ASSIGNED
                || request.status() == RequestStatus.IN_PROGRESS) {
            return request.updatedAt().toEpochMilli();
        }
        return -request.updatedAt().toEpochMilli();
    }
}
