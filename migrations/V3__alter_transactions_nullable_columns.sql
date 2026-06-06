-- Alter transactions table to allow nullable project_id and user_id
-- This matches the domain model where these fields are optional

-- Remove foreign key constraints first
ALTER TABLE transactions DROP FOREIGN KEY transactions_ibfk_1;
ALTER TABLE transactions DROP FOREIGN KEY transactions_ibfk_2;

-- Modify columns to allow NULL
ALTER TABLE transactions MODIFY COLUMN project_id BINARY(16) NULL;
ALTER TABLE transactions MODIFY COLUMN user_id BINARY(16) NULL;

-- Re-add foreign key constraints with ON DELETE SET NULL behavior
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_project_id
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

