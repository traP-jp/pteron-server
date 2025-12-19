-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    account_id BINARY(16) NOT NULL
);

-- Projects table
CREATE TABLE IF NOT EXISTS projects (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(32) COLLATE utf8mb4_general_ci NOT NULL UNIQUE,
    owner_id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL UNIQUE,
    url VARCHAR(2048) NULL
);

-- Project Admins table
CREATE TABLE IF NOT EXISTS project_admins (
    project_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    PRIMARY KEY (project_id, user_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- API Clients table
CREATE TABLE IF NOT EXISTS api_clients (
    client_id BINARY(16) PRIMARY KEY,
    project_id BINARY(16) NOT NULL,
    client_secret VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_users_account_id ON users(account_id);
CREATE INDEX idx_projects_owner_id ON projects(owner_id);
CREATE INDEX idx_api_clients_project_id ON api_clients(project_id);
