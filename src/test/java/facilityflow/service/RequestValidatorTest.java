package facilityflow.service;

import facilityflow.model.ReportedUrgency;
import facilityflow.model.RequestDraft;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidatorTest {
    private final RequestValidator validator = new RequestValidator(Set.of("Electrical", "Other"));

    @ParameterizedTest
    @CsvSource({"5,10,2", "100,2000,120"})
    @DisplayName("LIF-005 counts supplementary Unicode as single code points at valid boundaries")
    void acceptsUnicodeBoundaries(int titleLength, int descriptionLength, int locationLength) {
        var draft = new RequestDraft(" 😀" + "😀".repeat(titleLength - 1) + " ",
                "😀".repeat(descriptionLength), "😀".repeat(locationLength),
                "Other", ReportedUrgency.NORMAL);
        assertTrue(validator.validate(draft).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"3,10,2,title", "4,10,2,title", "101,10,2,title",
        "5,9,2,description", "5,2001,2,description", "5,10,1,location", "5,10,121,location"})
    @DisplayName("LIF-005 rejects Unicode inputs outside code-point boundaries")
    void rejectsUnicodeBoundaries(int titleLength, int descriptionLength, int locationLength, String field) {
        var draft = new RequestDraft("😀".repeat(titleLength), "😀".repeat(descriptionLength),
                "😀".repeat(locationLength), "Other", ReportedUrgency.NORMAL);
        assertEquals(Set.of(field), validator.validate(draft).keySet());
    }

    @Test
    @DisplayName("LIF-005 counts combining marks separately without normalizing the input")
    void combiningMarksAreCodePoints() {
        String title = "e\u0301e\u0301x";
        var draft = new RequestDraft(title, "The light flickers.", "Room 12",
                "Other", ReportedUrgency.NORMAL);
        assertTrue(validator.validate(draft).isEmpty());
        assertEquals(title, draft.title());
    }

    @Test
    @DisplayName("LIF-004 trims boundaries while preserving internal spaces and line breaks")
    void trimsInput() {
        var draft = new RequestDraft("  Broken  light  ", "\nThe light\nflickers.\n",
                "  Room  12  ", " Electrical ", ReportedUrgency.NORMAL);
        assertEquals("Broken  light", draft.title());
        assertEquals("The light\nflickers.", draft.description());
        assertEquals("Room  12", draft.location());
        assertEquals("Electrical", draft.category());
        assertTrue(validator.validate(draft).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"5,10,2", "100,2000,120"})
    @DisplayName("LIF-005 accepts inclusive field-length boundaries")
    void acceptsBoundaries(int titleLength, int descriptionLength, int locationLength) {
        var draft = new RequestDraft("t".repeat(titleLength), "d".repeat(descriptionLength),
                "l".repeat(locationLength), "Other", ReportedUrgency.LOW);
        assertTrue(validator.validate(draft).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"4,10,2,title", "101,10,2,title", "5,9,2,description",
        "5,2001,2,description", "5,10,1,location", "5,10,121,location"})
    @DisplayName("LIF-005 rejects out-of-range input with the correct field error")
    void rejectsLengths(int titleLength, int descriptionLength, int locationLength, String field) {
        var draft = new RequestDraft("t".repeat(titleLength), "d".repeat(descriptionLength),
                "l".repeat(locationLength), "Other", ReportedUrgency.HIGH);
        assertEquals(Set.of(field), validator.validate(draft).keySet());
    }

    @Test
    @DisplayName("REQ-012 and LIF-005 report all missing fields together")
    void rejectsMissingFields() {
        var draft = new RequestDraft(null, " \n ", null, null, null);
        assertEquals(Set.of("title", "description", "location", "category", "urgency"),
                validator.validate(draft).keySet());
    }

    @Test
    @DisplayName("LIF-005 validates against the supplied catalogue, not a hardcoded list")
    void usesSuppliedCatalogue() {
        var draft = new RequestDraft("Broken light", "The light flickers.", "Room 12",
                "Custom category", ReportedUrgency.EMERGENCY);
        assertTrue(validator.validate(draft).containsKey("category"));
        var custom = new RequestValidator(Set.of("Custom category"));
        assertFalse(custom.validate(draft).containsKey("category"));
    }

    @Test
    @DisplayName("LIF-005 does not count surrounding whitespace toward minimum length")
    void validatesAfterTrimming() {
        var draft = new RequestDraft("    four    ", "The light flickers.", "Room 12",
                "Other", ReportedUrgency.NORMAL);
        assertEquals(Set.of("title"), validator.validate(draft).keySet());
    }
}
