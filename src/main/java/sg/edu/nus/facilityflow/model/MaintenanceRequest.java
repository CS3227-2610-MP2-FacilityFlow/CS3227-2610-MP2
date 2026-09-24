package sg.edu.nus.facilityflow.model;

import java.time.Instant;
import java.util.Objects;

public record MaintenanceRequest(
        long id,
        String displayId,
        long requesterId,
        String title,
        String description,
        String location,
        String category,
        ReportedUrgency reportedUrgency,
        ManagerPriority managerPriority,
        RequestStatus status,
        Long assigneeId,
        Instant assignedAt,
        String resolutionSummary,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt) {

    public MaintenanceRequest(
            long id,
            String displayId,
            long requesterId,
            String title,
            String description,
            String location,
            String category,
            ReportedUrgency reportedUrgency,
            ManagerPriority managerPriority,
            RequestStatus status,
            Long assigneeId,
            Instant createdAt,
            Instant updatedAt) {
        this(id, displayId, requesterId, title, description, location, category,
                reportedUrgency, managerPriority, status, assigneeId, null, null, null,
                createdAt, updatedAt);
    }

    public MaintenanceRequest {
        if (id <= 0 || requesterId <= 0) {
            throw new IllegalArgumentException("Request and requester IDs must be positive");
        }
        Objects.requireNonNull(displayId, "displayId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(reportedUrgency, "reportedUrgency");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public MaintenanceRequest assignTo(long technicianId, ManagerPriority priority, Instant assignedAt) {
        if (status != RequestStatus.OPEN) {
            throw new IllegalStateException("Only an OPEN request can be assigned");
        }
        if (technicianId <= 0) {
            throw new IllegalArgumentException("Technician ID must be positive");
        }
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(assignedAt, "assignedAt");
        return new MaintenanceRequest(
                id,
                displayId,
                requesterId,
                title,
                description,
                location,
                category,
                reportedUrgency,
                priority,
                RequestStatus.ASSIGNED,
                technicianId,
                assignedAt,
                resolutionSummary,
                completedAt,
                createdAt,
                assignedAt);
    }

    public MaintenanceRequest startWork(Instant startedAt) {
        if (status != RequestStatus.ASSIGNED || assigneeId == null) {
            throw new IllegalStateException("Only assigned work can be started");
        }
        Objects.requireNonNull(startedAt, "startedAt");
        return copyWith(RequestStatus.IN_PROGRESS, resolutionSummary, completedAt, startedAt);
    }

    public MaintenanceRequest reassignTo(long technicianId, Instant reassignedAt) {
        if (status != RequestStatus.ASSIGNED && status != RequestStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only assigned or in-progress work can be reassigned");
        }
        if (technicianId <= 0 || Objects.equals(assigneeId, technicianId)) {
            throw new IllegalArgumentException("A different positive Technician ID is required");
        }
        Objects.requireNonNull(reassignedAt, "reassignedAt");
        return new MaintenanceRequest(
                id,
                displayId,
                requesterId,
                title,
                description,
                location,
                category,
                reportedUrgency,
                managerPriority,
                RequestStatus.ASSIGNED,
                technicianId,
                reassignedAt,
                resolutionSummary,
                completedAt,
                createdAt,
                reassignedAt);
    }

    public MaintenanceRequest recordWorkAt(Instant loggedAt) {
        if (status != RequestStatus.IN_PROGRESS || assigneeId == null) {
            throw new IllegalStateException("Work can be logged only while in progress");
        }
        Objects.requireNonNull(loggedAt, "loggedAt");
        return copyWith(status, resolutionSummary, completedAt, loggedAt);
    }

    public MaintenanceRequest complete(String summary, Instant completionTime) {
        if (status != RequestStatus.IN_PROGRESS || assigneeId == null) {
            throw new IllegalStateException("Only in-progress work can be completed");
        }
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(completionTime, "completionTime");
        return copyWith(RequestStatus.COMPLETED, summary, completionTime, completionTime);
    }

    private MaintenanceRequest copyWith(
            RequestStatus newStatus,
            String newResolutionSummary,
            Instant newCompletedAt,
            Instant newUpdatedAt) {
        return new MaintenanceRequest(
                id,
                displayId,
                requesterId,
                title,
                description,
                location,
                category,
                reportedUrgency,
                managerPriority,
                newStatus,
                assigneeId,
                assignedAt,
                newResolutionSummary,
                newCompletedAt,
                createdAt,
                newUpdatedAt);
    }
}
