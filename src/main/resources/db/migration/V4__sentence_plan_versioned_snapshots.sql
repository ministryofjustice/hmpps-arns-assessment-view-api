-- Multi-snapshot model for sentence_plan.
--
-- `id` keeps its meaning as the logical sentence plan identifier
-- `snapshot_id` becomes the per-row PK with a fresh UUID. Child
-- tables re-FK to `snapshot_id`
--
-- Two row kinds going forward, distinguished by `version`:
--   * version  > 0  -- immutable snapshots written by the SQS pipeline.
--                      `oasys_event` carries the OasysEvent that produced the snapshot.
--   * version = -1  -- the current state row written by the periodic sync
--                      job. No specific lifecycle event triggers it.
--
-- Legacy rows are collapsed to `version = -1` because
-- they come from the polling sync

-- 1. Per-snapshot PK column, fresh UUID per row.
ALTER TABLE sentence_plan ADD COLUMN snapshot_id UUID;
UPDATE sentence_plan SET snapshot_id = gen_random_uuid();
ALTER TABLE sentence_plan ALTER COLUMN snapshot_id SET NOT NULL;

-- 2. OasysEvent that produced the snapshot. Nullable for version = -1
ALTER TABLE sentence_plan ADD COLUMN oasys_event TEXT;

-- 3. Widen `version` from INTEGER to BIGINT to hold coordinator's epoch values
ALTER TABLE sentence_plan ALTER COLUMN version TYPE BIGINT;

-- 4. Collapse legacy rows to the current state magic number
UPDATE sentence_plan SET version = -1;

-- ----------------------------------------------------------------------------------
-- Re-point child FKs from sentence_plan.id to sentence_plan.snapshot_id.
-- ----------------------------------------------------------------------------------

ALTER TABLE sentence_plan_identifier DROP CONSTRAINT sentence_plan_identifier_sentence_plan_id_fkey;
ALTER TABLE plan_agreement          DROP CONSTRAINT plan_agreement_sentence_plan_id_fkey;
ALTER TABLE goal                    DROP CONSTRAINT goal_sentence_plan_id_fkey;

UPDATE sentence_plan_identifier c SET sentence_plan_id = sp.snapshot_id FROM sentence_plan sp WHERE c.sentence_plan_id = sp.id;
UPDATE plan_agreement          c SET sentence_plan_id = sp.snapshot_id FROM sentence_plan sp WHERE c.sentence_plan_id = sp.id;
UPDATE goal                    c SET sentence_plan_id = sp.snapshot_id FROM sentence_plan sp WHERE c.sentence_plan_id = sp.id;

-- 5. Swap the PK from `id` to `snapshot_id` (safe now no child FK references `id`).
ALTER TABLE sentence_plan DROP CONSTRAINT sentence_plan_pkey;
ALTER TABLE sentence_plan ADD CONSTRAINT sentence_plan_pkey PRIMARY KEY (snapshot_id);

-- 6. Re-add child FKs against the new PK.
ALTER TABLE sentence_plan_identifier
  ADD CONSTRAINT sentence_plan_identifier_sentence_plan_id_fkey
    FOREIGN KEY (sentence_plan_id) REFERENCES sentence_plan(snapshot_id) ON DELETE CASCADE;
ALTER TABLE plan_agreement
  ADD CONSTRAINT plan_agreement_sentence_plan_id_fkey
    FOREIGN KEY (sentence_plan_id) REFERENCES sentence_plan(snapshot_id) ON DELETE CASCADE;
ALTER TABLE goal
  ADD CONSTRAINT goal_sentence_plan_id_fkey
    FOREIGN KEY (sentence_plan_id) REFERENCES sentence_plan(snapshot_id) ON DELETE CASCADE;

-- 7. Logical uniqueness: one snapshot per (plan, version).
ALTER TABLE sentence_plan ADD CONSTRAINT sentence_plan_id_version_key UNIQUE (id, version);

-- 8. index for lookups by logical id
CREATE INDEX idx_sentence_plan_id ON sentence_plan(id);

-- 9. Reset legacy `updated_at` so the upcoming LWW comparison in the SQS event handler
--    lets the first real event-driven snapshot win.
UPDATE sentence_plan SET updated_at = '1970-01-01 00:00:00';
