package sg.edu.nus.facilityflow.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditDescriptionsTest {
    @Test
    @DisplayName("LIF-010 request creation and correction hide structured audit fields")
    void describesStructuredEventsWithoutJson() {
        String creation = AuditDescriptions.describe("REQUEST_CREATED", "{\"status\":\"OPEN\"}");
        String correction = AuditDescriptions.describe("REQUEST_CORRECTED",
                "{\"changedFields\":[\"title\",\"location\"]}");

        assertEquals("Request created.", creation);
        assertEquals("Request details corrected.", correction);
        assertFalse(creation.contains("{"));
        assertFalse(correction.contains("{"));
    }

    @Test
    @DisplayName("LIF-010 assignment identifies the Technician by username")
    void describesAssignmentWithUsername() {
        String description = AuditDescriptions.describe("REQUEST_ASSIGNED",
                "Assigned to account 4 with priority HIGH", id -> id == 4 ? "alex" : "unknown");

        assertEquals("Request assigned. Technician: alex. Priority: High", description);
    }

    @Test
    @DisplayName("LIF-010 REQ-017 cancellation gives a readable reason")
    void describesCancellationReason() {
        String description = AuditDescriptions.describe("REQUEST_CANCELLED",
                "{\"reason\":\"No longer needed\"}");

        assertEquals("Request cancelled. Reason: No longer needed", description);
    }

    @Test
    @DisplayName("LIF-010 escaped reason text remains readable")
    void decodesEscapedReason() {
        String description = AuditDescriptions.describe("REQUEST_REOPENED",
                "{\"reason\":\"Repair wasn\\\"t finished\\nPlease revisit\"}");

        assertEquals("Request reopened. Reason: Repair wasn\"t finished\nPlease revisit", description);
    }

    @Test
    @DisplayName("LIF-010 work log time is shown in minutes without structured fields")
    void describesWorkLogMinutes() {
        String description = AuditDescriptions.describe("WORK_LOG_ADDED",
                "{\"workLogId\":8,\"minutesSpent\":30}");

        assertEquals("Work log added. 30 minutes recorded", description);
    }
}
