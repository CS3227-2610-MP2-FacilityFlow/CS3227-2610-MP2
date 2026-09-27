package sg.edu.nus.facilityflow.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import sg.edu.nus.facilityflow.auth.PasswordHasher;

/** Initial login/demo foundation, run only in the new-database startup transaction. */
final class DemoWorkspace {
    private DemoWorkspace() {
    }

    static void seed(Connection connection) throws SQLException {
        Instant now = Instant.now();
        String[] names = {"requester1", "requester2", "technician1", "technician2", "manager1", "manager2"};
        String[] roles = {"REQUESTER", "REQUESTER", "TECHNICIAN", "TECHNICIAN", "FACILITIES_MANAGER", "FACILITIES_MANAGER"};
        try (PreparedStatement account = connection.prepareStatement("""
                INSERT INTO user_accounts(id, username, display_name, role, password_hash, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                """)) {
            for (int i = 0; i < names.length; i++) {
                char[] password = "Welcome123".toCharArray();
                try {
                    account.setLong(1, i + 1);
                    account.setString(2, names[i]);
                    account.setString(3, names[i]);
                    account.setString(4, roles[i]);
                    account.setString(5, PasswordHasher.hash(password));
                    account.setString(6, now.toString());
                    account.setString(7, now.toString());
                    account.executeUpdate();
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        }
        try (PreparedStatement audit = connection.prepareStatement("""
                INSERT INTO audit_events(actor_id, action, detail, occurred_at, target_type, target_id)
                VALUES (5, 'ACCOUNT_CREATED', '{"source":"initial_demo"}', ?, 'ACCOUNT', ?)
                """)) {
            for (int id = 1; id <= 6; id++) {
                audit.setString(1, now.toString());
                audit.setInt(2, id);
                audit.executeUpdate();
            }
        }
        seedRequests(connection, now);
    }

    private static void seedRequests(Connection connection, Instant now) throws SQLException {
        String sql = """
                INSERT INTO maintenance_requests(
                    id, display_id, requester_id, title, description, location, category,
                    reported_urgency, manager_priority, status, assignee_id, assigned_at,
                    resolution_summary, completed_at, created_at, updated_at)
                VALUES (?, printf('FF-%06d', ?), ?, ?, ?, ?, 'Other', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement request = connection.prepareStatement(sql)) {
            insertRequest(request, 1, 1, "Lighting issue", "Ceiling light flickers throughout the day.",
                    "North lobby", "EMERGENCY", null, "OPEN", null, null, null, null,
                    now.minusSeconds(21_600), now.minusSeconds(21_600));
            insertRequest(request, 2, 1, "Door closer fault", "Main door no longer closes safely on its own.",
                    "Main entrance", "HIGH", "HIGH", "ASSIGNED", 3L, now.minusSeconds(18_000),
                    null, null, now.minusSeconds(19_800), now.minusSeconds(18_000));
            insertRequest(request, 3, 2, "Air conditioning warm", "Meeting room remains warm when cooling is enabled.",
                    "Meeting room 2", "NORMAL", "CRITICAL", "IN_PROGRESS", 4L,
                    now.minusSeconds(14_400), null, null,
                    now.minusSeconds(16_200), now.minusSeconds(12_600));
            insertRequest(request, 4, 1, "Loose handrail", "The corridor handrail moves when used.",
                    "Level 3 corridor", "HIGH", "MEDIUM", "COMPLETED", 3L,
                    now.minusSeconds(10_800), "Handrail brackets replaced and load-tested.",
                    now.minusSeconds(7_200), now.minusSeconds(12_600), now.minusSeconds(7_200));
            insertRequest(request, 5, 2, "Blocked sink", "Pantry sink drains very slowly after use.",
                    "Level 2 pantry", "NORMAL", "LOW", "CLOSED", 4L,
                    now.minusSeconds(25_200), "Trap cleared and drainage flow verified.",
                    now.minusSeconds(21_600), now.minusSeconds(28_800), now.minusSeconds(18_000));
            insertRequest(request, 6, 2, "Move cabinet", "Storage cabinet blocks access to a power outlet.",
                    "Archive room", "LOW", null, "CANCELLED", null, null, null, null,
                    now.minusSeconds(9_000), now.minusSeconds(8_100));
        }
        seedWorkLogs(connection, now);
        seedRequestAudits(connection, now);
    }

    private static void insertRequest(
            PreparedStatement statement,
            int id,
            int owner,
            String title,
            String description,
            String location,
            String urgency,
            String priority,
            String status,
            Long assignee,
            Instant assignedAt,
            String resolution,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) throws SQLException {
        statement.setInt(1, id);
        statement.setInt(2, id);
        statement.setInt(3, owner);
        statement.setString(4, title);
        statement.setString(5, description);
        statement.setString(6, location);
        statement.setString(7, urgency);
        statement.setString(8, priority);
        statement.setString(9, status);
        if (assignee == null) {
            statement.setObject(10, null);
        } else {
            statement.setLong(10, assignee);
        }
        statement.setString(11, text(assignedAt));
        statement.setString(12, resolution);
        statement.setString(13, text(completedAt));
        statement.setString(14, createdAt.toString());
        statement.setString(15, updatedAt.toString());
        statement.executeUpdate();
    }

    private static void seedWorkLogs(Connection connection, Instant now) throws SQLException {
        try (PreparedStatement log = connection.prepareStatement("""
                INSERT INTO work_logs(request_id, author_id, note, minutes_spent, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            insertWorkLog(log, 3, 4, "Inspected filters and measured supply temperature.", 25,
                    now.minusSeconds(12_600));
            insertWorkLog(log, 4, 3, "Replaced loose brackets and tested the repaired handrail.", 50,
                    now.minusSeconds(7_800));
            insertWorkLog(log, 5, 4, "Cleared the trap and confirmed normal drainage.", 35,
                    now.minusSeconds(22_200));
        }
    }

    private static void insertWorkLog(
            PreparedStatement statement,
            int requestId,
            int authorId,
            String note,
            int minutes,
            Instant at) throws SQLException {
        statement.setInt(1, requestId);
        statement.setInt(2, authorId);
        statement.setString(3, note);
        statement.setInt(4, minutes);
        statement.setString(5, at.toString());
        statement.executeUpdate();
    }

    private static void seedRequestAudits(Connection connection, Instant now) throws SQLException {
        try (PreparedStatement audit = connection.prepareStatement("""
                INSERT INTO audit_events(
                    request_id, actor_id, action, detail, occurred_at, target_type, target_id)
                VALUES (?, ?, ?, ?, ?, 'REQUEST', ?)
                """)) {
            for (int id = 1; id <= 6; id++) {
                int owner = id == 2 || id == 3 || id == 5 || id == 6 ? 2 : 1;
                insertAudit(audit, id, owner, "REQUEST_CREATED", "{\"source\":\"initial_demo\"}",
                        now.minusSeconds(32_400L - id * 600L));
            }
            insertAudit(audit, 2, 5, "REQUEST_ASSIGNED", "{\"technicianId\":3}",
                    now.minusSeconds(18_000));
            insertAudit(audit, 3, 5, "REQUEST_ASSIGNED", "{\"technicianId\":4}",
                    now.minusSeconds(14_400));
            insertAudit(audit, 3, 4, "REQUEST_STARTED", "{}", now.minusSeconds(13_500));
            insertAudit(audit, 4, 5, "REQUEST_ASSIGNED", "{\"technicianId\":3}",
                    now.minusSeconds(10_800));
            insertAudit(audit, 4, 3, "REQUEST_STARTED", "{}", now.minusSeconds(9_900));
            insertAudit(audit, 4, 3, "REQUEST_COMPLETED", "{}", now.minusSeconds(7_200));
            insertAudit(audit, 5, 5, "REQUEST_ASSIGNED", "{\"technicianId\":4}",
                    now.minusSeconds(25_200));
            insertAudit(audit, 5, 4, "REQUEST_STARTED", "{}", now.minusSeconds(24_300));
            insertAudit(audit, 5, 4, "REQUEST_COMPLETED", "{}", now.minusSeconds(21_600));
            insertAudit(audit, 5, 5, "REQUEST_CLOSED", "{}", now.minusSeconds(18_000));
            insertAudit(audit, 6, 2, "REQUEST_CANCELLED", "Demo request withdrawn.",
                    now.minusSeconds(8_100));
        }
    }

    private static void insertAudit(
            PreparedStatement statement,
            int requestId,
            int actorId,
            String action,
            String detail,
            Instant at) throws SQLException {
        statement.setInt(1, requestId);
        statement.setInt(2, actorId);
        statement.setString(3, action);
        statement.setString(4, detail);
        statement.setString(5, at.toString());
        statement.setInt(6, requestId);
        statement.executeUpdate();
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
