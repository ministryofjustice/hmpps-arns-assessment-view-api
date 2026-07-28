-- V6 created the reporting views against the pre-V4 sentence_plan schema, so the
-- multi-snapshot columns added in V4 (snapshot_id, oasys_event) are not surfaced
-- CREATE OR REPLACE VIEW to expose them |(permits appending columns)

CREATE OR REPLACE VIEW "assessment-view".sentence_plan_vw AS
SELECT id, created_at, updated_at, last_synced_at, oasys_pk::varchar(100) AS oasys_pk, version,
       region_code::varchar(100) AS region_code, deleted::varchar(100) AS deleted,
       snapshot_id, oasys_event::varchar(100) AS oasys_event
FROM "assessment-view".sentence_plan;
