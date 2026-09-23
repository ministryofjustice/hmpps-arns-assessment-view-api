-- The free text is needed for data hub.
-- ONR is restricted from viewing these columns
ALTER TABLE step ADD COLUMN description TEXT;
ALTER TABLE goal ADD COLUMN title TEXT;
ALTER TABLE free_text ADD COLUMN text TEXT;