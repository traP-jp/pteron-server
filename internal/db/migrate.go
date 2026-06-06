package db

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/jmoiron/sqlx"
)

const migrationsTable = "pteron_schema_migrations"

func Migrate(ctx context.Context, conn *sqlx.DB, dir string, logger *slog.Logger) error {
	files, err := filepath.Glob(filepath.Join(dir, "*.sql"))
	if err != nil {
		return err
	}
	sort.Strings(files)
	if len(files) == 0 {
		return fmt.Errorf("no migration files found in %s", dir)
	}

	if err := ensureMigrationTable(ctx, conn); err != nil {
		return err
	}

	applied, err := appliedMigrations(ctx, conn)
	if err != nil {
		return err
	}
	if len(applied) == 0 {
		hasExistingSchema, err := tableExists(ctx, conn, "users")
		if err != nil {
			return err
		}
		if hasExistingSchema {
			logger.Info("existing schema detected; baselining Go migrations")
			return baselineMigrations(ctx, conn, files)
		}
	}

	for _, file := range files {
		version := migrationVersion(file)
		if applied[version] {
			continue
		}
		content, err := os.ReadFile(file)
		if err != nil {
			return err
		}
		logger.Info("applying migration", "version", version, "file", filepath.Base(file))
		if _, err := conn.ExecContext(ctx, string(content)); err != nil {
			return fmt.Errorf("apply migration %s: %w", file, err)
		}
		if _, err := conn.ExecContext(ctx, "INSERT INTO "+migrationsTable+" (version) VALUES (?)", version); err != nil {
			return err
		}
	}
	return nil
}

func ensureMigrationTable(ctx context.Context, conn *sqlx.DB) error {
	_, err := conn.ExecContext(ctx, `
CREATE TABLE IF NOT EXISTS pteron_schema_migrations (
    version VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)`)
	return err
}

func appliedMigrations(ctx context.Context, conn *sqlx.DB) (map[string]bool, error) {
	var versions []string
	if err := conn.SelectContext(ctx, &versions, "SELECT version FROM "+migrationsTable); err != nil {
		return nil, err
	}
	applied := make(map[string]bool, len(versions))
	for _, version := range versions {
		applied[version] = true
	}
	return applied, nil
}

func baselineMigrations(ctx context.Context, conn *sqlx.DB, files []string) error {
	tx, err := conn.BeginTxx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	for _, file := range files {
		if _, err := tx.ExecContext(ctx, "INSERT IGNORE INTO "+migrationsTable+" (version) VALUES (?)", migrationVersion(file)); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func tableExists(ctx context.Context, conn *sqlx.DB, table string) (bool, error) {
	var name string
	err := conn.GetContext(ctx, &name, `
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = ?
LIMIT 1`, table)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return false, nil
		}
		return false, err
	}
	return name != "", nil
}

func migrationVersion(file string) string {
	base := filepath.Base(file)
	if idx := strings.Index(base, "__"); idx > 0 {
		return base[:idx]
	}
	return strings.TrimSuffix(base, filepath.Ext(base))
}
