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
        Instant createdAt,
        Instant updatedAt) {

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
                createdAt,
                assignedAt);
    }
}
