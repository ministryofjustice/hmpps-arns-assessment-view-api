CREATE TYPE identifier_type AS ENUM ('CRN', 'NOMIS');
CREATE TYPE plan_status AS ENUM ('DRAFT', 'AGREED', 'DO_NOT_AGREE', 'COULD_NOT_ANSWER', 'UPDATED_AGREED', 'UPDATED_DO_NOT_AGREE');
CREATE TYPE criminogenic_need AS ENUM ('ACCOMMODATION', 'EMPLOYMENT_AND_EDUCATION', 'FINANCES', 'DRUG_USE', 'ALCOHOL_USE', 'HEALTH_AND_WELLBEING', 'PERSONAL_RELATIONSHIPS_AND_COMMUNITY', 'THINKING_BEHAVIOURS_AND_ATTITUDES');
CREATE TYPE goal_status AS ENUM ('ACTIVE', 'FUTURE', 'ACHIEVED', 'REMOVED');
CREATE TYPE step_status AS ENUM ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANNOT_BE_DONE_YET', 'NO_LONGER_NEEDED');
CREATE TYPE actor_type AS ENUM ('PERSON_ON_PROBATION', 'PROBATION_PRACTITIONER', 'PROGRAMME_STAFF', 'PARTNERSHIP_AGENCY', 'CRS_PROVIDER', 'PRISON_OFFENDER_MANAGER', 'SOMEONE_ELSE');
CREATE TYPE free_text_type AS ENUM ('GOAL_NOTE', 'AGREEMENT_DETAILS', 'AGREEMENT_NOTES');

CREATE TABLE sentence_plan (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE sentence_plan_identifier (
    id UUID PRIMARY KEY,
    sentence_plan_id UUID NOT NULL REFERENCES sentence_plan(id) ON DELETE CASCADE,
    type identifier_type NOT NULL,
    value TEXT NOT NULL,
    UNIQUE (sentence_plan_id, type, value)
);

CREATE INDEX idx_sentence_plan_identifier_type_value ON sentence_plan_identifier(type, value);

CREATE TABLE sentence_plan_oasys_pk (
    sentence_plan_id UUID NOT NULL REFERENCES sentence_plan(id) ON DELETE CASCADE,
    oasys_assessment_pk TEXT NOT NULL,
    PRIMARY KEY (sentence_plan_id, oasys_assessment_pk)
);

CREATE TABLE plan_agreement (
    id UUID PRIMARY KEY,
    sentence_plan_id UUID NOT NULL REFERENCES sentence_plan(id) ON DELETE CASCADE,
    status plan_status NOT NULL,
    status_date TIMESTAMPTZ,
    created_by_user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_plan_agreement_sentence_plan_id ON plan_agreement(sentence_plan_id);

CREATE TABLE goal (
    id UUID PRIMARY KEY,
    sentence_plan_id UUID NOT NULL REFERENCES sentence_plan(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    area_of_need criminogenic_need NOT NULL,
    target_date DATE,
    status goal_status NOT NULL,
    status_date TIMESTAMPTZ,
    created_by_user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_goal_sentence_plan_id ON goal(sentence_plan_id);

CREATE TABLE goal_related_area_of_need (
    goal_id UUID NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    criminogenic_need criminogenic_need NOT NULL,
    PRIMARY KEY (goal_id, criminogenic_need)
);

CREATE TABLE free_text (
    id UUID PRIMARY KEY,
    type free_text_type NOT NULL,
    text_length INT NOT NULL,
    goal_id UUID REFERENCES goal(id) ON DELETE CASCADE,
    plan_agreement_id UUID REFERENCES plan_agreement(id) ON DELETE CASCADE,
    created_by_user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_free_text_goal_id ON free_text(goal_id);
CREATE INDEX idx_free_text_plan_agreement_id ON free_text(plan_agreement_id);

CREATE TABLE step (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    actor actor_type NOT NULL,
    status step_status NOT NULL,
    status_date TIMESTAMPTZ,
    created_by_user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_step_goal_id ON step(goal_id);
