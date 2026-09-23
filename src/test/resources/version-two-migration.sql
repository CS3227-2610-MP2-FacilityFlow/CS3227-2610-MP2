CREATE TABLE request_identity_sequence (
                            singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
                            next_value INTEGER NOT NULL CHECK (next_value > 0)
                        )
-- migration-statement
INSERT INTO request_identity_sequence(singleton, next_value)
                        SELECT 1, MAX(COALESCE(MAX(id), 0),
                            COALESCE(MAX(CAST(SUBSTR(display_id, 4) AS INTEGER)), 0)) + 1
                        FROM maintenance_requests
-- migration-statement
CREATE TRIGGER advance_request_identity AFTER INSERT ON maintenance_requests
                        BEGIN
                            UPDATE request_identity_sequence
                            SET next_value = MAX(next_value, NEW.id + 1,
                                CAST(SUBSTR(NEW.display_id, 4) AS INTEGER) + 1)
                            WHERE singleton = 1;
                        END
-- migration-statement
ALTER TABLE audit_events
                        ADD COLUMN target_type TEXT GENERATED ALWAYS AS ('REQUEST') VIRTUAL
-- migration-statement
ALTER TABLE audit_events
                        ADD COLUMN target_id INTEGER GENERATED ALWAYS AS (request_id) VIRTUAL
-- migration-statement
CREATE INDEX maintenance_requests_owner ON maintenance_requests(requester_id)
-- migration-statement
PRAGMA user_version = 2
