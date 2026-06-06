package mysql

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/traP-jp/pteron-server/internal/domain"
)

type TransactionRepository struct {
	db *sqlx.DB
}

func NewTransactionRepository(db *sqlx.DB) *TransactionRepository {
	return &TransactionRepository{db: db}
}

func (r *TransactionRepository) FindByID(ctx context.Context, id domain.TransactionID) (*domain.Transaction, error) {
	var row transactionRow
	if err := r.db.GetContext(ctx, &row, `
SELECT id, type, amount, project_id, user_id, description, created_at
FROM transactions WHERE id = ?`, id.Bytes()); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	tx, err := row.toDomain()
	return &tx, err
}

func (r *TransactionRepository) FindAll(ctx context.Context, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error) {
	return r.executeQuery(ctx, options, "", nil)
}

func (r *TransactionRepository) FindByUserID(ctx context.Context, userID domain.UserID, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error) {
	return r.executeQuery(ctx, options, "user_id = ?", []any{userID.Bytes()})
}

func (r *TransactionRepository) FindByProjectID(ctx context.Context, projectID domain.ProjectID, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error) {
	return r.executeQuery(ctx, options, "project_id = ?", []any{projectID.Bytes()})
}

func (r *TransactionRepository) Save(ctx context.Context, transaction domain.Transaction) error {
	var projectID any
	if transaction.ProjectID != nil {
		projectID = transaction.ProjectID.Bytes()
	}
	var userID any
	if transaction.UserID != nil {
		userID = transaction.UserID.Bytes()
	}
	_, err := r.db.ExecContext(ctx, `
INSERT INTO transactions (id, type, amount, project_id, user_id, description, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?)`,
		transaction.ID.Bytes(),
		string(transaction.Type),
		transaction.Amount,
		projectID,
		userID,
		transaction.Description,
		transaction.CreatedAt.UTC(),
	)
	return err
}

func (r *TransactionRepository) GetUserBalanceChangeAfter(ctx context.Context, userID domain.UserID, after time.Time) (domain.BalanceChangeData, error) {
	inAmount, err := r.sumAmount(ctx, "user_id = ? AND created_at > ? AND type IN ('TRANSFER', 'SYSTEM')", userID.Bytes(), after.UTC())
	if err != nil {
		return domain.BalanceChangeData{}, err
	}
	outAmount, err := r.sumAmount(ctx, "user_id = ? AND created_at > ? AND type = 'BILL_PAYMENT'", userID.Bytes(), after.UTC())
	if err != nil {
		return domain.BalanceChangeData{}, err
	}
	return domain.BalanceChangeData{InAmount: inAmount, OutAmount: outAmount}, nil
}

func (r *TransactionRepository) GetProjectBalanceChangeAfter(ctx context.Context, projectID domain.ProjectID, after time.Time) (domain.BalanceChangeData, error) {
	inAmount, err := r.sumAmount(ctx, "project_id = ? AND created_at > ? AND type IN ('BILL_PAYMENT', 'SYSTEM')", projectID.Bytes(), after.UTC())
	if err != nil {
		return domain.BalanceChangeData{}, err
	}
	outAmount, err := r.sumAmount(ctx, "project_id = ? AND created_at > ? AND type = 'TRANSFER'", projectID.Bytes(), after.UTC())
	if err != nil {
		return domain.BalanceChangeData{}, err
	}
	return domain.BalanceChangeData{InAmount: inAmount, OutAmount: outAmount}, nil
}

func (r *TransactionRepository) GetStats(ctx context.Context, since time.Time) (domain.TransactionStatsData, error) {
	inCount, inAmount, err := r.countAndSum(ctx, "created_at >= ? AND type IN ('TRANSFER', 'SYSTEM')", since.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	outCount, outAmount, err := r.countAndSum(ctx, "created_at >= ? AND type = 'BILL_PAYMENT'", since.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	return domain.TransactionStatsData{Count: inCount + outCount, Total: inAmount + outAmount, NetChange: inAmount - outAmount, InAmount: inAmount, OutAmount: outAmount}, nil
}

func (r *TransactionRepository) GetUsersStats(ctx context.Context, since time.Time) (domain.TransactionStatsData, error) {
	inCount, inAmount, err := r.countAndSum(ctx, "created_at >= ? AND user_id IS NOT NULL AND type IN ('TRANSFER', 'SYSTEM')", since.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	outCount, outAmount, err := r.countAndSum(ctx, "created_at >= ? AND user_id IS NOT NULL AND type = 'BILL_PAYMENT'", since.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	return domain.TransactionStatsData{Count: inCount + outCount, Total: inAmount + outAmount, NetChange: inAmount - outAmount, InAmount: inAmount, OutAmount: outAmount}, nil
}

func (r *TransactionRepository) GetProjectsStats(ctx context.Context, since time.Time) (domain.TransactionStatsData, error) {
	inCount, inAmount, err := r.countAndSum(ctx, "created_at >= ? AND project_id IS NOT NULL AND type IN ('BILL_PAYMENT', 'SYSTEM')", since.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	outCount, outAmount, err := r.countAndSum(ctx, "created_at >= ? AND project_id IS NOT NULL AND type = 'TRANSFER'", since.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	return domain.TransactionStatsData{Count: inCount + outCount, Total: inAmount + outAmount, NetChange: inAmount - outAmount, InAmount: inAmount, OutAmount: outAmount}, nil
}

func (r *TransactionRepository) GetUserStats(ctx context.Context, userID domain.UserID, since time.Time, until time.Time) (domain.TransactionStatsData, error) {
	inCount, inAmount, err := r.countAndSum(ctx, "user_id = ? AND created_at >= ? AND created_at < ? AND type IN ('TRANSFER', 'SYSTEM')", userID.Bytes(), since.UTC(), until.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	outCount, outAmount, err := r.countAndSum(ctx, "user_id = ? AND created_at >= ? AND created_at < ? AND type = 'BILL_PAYMENT'", userID.Bytes(), since.UTC(), until.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	return domain.TransactionStatsData{Count: inCount + outCount, Total: inAmount + outAmount, NetChange: inAmount - outAmount, InAmount: inAmount, OutAmount: outAmount}, nil
}

func (r *TransactionRepository) GetProjectStats(ctx context.Context, projectID domain.ProjectID, since time.Time, until time.Time) (domain.TransactionStatsData, error) {
	inCount, inAmount, err := r.countAndSum(ctx, "project_id = ? AND created_at >= ? AND created_at < ? AND type IN ('BILL_PAYMENT', 'SYSTEM')", projectID.Bytes(), since.UTC(), until.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	outCount, outAmount, err := r.countAndSum(ctx, "project_id = ? AND created_at >= ? AND created_at < ? AND type = 'TRANSFER'", projectID.Bytes(), since.UTC(), until.UTC())
	if err != nil {
		return domain.TransactionStatsData{}, err
	}
	return domain.TransactionStatsData{Count: inCount + outCount, Total: inAmount + outAmount, NetChange: inAmount - outAmount, InAmount: inAmount, OutAmount: outAmount}, nil
}

func (r *TransactionRepository) sumAmount(ctx context.Context, where string, args ...any) (int64, error) {
	var total sql.NullInt64
	if err := r.db.GetContext(ctx, &total, "SELECT SUM(amount) FROM transactions WHERE "+where, args...); err != nil {
		return 0, err
	}
	if !total.Valid {
		return 0, nil
	}
	return total.Int64, nil
}

func (r *TransactionRepository) countAndSum(ctx context.Context, where string, args ...any) (int64, int64, error) {
	var row struct {
		Count int64         `db:"count"`
		Total sql.NullInt64 `db:"total"`
	}
	if err := r.db.GetContext(ctx, &row, "SELECT COUNT(id) AS count, SUM(amount) AS total FROM transactions WHERE "+where, args...); err != nil {
		return 0, 0, err
	}
	if !row.Total.Valid {
		return row.Count, 0, nil
	}
	return row.Count, row.Total.Int64, nil
}

func (r *TransactionRepository) executeQuery(ctx context.Context, options domain.TransactionQueryOptions, baseFilter string, baseArgs []any) (domain.TransactionQueryResult, error) {
	limit := 20
	if options.Limit != nil {
		limit = *options.Limit
	}
	conditions := make([]string, 0)
	args := make([]any, 0)
	if baseFilter != "" {
		conditions = append(conditions, baseFilter)
		args = append(args, baseArgs...)
	}
	if options.Cursor != nil && *options.Cursor != "" {
		cursor, err := decodeCursor(*options.Cursor)
		if err == nil && cursor != nil {
			conditions = append(conditions, "(created_at < ? OR (created_at = ? AND id < ?))")
			args = append(args, cursor.CreatedAt, cursor.CreatedAt, cursor.ID.Bytes())
		}
	}
	if options.Since != nil {
		conditions = append(conditions, "created_at > ?")
		args = append(args, options.Since.UTC())
	}

	query := "SELECT id, type, amount, project_id, user_id, description, created_at FROM transactions"
	if len(conditions) > 0 {
		query += " WHERE " + strings.Join(conditions, " AND ")
	}
	query += " ORDER BY created_at DESC, id DESC LIMIT ?"
	args = append(args, limit+1)

	var rows []transactionRow
	if err := r.db.SelectContext(ctx, &rows, query, args...); err != nil {
		return domain.TransactionQueryResult{}, err
	}
	items := make([]domain.Transaction, 0, len(rows))
	for _, row := range rows {
		item, err := row.toDomain()
		if err != nil {
			return domain.TransactionQueryResult{}, err
		}
		items = append(items, item)
	}
	var nextCursor *string
	if len(items) > limit {
		items = items[:limit]
		last := items[len(items)-1]
		value := encodeCursor(last.CreatedAt, last.ID)
		nextCursor = &value
	}
	return domain.TransactionQueryResult{Items: items, NextCursor: nextCursor}, nil
}

type transactionRow struct {
	ID          []byte         `db:"id"`
	Type        string         `db:"type"`
	Amount      int64          `db:"amount"`
	ProjectID   []byte         `db:"project_id"`
	UserID      []byte         `db:"user_id"`
	Description sql.NullString `db:"description"`
	CreatedAt   time.Time      `db:"created_at"`
}

func (r transactionRow) toDomain() (domain.Transaction, error) {
	id, err := domain.IDFromBytes(r.ID)
	if err != nil {
		return domain.Transaction{}, err
	}
	var projectID *domain.ProjectID
	if len(r.ProjectID) > 0 {
		parsed, err := domain.IDFromBytes(r.ProjectID)
		if err != nil {
			return domain.Transaction{}, err
		}
		value := domain.ProjectID(parsed)
		projectID = &value
	}
	var userID *domain.UserID
	if len(r.UserID) > 0 {
		parsed, err := domain.IDFromBytes(r.UserID)
		if err != nil {
			return domain.Transaction{}, err
		}
		value := domain.UserID(parsed)
		userID = &value
	}
	var description *string
	if r.Description.Valid {
		description = &r.Description.String
	}
	return domain.Transaction{
		ID:          domain.TransactionID(id),
		Type:        domain.TransactionType(r.Type),
		Amount:      r.Amount,
		ProjectID:   projectID,
		UserID:      userID,
		Description: description,
		CreatedAt:   r.CreatedAt.UTC(),
	}, nil
}
