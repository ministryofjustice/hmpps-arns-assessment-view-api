-- Redact step descriptions: store length and sha256 hash instead of raw text.
ALTER TABLE step ADD COLUMN description_length INTEGER     NOT NULL DEFAULT 0;
ALTER TABLE step ADD COLUMN description_hash   VARCHAR(64) NOT NULL DEFAULT '';

-- Backfill from existing plaintext. sha256() is built in to Postgres
UPDATE step
SET description_length = length(description),
    description_hash   = encode(sha256(description::bytea), 'hex');

ALTER TABLE step DROP COLUMN description;
