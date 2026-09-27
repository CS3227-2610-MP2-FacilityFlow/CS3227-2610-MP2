package sg.edu.nus.facilityflow.ui;

import java.util.function.LongFunction;

/** Short descriptions for structured audit details shown to people. */
public final class AuditDescriptions {
    private AuditDescriptions() { }

    public static String action(String action) {
        return switch (action) {
            case "REQUEST_CREATED" -> "Request created";
            case "REQUEST_RECORDED_ON_BEHALF" -> "Request recorded for requester";
            case "REQUEST_EDITED" -> "Request details updated";
            case "REQUEST_CORRECTED" -> "Request details corrected";
            case "REQUEST_ASSIGNED" -> "Request assigned";
            case "REQUEST_REASSIGNED" -> "Request reassigned";
            case "REQUEST_STARTED" -> "Work started";
            case "REQUEST_COMPLETED" -> "Work completed";
            case "REQUEST_CLOSED" -> "Request closed";
            case "REQUEST_RETURNED" -> "Work returned for rework";
            case "REQUEST_REOPENED" -> "Request reopened";
            case "REQUEST_CANCELLED" -> "Request cancelled";
            case "WORK_LOG_ADDED" -> "Work log added";
            case "ACCOUNT_CREATED" -> "Account created";
            case "ACCOUNT_DEACTIVATED" -> "Account deactivated";
            case "ACCOUNT_REACTIVATED" -> "Account reactivated";
            case "ACCOUNT_ROLE_CHANGED" -> "Account role changed";
            default -> readable(action);
        };
    }

    public static String detail(String action, String storedDetail) {
        return detail(action, storedDetail, id -> "Technician");
    }

    public static String detail(String action, String storedDetail,
                                LongFunction<String> accountName) {
        if (storedDetail == null || storedDetail.isBlank() || storedDetail.equals("{}")) {
            return "";
        }
        if (storedDetail.stripLeading().startsWith("{")) {
            String reason = jsonString(storedDetail, "reason");
            if (reason != null && !reason.isBlank()) {
                return "Reason: " + reason;
            }
            if (action.equals("WORK_LOG_ADDED")) {
                String minutes = jsonNumber(storedDetail, "minutesSpent");
                return minutes == null ? "" : minutes + " minutes recorded";
            }
            if (action.equals("REQUEST_ASSIGNED")) {
                String id = jsonNumber(storedDetail, "technicianId");
                return id == null ? "" : "Technician: " + accountName.apply(Long.parseLong(id));
            }
            return "";
        }
        if (action.equals("REQUEST_ASSIGNED")
                && storedDetail.startsWith("Assigned to account ")) {
            String rest = storedDetail.substring("Assigned to account ".length());
            int priority = rest.indexOf(" with priority ");
            if (priority > 0) {
                try {
                    long id = Long.parseLong(rest.substring(0, priority));
                    String level = readable(rest.substring(priority + " with priority ".length()));
                    return "Technician: " + accountName.apply(id) + ". Priority: " + level;
                } catch (NumberFormatException ignored) {
                    return "Technician assigned";
                }
            }
            return "Technician assigned";
        }
        return switch (action) {
            case "REQUEST_CANCELLED", "REQUEST_REOPENED", "REQUEST_RETURNED" ->
                    "Reason: " + storedDetail;
            default -> storedDetail;
        };
    }

    public static String describe(String action, String storedDetail) {
        return describe(action, storedDetail, id -> "Technician");
    }

    public static String describe(String action, String storedDetail,
                                  LongFunction<String> accountName) {
        String extra = detail(action, storedDetail, accountName);
        return action(action) + (extra.isEmpty() ? "." : ". " + extra);
    }

    private static String readable(String code) {
        String words = code.replace('_', ' ').toLowerCase();
        return words.isEmpty() ? "Activity recorded"
                : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String jsonNumber(String json, String key) {
        int start = json.indexOf('"' + key + '"');
        if (start < 0) {
            return null;
        }
        start = json.indexOf(':', start + key.length() + 2);
        if (start < 0) {
            return null;
        }
        int first = start + 1;
        while (first < json.length() && Character.isWhitespace(json.charAt(first))) {
            first++;
        }
        int end = first;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return end == first ? null : json.substring(first, end);
    }

    private static String jsonString(String json, String key) {
        int start = json.indexOf('"' + key + '"');
        if (start < 0) {
            return null;
        }
        start = json.indexOf(':', start + key.length() + 2);
        if (start < 0) {
            return null;
        }
        start++;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start == json.length() || json.charAt(start++) != '"') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        for (int index = start; index < json.length(); index++) {
            char character = json.charAt(index);
            if (character == '"') {
                return value.toString();
            }
            if (character != '\\' || ++index == json.length()) {
                value.append(character);
                continue;
            }
            character = json.charAt(index);
            switch (character) {
                case '"', '\\', '/' -> value.append(character);
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'u' -> {
                    if (index + 4 >= json.length()) {
                        return null;
                    }
                    try {
                        value.append((char) Integer.parseInt(json.substring(index + 1, index + 5), 16));
                    } catch (NumberFormatException error) {
                        return null;
                    }
                    index += 4;
                }
                default -> { return null; }
            }
        }
        return null;
    }
}
