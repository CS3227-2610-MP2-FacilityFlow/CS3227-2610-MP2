package sg.edu.nus.facilityflow.model;

import java.time.LocalDate;

/** Stable Manager queue filters; null values mean that a filter is not applied. */
public record ManagerRequestFilter(
        String query,
        RequestStatus status,
        String category,
        ManagerPriority priority,
        Long technicianId,
        LocalDate from,
        LocalDate through) {

    public static ManagerRequestFilter empty() {
        return new ManagerRequestFilter(null, null, null, null, null, null, null);
    }
}
