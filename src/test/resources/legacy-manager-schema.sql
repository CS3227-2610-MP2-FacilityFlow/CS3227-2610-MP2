CREATE TABLE IF NOT EXISTS user_accounts (
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                display_name TEXT NOT NULL,
                role TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                active INTEGER NOT NULL CHECK (active IN (0, 1)),
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

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
            );

CREATE TABLE IF NOT EXISTS audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                request_id INTEGER NOT NULL REFERENCES maintenance_requests(id),
                actor_id INTEGER NOT NULL REFERENCES user_accounts(id),
                action TEXT NOT NULL,
                detail TEXT NOT NULL,
                occurred_at TEXT NOT NULL
            );
