package sg.edu.nus.facilityflow.service;

import sg.edu.nus.facilityflow.model.RequestDraft;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure field validation for REQ-002 and LIF-004–005; does not authorize or save. */
public final class RequestValidator {
    private final Set<String> categories;

    public RequestValidator(Set<String> categories) {
        this.categories = Set.copyOf(categories);
    }

    /** UIX-007: report every invalid field so callers can retain the form input. */
    public Map<String, String> validate(RequestDraft draft) {
        Objects.requireNonNull(draft, "draft");
        Map<String, String> errors = new LinkedHashMap<>();
        checkLength(errors, "title", "Title", draft.title(), 5, 100);
        checkLength(errors, "description", "Description", draft.description(), 10, 2000);
        checkLength(errors, "location", "Location", draft.location(), 2, 120);
        if (!categories.contains(draft.category())) {
            errors.put("category", "Choose a category from the catalogue.");
        }
        if (draft.urgency() == null) {
            errors.put("urgency", "Choose a reported urgency.");
        }
        return Collections.unmodifiableMap(errors);
    }

    private static void checkLength(Map<String, String> errors, String field,
                                    String label, String value, int min, int max) {
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) {
            errors.put(field, label + " must contain " + min + "–" + max
                    + " characters after trimming.");
        }
    }
}
