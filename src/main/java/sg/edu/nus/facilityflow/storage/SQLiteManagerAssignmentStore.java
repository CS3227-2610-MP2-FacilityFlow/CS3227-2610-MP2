package sg.edu.nus.facilityflow.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import sg.edu.nus.facilityflow.model.AuditEvent;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestStatus;
import sg.edu.nus.facilityflow.model.Role;
import sg.edu.nus.facilityflow.model.UserAccount;

/** SQLite implementation whose callback commits all writes or rolls all of them back. */
public final class SQLiteManagerAssignmentStore implements ManagerAssignmentStore {
    private final String jdbcUrl;

    public SQLiteManagerAssignmentStore(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
            throw new IllegalArgumentException("A SQLite JDBC URL is required");
        }
        this.jdbcUrl = jdbcUrl;
    }

    public void initializeSchema() {
        String[] statements = {
            """
            CREATE TABLE IF NOT EXISTS user_accounts (
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                display_name TEXT NOT NULL,
                role TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                active INTEGER NOT NULL CHECK (active IN (0, 1)),
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS maintenance_requests (
                id INTEGER PRIMARY KEY,
                display_id TEXT NOT NULL UNIQUE,
                requester_id INTEGER NOT NULL REFERENCES user_accounts(id),
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                location TEXT NOT NULL,
                category TEXT NOT NULL,
                reported_urgency TEXT NOT NULL,
                manager_priority TEXT,
                status TEXT NOT NULL,
                assignee_id INTEGER REFERENCES user_accounts(id),
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                request_id INTEGER NOT NULL REFERENCES maintenance_requests(id),
                actor_id INTEGER NOT NULL REFERENCES user_accounts(id),
                action TEXT NOT NULL,
                detail TEXT NOT NULL,
                occurred_at TEXT NOT NULL
            )
            """
        };

        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                for (String sql : statements) {
                    statement.execute(sql);
                }
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new StorageException("Could not initialize the local database", exception);
        }
    }

    @Override
    public <T> T inTransaction(TransactionWork<T> work) {
        if (work == null) {
            throw new IllegalArgumentException("Transaction work is required");
        }
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(new SQLiteTransactionContext(connection));
                connection.commit();
                return result;
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new StorageException("The local database operation failed", exception);
            }
        } catch (SQLException exception) {
            throw new StorageException("Could not open the local database", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static final class SQLiteTransactionContext implements TransactionContext {
        private final Connection connection;

        private SQLiteTransactionContext(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Optional<UserAccount> findAccount(long accountId) {
            String sql = """
                    SELECT id, username, display_name, role, active, created_at, updated_at
                    FROM user_accounts WHERE id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, accountId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readAccount(result)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public Optional<MaintenanceRequest> findRequest(long requestId) {
            String sql = "SELECT * FROM maintenance_requests WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, requestId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readRequest(result)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public List<MaintenanceRequest> listRequests() {
            List<MaintenanceRequest> requests = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT * FROM maintenance_requests");
                    ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    requests.add(readRequest(result));
                }
                return requests;
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public List<UserAccount> listActiveTechnicians() {
            String sql = """
                    SELECT id, username, display_name, role, active, created_at, updated_at
                    FROM user_accounts
                    WHERE active = 1 AND role = 'TECHNICIAN'
                    ORDER BY display_name COLLATE NOCASE, id
                    """;
            List<UserAccount> technicians = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                    ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    technicians.add(readAccount(result));
                }
                return technicians;
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public void updateRequest(MaintenanceRequest request) {
            String sql = """
                    UPDATE maintenance_requests
                    SET manager_priority = ?, status = ?, assignee_id = ?, updated_at = ?
                    WHERE id = ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.managerPriority().name());
                statement.setString(2, request.status().name());
                statement.setLong(3, request.assigneeId());
                statement.setString(4, request.updatedAt().toString());
                statement.setLong(5, request.id());
                if (statement.executeUpdate() != 1) {
                    throw new StorageException("The request could not be updated", null);
                }
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public void appendAuditEvent(AuditEvent event) {
            String sql = """
                    INSERT INTO audit_events(request_id, actor_id, action, detail, occurred_at)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, event.requestId());
                statement.setLong(2, event.actorId());
                statement.setString(3, event.action());
                statement.setString(4, event.detail());
                statement.setString(5, event.occurredAt().toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        private static UserAccount readAccount(ResultSet result) throws SQLException {
            return new UserAccount(
                    result.getLong("id"),
                    result.getString("username"),
                    result.getString("display_name"),
                    Role.valueOf(result.getString("role")),
                    result.getInt("active") == 1,
                    Instant.parse(result.getString("created_at")),
                    Instant.parse(result.getString("updated_at")));
        }

        private static MaintenanceRequest readRequest(ResultSet result) throws SQLException {
            String priority = result.getString("manager_priority");
            long assignee = result.getLong("assignee_id");
            Long nullableAssignee = result.wasNull() ? null : assignee;
            return new MaintenanceRequest(
                    result.getLong("id"),
                    result.getString("display_id"),
                    result.getLong("requester_id"),
                    result.getString("title"),
                    result.getString("description"),
                    result.getString("location"),
                    result.getString("category"),
                    ReportedUrgency.valueOf(result.getString("reported_urgency")),
                    priority == null ? null : ManagerPriority.valueOf(priority),
                    RequestStatus.valueOf(result.getString("status")),
                    nullableAssignee,
                    Instant.parse(result.getString("created_at")),
                    Instant.parse(result.getString("updated_at")));
        }

        private static StorageException storageFailure(SQLException cause) {
            return new StorageException("The local database operation failed", cause);
        }
    }
}
