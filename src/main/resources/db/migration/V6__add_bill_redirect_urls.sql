-- Add redirect URL columns to bills table
ALTER TABLE bills ADD COLUMN success_url VARCHAR(2048);
ALTER TABLE bills ADD COLUMN cancel_url VARCHAR(2048);
