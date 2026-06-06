-- システム統計キャッシュ
CREATE TABLE stats_cache_system (
    term VARCHAR(16) PRIMARY KEY,
    balance BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    count BIGINT NOT NULL,
    total BIGINT NOT NULL,
    ratio BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- ユーザー集計統計キャッシュ
CREATE TABLE stats_cache_users_aggregate (
    term VARCHAR(16) PRIMARY KEY,
    number BIGINT NOT NULL,
    balance BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    count BIGINT NOT NULL,
    total BIGINT NOT NULL,
    ratio BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- プロジェクト集計統計キャッシュ
CREATE TABLE stats_cache_projects_aggregate (
    term VARCHAR(16) PRIMARY KEY,
    number BIGINT NOT NULL,
    balance BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    count BIGINT NOT NULL,
    total BIGINT NOT NULL,
    ratio BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- ユーザーランキングキャッシュ
CREATE TABLE stats_cache_user_rankings (
    term VARCHAR(16) NOT NULL,
    ranking_type VARCHAR(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    `rank` BIGINT NOT NULL,
    value BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    PRIMARY KEY (term, ranking_type, user_id),
    INDEX idx_user_rankings_order (term, ranking_type, `rank`)
);

-- プロジェクトランキングキャッシュ
CREATE TABLE stats_cache_project_rankings (
    term VARCHAR(16) NOT NULL,
    ranking_type VARCHAR(16) NOT NULL,
    project_id BINARY(16) NOT NULL,
    `rank` BIGINT NOT NULL,
    value BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    PRIMARY KEY (term, ranking_type, project_id),
    INDEX idx_project_rankings_order (term, ranking_type, `rank`)
);
