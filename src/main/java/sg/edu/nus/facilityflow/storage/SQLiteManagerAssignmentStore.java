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
import sg.edu.nus.facilityflow.model.AccountCredentials;
import sg.edu.nus.facilityflow.model.MaintenanceRequest;
import sg.edu.nus.facilityflow.model.ManagerPriority;
import sg.edu.nus.facilityflow.model.ReportedUrgency;
import sg.edu.nus.facilityflow.model.RequestDraft;
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
        initialize(null);
    }

    public void initializeWorkspace(List<String> categories) {
        initialize(List.copyOf(categories));
    }

    private void initialize(List<String> categories) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean fresh;
                try (Statement statement = connection.createStatement();
                        ResultSet tables = statement.executeQuery(
                                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
                    fresh = tables.next() && tables.getInt(1) == 0;
                }
                SchemaMigrations.apply(connection);
                if (categories != null) {
                    if (fresh) {
                        DemoWorkspace.seed(connection);
                    }
                    try (Statement statement = connection.createStatement();
                            ResultSet rows = statement.executeQuery("SELECT DISTINCT category FROM maintenance_requests")) {
                        while (rows.next()) {
                            if (!categories.contains(rows.getString(1))) {
                                throw new SQLException("Stored categories require an explicit category migration.");
                            }
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new StorageException("Could not initialize the local database. "
                    + "Use a compatible app version or restore a valid backup.", exception);
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
                    SELECT id, username, display_name, role, active, created_at, updated_at, session_version
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
        public Optional<AccountCredentials> findCredentials(String username) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM user_accounts WHERE username = ? COLLATE NOCASE")) {
                statement.setString(1, username);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(new AccountCredentials(
                            readAccount(result), result.getString("password_hash"))) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public void updatePassword(long accountId, String hash, boolean invalidateSessions, Instant changedAt) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE user_accounts SET password_hash = ?, updated_at = ?,
                        session_version = session_version + ? WHERE id = ?
                    """)) {
                statement.setString(1, hash);
                statement.setString(2, changedAt.toString());
                statement.setInt(3, invalidateSessions ? 1 : 0);
                statement.setLong(4, accountId);
                if (statement.executeUpdate() != 1) {
                    throw new StorageException("The account could not be updated.", null);
                }
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public void appendAccountAudit(long actorId, long targetId, String action, Instant occurredAt) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO audit_events(actor_id, action, detail, occurred_at, target_type, target_id)
                    VALUES (?, ?, '{}', ?, 'ACCOUNT', ?)
                    """)) {
                statement.setLong(1, actorId);
                statement.setString(2, action);
                statement.setString(3, occurredAt.toString());
                statement.setLong(4, targetId);
                statement.executeUpdate();
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
        public MaintenanceRequest createOpenRequest(long requesterId, RequestDraft draft, Instant createdAt) {
            long id;
            try (PreparedStatement allocation = connection.prepareStatement("""
                    UPDATE request_identity_sequence SET next_value = next_value + 1
                    WHERE singleton = 1 AND next_value BETWEEN 1 AND 999999
                    RETURNING next_value - 1
                    """);
                    ResultSet result = allocation.executeQuery()) {
                if (!result.next()) {
                    throw new StorageException("No request IDs are available. Contact the Facilities Manager.", null);
                }
                id = result.getLong(1);
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
            String sql = """
                    INSERT INTO maintenance_requests(id, display_id, requester_id, title, description,
                        location, category, reported_urgency, manager_priority, status, assignee_id,
                        created_at, updated_at)
                    VALUES (?, printf('FF-%06d', ?), ?, ?, ?, ?, ?, ?, NULL, 'OPEN', NULL, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.setLong(2, id);
                statement.setLong(3, requesterId);
                statement.setString(4, draft.title());
                statement.setString(5, draft.description());
                statement.setString(6, draft.location());
                statement.setString(7, draft.category());
                statement.setString(8, draft.urgency().name());
                statement.setString(9, createdAt.toString());
                statement.setString(10, createdAt.toString());
                statement.executeUpdate();
                return findOwnRequest(requesterId, id).orElseThrow(
                        () -> new StorageException("The created request could not be read back.", null));
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public List<MaintenanceRequest> listOwnRequests(long requesterId) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM maintenance_requests WHERE requester_id = ?")) {
                statement.setLong(1, requesterId);
                List<MaintenanceRequest> requests = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        requests.add(readRequest(result));
                    }
                }
                return requests;
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public Optional<MaintenanceRequest> findOwnRequest(long requesterId, long requestId) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM maintenance_requests WHERE requester_id = ? AND id = ?")) {
                statement.setLong(1, requesterId);
                statement.setLong(2, requestId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readRequest(result)) : Optional.empty();
                }
            } catch (SQLException exception) {
                throw storageFailure(exception);
            }
        }

        @Override
        public List<UserAccount> listActiveTechnicians() {
            String sql = """
                    SELECT id, username, display_name, role, active, created_at, updated_at, session_version
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
                    INSERT INTO audit_events(request_id, actor_id, action, detail, occurred_at, target_type, target_id)
                    VALUES (?, ?, ?, ?, ?, 'REQUEST', ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, event.requestId());
                statement.setLong(2, event.actorId());
                statement.setString(3, event.action());
                statement.setString(4, event.detail());
                statement.setString(5, event.occurredAt().toString());
                statement.setLong(6, event.requestId());
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
                    Instant.parse(result.getString("updated_at")), result.getLong("session_version"));
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
