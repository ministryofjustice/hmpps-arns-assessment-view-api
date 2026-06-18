-- Reporting views: expose tables with enum/uuid/boolean columns widened to varchar for consumers

CREATE VIEW "assessment-view".flyway_schema_history_vw AS
SELECT installed_rank, version, description, type, script, checksum, installed_by, installed_on,
       execution_time, success::varchar(10) AS success
FROM "assessment-view".flyway_schema_history;

CREATE VIEW "assessment-view".free_text_vw AS
SELECT id, type::varchar(100) AS type, text_length, goal_id, plan_agreement_id, created_by_user_id,
       created_at, goal_note_type::varchar(100) AS goal_note_type, text_hash::varchar(100) AS text_hash
FROM "assessment-view".free_text;

CREATE VIEW "assessment-view".goal_vw AS
SELECT id, sentence_plan_id, area_of_need::varchar(100) AS area_of_need, target_date,
       status::varchar(100) AS status, status_date, created_by_user_id, created_at, updated_at,
       goal_order, updated_by_user_id, title_length, title_hash
FROM "assessment-view".goal;

CREATE VIEW "assessment-view".goal_related_area_of_need_vw AS
SELECT goal_id, criminogenic_need::varchar(100) AS criminogenic_need
FROM "assessment-view".goal_related_area_of_need;

CREATE VIEW "assessment-view".plan_agreement_vw AS
SELECT id, sentence_plan_id, status::varchar(100) AS status, status_date, created_by_user_id, created_at
FROM "assessment-view".plan_agreement;

CREATE VIEW "assessment-view".sentence_plan_vw AS
SELECT id, created_at, updated_at, last_synced_at, oasys_pk::varchar(100) AS oasys_pk, version,
       region_code::varchar(100) AS region_code, deleted::varchar(100) AS deleted
FROM "assessment-view".sentence_plan;

CREATE VIEW "assessment-view".sentence_plan_identifier_vw AS
SELECT id, sentence_plan_id, type::varchar(100) AS type, value::varchar(100) AS value
FROM "assessment-view".sentence_plan_identifier;

CREATE VIEW "assessment-view".step_vw AS
SELECT id, goal_id, description_length, description_hash::varchar(100) AS description_hash,
       actor::varchar(100) AS actor, status::varchar(100) AS status, status_date,
       created_by_user_id, created_at
FROM "assessment-view".step;

CREATE VIEW "assessment-view".sync_state_vw AS
SELECT id::varchar(100) AS id, last_sync_started_at
FROM "assessment-view".sync_state;