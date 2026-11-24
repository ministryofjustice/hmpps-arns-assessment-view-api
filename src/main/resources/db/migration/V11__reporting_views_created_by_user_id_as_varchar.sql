-- Change the reporting created_by_user_id column from TEXT to VARCHAR(108)
-- for downstream Oracle mapping. It continues to expose the source user ID.
-- Underlying table columns and data are unchanged.
DROP VIEW "assessment-view".free_text_vw;
DROP VIEW "assessment-view".goal_vw;
DROP VIEW "assessment-view".plan_agreement_vw;
DROP VIEW "assessment-view".step_vw;

CREATE VIEW "assessment-view".free_text_vw AS
SELECT id, type::varchar(100) AS type, text_length, goal_id, plan_agreement_id,
    created_by_source_user_id::varchar(108) AS created_by_user_id,
    created_at, goal_note_type::varchar(100) AS goal_note_type, text_hash::varchar(100) AS text_hash,
    created_by_user_name, created_by_source_user_id, created_by_auth_source
FROM "assessment-view".free_text;

CREATE VIEW "assessment-view".goal_vw AS
SELECT id, sentence_plan_id, area_of_need::varchar(100) AS area_of_need, target_date,
       status::varchar(100) AS status, status_date,
       created_by_source_user_id::varchar(108) AS created_by_user_id, created_at, updated_at,
       goal_order, updated_by_user_id, title_length, title_hash, created_by_user_name,
       created_by_source_user_id, created_by_auth_source, updated_by_source_user_id, updated_by_auth_source
FROM "assessment-view".goal;

CREATE VIEW "assessment-view".plan_agreement_vw AS
SELECT id, sentence_plan_id, status::varchar(100) AS status, status_date,
       created_by_source_user_id::varchar(108) AS created_by_user_id, created_at,
       created_by_user_name, created_by_source_user_id, created_by_auth_source
FROM "assessment-view".plan_agreement;

CREATE VIEW "assessment-view".step_vw AS
SELECT id, goal_id, description_length, description_hash::varchar(100) AS description_hash,
    actor::varchar(100) AS actor, status::varchar(100) AS status, status_date,
       created_by_source_user_id::varchar(108) AS created_by_user_id, created_at,
       created_by_user_name, created_by_source_user_id, created_by_auth_source
FROM "assessment-view".step;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname = 'oasys_national_reporting_user'
    ) THEN
        GRANT SELECT ON
    "assessment-view".free_text_vw,
    "assessment-view".goal_vw,
    "assessment-view".plan_agreement_vw,
    "assessment-view".step_vw
    TO oasys_national_reporting_user;
END IF;
END
$$;
