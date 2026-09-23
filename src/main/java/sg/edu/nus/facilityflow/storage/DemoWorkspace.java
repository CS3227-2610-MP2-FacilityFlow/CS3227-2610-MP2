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
        String now = Instant.now().toString();
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
                    account.setString(6, now);
                    account.setString(7, now);
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
                audit.setString(1, now);
                audit.setInt(2, id);
                audit.executeUpdate();
            }
        }
        // Remaining lifecycle demo fixtures belong to the complete workflow/seed increment (AUT-008).
    }
}
