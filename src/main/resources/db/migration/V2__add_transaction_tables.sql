-- Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id BINARY(16) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    project_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    description VARCHAR(1024),
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Bills table
CREATE TABLE IF NOT EXISTS bills (
    id BINARY(16) PRIMARY KEY,
    amount BIGINT NOT NULL,
    user_id BINARY(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    description VARCHAR(1024),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

-- Indexes for transactions
CREATE INDEX idx_transactions_project_id ON transactions(project_id);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_created_at_id ON transactions(created_at, id);

-- Indexes for bills
CREATE INDEX idx_bills_project_id ON bills(project_id);
CREATE INDEX idx_bills_user_id ON bills(user_id);
CREATE INDEX idx_bills_status ON bills(status);
CREATE INDEX idx_bills_created_at ON bills(created_at);
CREATE INDEX idx_bills_created_at_id ON bills(created_at, id);
