-- Drop created_by columns on goal and step.
-- AAP has no single author for these at the aggregate level they're edited in place by multiple practitioners
ALTER TABLE goal DROP COLUMN created_by_user_id;
ALTER TABLE step DROP COLUMN created_by_user_id;

-- Convert free_text and plan_agreement creator columns from TEXT to UUID
DELETE FROM free_text;
DELETE FROM plan_agreement;
ALTER TABLE free_text      ALTER COLUMN created_by_user_id TYPE UUID USING created_by_user_id::UUID;
ALTER TABLE plan_agreement ALTER COLUMN created_by_user_id TYPE UUID USING created_by_user_id::UUID;

-- Mirror of coordinator's deleted flag.
ALTER TABLE sentence_plan ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Redact goal titles: store length and sha256 hash instead of raw text.
ALTER TABLE goal DROP COLUMN title;
ALTER TABLE goal ADD COLUMN title_length INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE goal ADD COLUMN title_hash   VARCHAR(64) NOT NULL DEFAULT '';

-- Sync watermark
CREATE TABLE sync_state (
    id                   TEXT PRIMARY KEY,
    last_sync_started_at TIMESTAMPTZ
);
INSERT INTO sync_state (id, last_sync_started_at) VALUES ('sentence_plan', NULL);

-- Storing as TEXT removes the need for view-api to parse incoming String values
-- from coordinator-api into Int and lets values be tolerated instead of silently nulling.
ALTER TABLE sentence_plan ALTER COLUMN oasys_pk TYPE TEXT USING oasys_pk::TEXT;
