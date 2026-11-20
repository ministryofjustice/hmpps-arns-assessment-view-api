-- Recreate the reporting views to expose created_by_source_user_id as
-- created_by_user_id, replacing the original column projection and changing
-- the view column type from UUID to TEXT. Underlying table data is unchanged.
DROP VIEW "assessment-view".free_text_vw;
DROP VIEW "assessment-view".goal_vw;
DROP VIEW "assessment-view".plan_agreement_vw;
DROP VIEW "assessment-view".step_vw;

-- Replace the value exposed as created_by_user_id in the reporting views with
-- created_by_source_user_id, aliased as created_by_user_id. This replaces the
-- projection of the underlying created_by_user_id column; table data is unchanged.
-- Preserve existing view column names, order and types. No fallback is applied
-- when created_by_source_user_id is NULL.
CREATE OR REPLACE VIEW "assessment-view".free_text_vw AS
SELECT id, type::varchar(100) AS type, text_length, goal_id, plan_agreement_id,
       created_by_source_user_id AS created_by_user_id,
       created_at, goal_note_type::varchar(100) AS goal_note_type, text_hash::varchar(100) AS text_hash,
    created_by_user_name, created_by_source_user_id, created_by_auth_source
FROM "assessment-view".free_text;

CREATE OR REPLACE VIEW "assessment-view".goal_vw AS
SELECT id, sentence_plan_id, area_of_need::varchar(100) AS area_of_need, target_date,
       status::varchar(100) AS status, status_date,
       created_by_source_user_id AS created_by_user_id, created_at, updated_at,
       goal_order, updated_by_user_id, title_length, title_hash, created_by_user_name,
       created_by_source_user_id, created_by_auth_source, updated_by_source_user_id, updated_by_auth_source
FROM "assessment-view".goal;

CREATE OR REPLACE VIEW "assessment-view".plan_agreement_vw AS
SELECT id, sentence_plan_id, status::varchar(100) AS status, status_date,
       created_by_source_user_id AS created_by_user_id, created_at,
       created_by_user_name, created_by_source_user_id, created_by_auth_source
FROM "assessment-view".plan_agreement;

CREATE OR REPLACE VIEW "assessment-view".step_vw AS
SELECT id, goal_id, description_length, description_hash::varchar(100) AS description_hash,
    actor::varchar(100) AS actor, status::varchar(100) AS status, status_date,
       created_by_source_user_id AS created_by_user_id, created_at,
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
