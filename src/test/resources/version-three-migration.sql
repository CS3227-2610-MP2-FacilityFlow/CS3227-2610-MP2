ALTER TABLE user_accounts ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0
-- migration-statement
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
-- migration-statement
INSERT INTO audit_events_v3
SELECT id, request_id, actor_id, action, detail, occurred_at, target_type, target_id
FROM audit_events
-- migration-statement
DROP TABLE audit_events
-- migration-statement
ALTER TABLE audit_events_v3 RENAME TO audit_events
-- migration-statement
PRAGMA user_version = 3
