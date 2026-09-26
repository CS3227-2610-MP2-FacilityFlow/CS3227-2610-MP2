package sg.edu.nus.facilityflow.auth;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import sg.edu.nus.facilityflow.service.ManagerRequestService;
import sg.edu.nus.facilityflow.service.ManagerAccountService;
import sg.edu.nus.facilityflow.service.RequesterRequestService;
import sg.edu.nus.facilityflow.service.RequestValidator;
import sg.edu.nus.facilityflow.service.TechnicianRequestService;
import sg.edu.nus.facilityflow.storage.SQLiteManagerAssignmentStore;

/** Real hashed credentials in isolated databases for authentication and UI integration tests. */
public final class AuthFixture {
    private static final String HASH = PasswordHasher.hash("password".toCharArray());
    public final String url;
    public final SQLiteManagerAssignmentStore store;
    public final SessionManager sessions = new SessionManager();
    public final AuthenticationService auth;
    public final RequesterRequestService requester;
    public final ManagerRequestService manager;
    public final ManagerAccountService managerAccounts;
    public final TechnicianRequestService technician;

    public AuthFixture(Path directory) throws SQLException {
        url = "jdbc:sqlite:" + directory.resolve("auth.db");
        store = new SQLiteManagerAssignmentStore(url);
        store.initializeSchema();
        String[] names = {"owner", "other", "manager", "tech", "inactive"};
        String[] roles = {"REQUESTER", "REQUESTER", "FACILITIES_MANAGER", "TECHNICIAN", "REQUESTER"};
        try (var connection = DriverManager.getConnection(url);
                var statement = connection.prepareStatement("""
                        INSERT INTO user_accounts(id, username, display_name, role, password_hash,
                            active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            for (int i = 0; i < names.length; i++) {
                statement.setInt(1, i + 1);
                statement.setString(2, names[i]);
                statement.setString(3, names[i]);
                statement.setString(4, roles[i]);
                statement.setString(5, HASH);
                statement.setInt(6, i == 4 ? 0 : 1);
                statement.setString(7, "2026-09-22T00:00:00Z");
                statement.setString(8, "2026-09-22T00:00:00Z");
                statement.executeUpdate();
            }
        }
        var clock = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
        auth = new AuthenticationService(store, sessions, clock);
        requester = new RequesterRequestService(store, new RequestValidator(Set.of("Plumbing")), clock, sessions);
        manager = new ManagerRequestService(
                store, clock, sessions, new RequestValidator(Set.of("Plumbing")));
        managerAccounts = new ManagerAccountService(store, clock, sessions);
        technician = new TechnicianRequestService(store, clock, sessions);
    }

    public void execute(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public long scalar(String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new SQLException("Expected a scalar row");
            }
            return result.getLong(1);
        }
    }
}
