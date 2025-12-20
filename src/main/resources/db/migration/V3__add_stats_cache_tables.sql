-- Stats Cache Tables for periodic pre-calculated statistics

-- System-wide stats cache
CREATE TABLE IF NOT EXISTS stats_cache_system (
    term VARCHAR(16) PRIMARY KEY,
    balance BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    count BIGINT NOT NULL,
    total BIGINT NOT NULL,
    ratio BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Users aggregate stats cache
CREATE TABLE IF NOT EXISTS stats_cache_users (
    term VARCHAR(16) PRIMARY KEY,
    number BIGINT NOT NULL,
    balance BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    count BIGINT NOT NULL,
    total BIGINT NOT NULL,
    ratio BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Projects aggregate stats cache
CREATE TABLE IF NOT EXISTS stats_cache_projects (
    term VARCHAR(16) PRIMARY KEY,
    number BIGINT NOT NULL,
    balance BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    count BIGINT NOT NULL,
    total BIGINT NOT NULL,
    ratio BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User rankings cache
CREATE TABLE IF NOT EXISTS stats_cache_user_rankings (
    term VARCHAR(16) NOT NULL,
    ranking_type VARCHAR(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    rank_value BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (term, ranking_type, user_id),
    INDEX idx_user_rankings (term, ranking_type, rank_value)
);

-- Project rankings cache
CREATE TABLE IF NOT EXISTS stats_cache_project_rankings (
    term VARCHAR(16) NOT NULL,
    ranking_type VARCHAR(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    rank_value BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (term, ranking_type, project_id),
    INDEX idx_project_rankings (term, ranking_type, rank_value)
);
