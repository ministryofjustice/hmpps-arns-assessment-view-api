ALTER TABLE goal ADD COLUMN created_by_user_name TEXT;
ALTER TABLE step ADD COLUMN created_by_user_name TEXT;
ALTER TABLE free_text ADD COLUMN created_by_user_name TEXT;
ALTER TABLE plan_agreement ADD COLUMN created_by_user_name TEXT;

CREATE OR REPLACE VIEW "assessment-view".free_text_vw AS
SELECT id, type::varchar(100) AS type, text_length, goal_id, plan_agreement_id, created_by_user_id,
       created_at, goal_note_type::varchar(100) AS goal_note_type, text_hash::varchar(100) AS text_hash,
       created_by_user_name
FROM "assessment-view".free_text;

CREATE OR REPLACE VIEW "assessment-view".goal_vw AS
SELECT id, sentence_plan_id, area_of_need::varchar(100) AS area_of_need, target_date,
       status::varchar(100) AS status, status_date, created_by_user_id, created_at, updated_at,
       goal_order, updated_by_user_id, title_length, title_hash, created_by_user_name
FROM "assessment-view".goal;

CREATE OR REPLACE VIEW "assessment-view".plan_agreement_vw AS
SELECT id, sentence_plan_id, status::varchar(100) AS status, status_date, created_by_user_id, created_at,
       created_by_user_name
FROM "assessment-view".plan_agreement;

CREATE OR REPLACE VIEW "assessment-view".step_vw AS
SELECT id, goal_id, description_length, description_hash::varchar(100) AS description_hash,
       actor::varchar(100) AS actor, status::varchar(100) AS status, status_date,
       created_by_user_id, created_at, created_by_user_name
FROM "assessment-view".step;
