package sg.edu.nus.facilityflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RequesterMutationTest {
    private static final Instant CREATED = Instant.parse("2026-09-24T01:00:00Z");
    private static final Instant CHANGED = CREATED.plusSeconds(60);

    @Test
    @DisplayName("REQ-007/008 LIF-013 immutable edit and cancellation preserve system-owned fields")
    void editsThenCancelsOpenRequest() {
        var original = request(RequestStatus.OPEN);
        var draft = new RequestDraft("Changed title", "Changed description", "Room 2", "Electrical", ReportedUrgency.HIGH);
        var edited = original.editDetails(draft, CHANGED);
        assertEquals(new MaintenanceRequest(1, "FF-000001", 2, draft.title(), draft.description(), draft.location(),
                draft.category(), draft.urgency(), null, RequestStatus.OPEN, null, CREATED, CHANGED), edited);
        var cancelled = edited.cancel(CHANGED.plusSeconds(1));
        assertEquals(new MaintenanceRequest(1, "FF-000001", 2, draft.title(), draft.description(), draft.location(),
                draft.category(), draft.urgency(), null, RequestStatus.CANCELLED, null, CREATED, CHANGED.plusSeconds(1)), cancelled);
        assertEquals("Original title", original.title());
        assertEquals(RequestStatus.OPEN, original.status());
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("REQ-007/008 LIF-015 model rejects editing and cancelling every non-OPEN state")
    void rejectsNonOpenMutations(RequestStatus status) {
        var request = request(status);
        var draft = new RequestDraft("Changed title", "Changed description", "Room 2", "Electrical", ReportedUrgency.HIGH);
        assertThrows(IllegalStateException.class, () -> request.editDetails(draft, CHANGED));
        assertThrows(IllegalStateException.class, () -> request.cancel(CHANGED));
        assertEquals(status, request.status());
    }

    private MaintenanceRequest request(RequestStatus status) {
        return new MaintenanceRequest(1, "FF-000001", 2, "Original title", "Original description", "Room 1",
                "Plumbing", ReportedUrgency.NORMAL, null, status, null, CREATED, CREATED);
    }
}
