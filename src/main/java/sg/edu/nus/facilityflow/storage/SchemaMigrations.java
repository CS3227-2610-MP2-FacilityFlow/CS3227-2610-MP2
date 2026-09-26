package sg.edu.nus.facilityflow.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** DAT-003/004: ordered schema changes, within the caller's startup transaction. */
final class SchemaMigrations {
    private static final String[] BASELINE = {
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

    private SchemaMigrations() {
    }

    static void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int version;
            try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
                if (!result.next()) {
                    throw new SQLException("Schema version is unavailable.");
                }
                version = result.getInt(1);
            }
            if (version < 0 || version > 5) {
                throw new SQLException("Unsupported schema version; use a compatible FacilityFlow build.");
            }
            if (version == 0) {
                for (String sql : BASELINE) {
                    statement.execute(sql);
                }
                statement.execute("PRAGMA user_version = 1");
            }
            if (version < 2) {
                statement.execute("""
                        CREATE TABLE request_identity_sequence (
                            singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
                            next_value INTEGER NOT NULL CHECK (next_value > 0)
                        )
                        """);
                statement.execute("""
                        INSERT INTO request_identity_sequence(singleton, next_value)
                        SELECT 1, MAX(COALESCE(MAX(id), 0),
                            COALESCE(MAX(CAST(SUBSTR(display_id, 4) AS INTEGER)), 0)) + 1
                        FROM maintenance_requests
                        """);
                // Keep future demo inserts from colliding with generated identities.
                statement.execute("""
                        CREATE TRIGGER advance_request_identity AFTER INSERT ON maintenance_requests
                        BEGIN
                            UPDATE request_identity_sequence
                            SET next_value = MAX(next_value, NEW.id + 1,
                                CAST(SUBSTR(NEW.display_id, 4) AS INTEGER) + 1)
                            WHERE singleton = 1;
                        END
                        """);
                statement.execute("""
                        ALTER TABLE audit_events
                        ADD COLUMN target_type TEXT GENERATED ALWAYS AS ('REQUEST') VIRTUAL
                        """);
                statement.execute("""
                        ALTER TABLE audit_events
                        ADD COLUMN target_id INTEGER GENERATED ALWAYS AS (request_id) VIRTUAL
                        """);
                statement.execute("""
                        CREATE INDEX maintenance_requests_owner ON maintenance_requests(requester_id)
                        """);
                statement.execute("PRAGMA user_version = 2");
            }
            if (version < 3) {
                statement.execute("ALTER TABLE user_accounts ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0");
                statement.execute("""
                        CREATE TABLE audit_events_v3 (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            request_id INTEGER REFERENCES maintenance_requests(id),
                            actor_id INTEGER NOT NULL REFERENCES user_accounts(id),
                            action TEXT NOT NULL,
                            detail TEXT NOT NULL,
                            occurred_at TEXT NOT NULL,
                            target_type TEXT NOT NULL CHECK (target_type IN ('REQUEST', 'ACCOUNT')),
                            target_id INTEGER NOT NULL CHECK (target_id > 0),
                            CHECK ((target_type = 'REQUEST' AND request_id IS NOT NULL AND request_id = target_id)
                                OR (target_type = 'ACCOUNT' AND request_id IS NULL))
                        )
                        """);
                statement.execute("""
                        INSERT INTO audit_events_v3
                        SELECT id, request_id, actor_id, action, detail, occurred_at, target_type, target_id
                        FROM audit_events
                        """);
                statement.execute("DROP TABLE audit_events");
                statement.execute("ALTER TABLE audit_events_v3 RENAME TO audit_events");
                statement.execute("PRAGMA user_version = 3");
            }
            if (version < 4) {
                statement.execute("ALTER TABLE maintenance_requests ADD COLUMN assigned_at TEXT");
                statement.execute("ALTER TABLE maintenance_requests ADD COLUMN resolution_summary TEXT");
                statement.execute("ALTER TABLE maintenance_requests ADD COLUMN completed_at TEXT");
                statement.execute("""
                        CREATE TABLE work_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            request_id INTEGER NOT NULL REFERENCES maintenance_requests(id),
                            author_id INTEGER NOT NULL REFERENCES user_accounts(id),
                            note TEXT NOT NULL CHECK (length(note) BETWEEN 1 AND 1000),
                            minutes_spent INTEGER NOT NULL CHECK (minutes_spent BETWEEN 1 AND 1440),
                            created_at TEXT NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE INDEX work_logs_request_history
                        ON work_logs(request_id, created_at, id)
                        """);
                statement.execute("""
                        CREATE INDEX work_logs_author_history
                        ON work_logs(author_id, created_at, id)
                        """);
                statement.execute("""
                        CREATE INDEX maintenance_requests_technician_queue
                        ON maintenance_requests(
                            assignee_id, status, manager_priority, reported_urgency, assigned_at, display_id)
                        """);
                statement.execute("PRAGMA user_version = 4");
            }
            if (version < 5) {
                statement.execute("CREATE TABLE requester_updates (id INTEGER PRIMARY KEY AUTOINCREMENT, request_id INTEGER NOT NULL REFERENCES maintenance_requests(id), author_id INTEGER NOT NULL REFERENCES user_accounts(id), text TEXT NOT NULL CHECK(length(text) BETWEEN 1 AND 1000), created_at TEXT NOT NULL)");
                statement.execute("CREATE INDEX requester_updates_history ON requester_updates(request_id, created_at, id)");
                statement.execute("PRAGMA user_version = 5");
            }
            // Fail at startup rather than accepting a version marker on an incompatible schema.
            statement.executeQuery("""
                    SELECT id, username, display_name, role, password_hash, active, created_at, updated_at, session_version
                    FROM user_accounts LIMIT 0
                    """).close();
            statement.executeQuery("""
                    SELECT id, display_id, requester_id, title, description, location, category,
                        reported_urgency, manager_priority, status, assignee_id, assigned_at,
                        resolution_summary, completed_at, created_at, updated_at
                    FROM maintenance_requests LIMIT 0
                    """).close();
            statement.executeQuery("""
                    SELECT id, request_id, author_id, note, minutes_spent, created_at
                    FROM work_logs LIMIT 0
                    """).close();
            statement.executeQuery("""
                    SELECT id, request_id, author_id, text, created_at
                    FROM requester_updates LIMIT 0
                    """).close();
            statement.executeQuery("""
                    SELECT id, request_id, actor_id, action, detail, occurred_at, target_type, target_id
                    FROM audit_events LIMIT 0
                    """).close();
            try (ResultSet sequence = statement.executeQuery(
                    "SELECT next_value FROM request_identity_sequence WHERE singleton = 1")) {
                if (!sequence.next() || sequence.getLong(1) < 1) {
                    throw new SQLException("Request identity sequence is missing or invalid.");
                }
            }
        }
    }
}
