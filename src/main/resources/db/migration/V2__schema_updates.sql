DROP TABLE sentence_plan_oasys_pk;
ALTER TABLE sentence_plan ADD COLUMN oasys_pk INTEGER;

ALTER TABLE sentence_plan ADD COLUMN version INTEGER NOT NULL;

ALTER TABLE goal ADD COLUMN goal_order INTEGER NOT NULL;

CREATE TYPE goal_note_type AS ENUM ('ACHIEVED', 'REMOVED', 'READDED', 'UPDATED', 'PROGRESS');
ALTER TABLE free_text ADD COLUMN goal_note_type goal_note_type;

ALTER TABLE free_text ADD COLUMN text_hash TEXT;

ALTER TABLE sentence_plan ADD COLUMN region_code TEXT;
