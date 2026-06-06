package mysql

import (
	"context"
	"database/sql"
	"errors"

	"github.com/jmoiron/sqlx"
)

type SystemConfigRepository struct {
	db *sqlx.DB
}

func NewSystemConfigRepository(db *sqlx.DB) *SystemConfigRepository {
	return &SystemConfigRepository{db: db}
}

func (r *SystemConfigRepository) Find(ctx context.Context, key string) (*string, error) {
	var value string
	if err := r.db.GetContext(ctx, &value, "SELECT value FROM system_configs WHERE `key` = ?", key); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &value, nil
}

func (r *SystemConfigRepository) Save(ctx context.Context, key string, value string) error {
	_, err := r.db.ExecContext(ctx, `
INSERT INTO system_configs (`+"`key`, `value`"+`)
VALUES (?, ?)
ON DUPLICATE KEY UPDATE `+"`value`"+` = VALUES(`+"`value`"+`)`, key, value)
	return err
}
