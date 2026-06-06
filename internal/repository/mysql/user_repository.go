package mysql

import (
	"context"
	"database/sql"
	"errors"

	"github.com/jmoiron/sqlx"

	"github.com/traP-jp/pteron-server/internal/domain"
)

type UserRepository struct {
	db *sqlx.DB
}

func NewUserRepository(db *sqlx.DB) *UserRepository {
	return &UserRepository{db: db}
}

func (r *UserRepository) FindAll(ctx context.Context) ([]domain.User, error) {
	var rows []userRow
	if err := r.db.SelectContext(ctx, &rows, "SELECT id, name, account_id FROM users"); err != nil {
		return nil, err
	}
	return userRowsToDomain(rows)
}

func (r *UserRepository) FindByID(ctx context.Context, id domain.UserID) (*domain.User, error) {
	var row userRow
	if err := r.db.GetContext(ctx, &row, "SELECT id, name, account_id FROM users WHERE id = ?", id.Bytes()); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	user, err := row.toDomain()
	return &user, err
}

func (r *UserRepository) FindByUsername(ctx context.Context, username domain.Username) (*domain.User, error) {
	var row userRow
	if err := r.db.GetContext(ctx, &row, "SELECT id, name, account_id FROM users WHERE name = ?", username.String()); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	user, err := row.toDomain()
	return &user, err
}

func (r *UserRepository) FindByIDs(ctx context.Context, ids []domain.UserID) ([]domain.User, error) {
	if len(ids) == 0 {
		return nil, nil
	}
	args := make([][]byte, 0, len(ids))
	for _, id := range ids {
		args = append(args, id.Bytes())
	}
	query, queryArgs, err := sqlx.In("SELECT id, name, account_id FROM users WHERE id IN (?)", args)
	if err != nil {
		return nil, err
	}
	query = r.db.Rebind(query)

	var rows []userRow
	if err := r.db.SelectContext(ctx, &rows, query, queryArgs...); err != nil {
		return nil, err
	}
	return userRowsToDomain(rows)
}

func (r *UserRepository) Save(ctx context.Context, user domain.User) error {
	_, err := r.db.ExecContext(ctx, `
INSERT INTO users (id, name, account_id)
VALUES (?, ?, ?)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    account_id = VALUES(account_id)`,
		user.ID.Bytes(),
		user.Name.String(),
		user.AccountID.Bytes(),
	)
	return err
}

type userRow struct {
	ID        []byte `db:"id"`
	Name      string `db:"name"`
	AccountID []byte `db:"account_id"`
}

func (r userRow) toDomain() (domain.User, error) {
	id, err := domain.IDFromBytes(r.ID)
	if err != nil {
		return domain.User{}, err
	}
	accountID, err := domain.IDFromBytes(r.AccountID)
	if err != nil {
		return domain.User{}, err
	}
	username, err := domain.NewUsername(r.Name)
	if err != nil {
		return domain.User{}, err
	}
	return domain.User{
		ID:        domain.UserID(id),
		Name:      username,
		AccountID: domain.AccountID(accountID),
	}, nil
}

func userRowsToDomain(rows []userRow) ([]domain.User, error) {
	users := make([]domain.User, 0, len(rows))
	for _, row := range rows {
		user, err := row.toDomain()
		if err != nil {
			return nil, err
		}
		users = append(users, user)
	}
	return users, nil
}
