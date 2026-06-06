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

type BillRepository struct {
	db *sqlx.DB
}

func NewBillRepository(db *sqlx.DB) *BillRepository {
	return &BillRepository{db: db}
}

func (r *BillRepository) FindByID(ctx context.Context, id domain.BillID) (*domain.Bill, error) {
	var row billRow
	if err := r.db.GetContext(ctx, &row, `
SELECT id, amount, user_id, project_id, description, status, success_url, cancel_url, created_at
FROM bills WHERE id = ?`, id.Bytes()); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	bill, err := row.toDomain()
	return &bill, err
}

func (r *BillRepository) FindByUserID(ctx context.Context, userID domain.UserID, options domain.BillQueryOptions) (domain.BillQueryResult, error) {
	return r.executeQuery(ctx, options, "user_id = ?", []any{userID.Bytes()})
}

func (r *BillRepository) FindByProjectID(ctx context.Context, projectID domain.ProjectID, options domain.BillQueryOptions) (domain.BillQueryResult, error) {
	return r.executeQuery(ctx, options, "project_id = ?", []any{projectID.Bytes()})
}

func (r *BillRepository) Save(ctx context.Context, bill domain.Bill) error {
	_, err := r.db.ExecContext(ctx, `
INSERT INTO bills (id, amount, user_id, project_id, description, status, success_url, cancel_url, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    amount = VALUES(amount),
    user_id = VALUES(user_id),
    project_id = VALUES(project_id),
    description = VALUES(description),
    status = VALUES(status),
    success_url = VALUES(success_url),
    cancel_url = VALUES(cancel_url),
    created_at = VALUES(created_at)`,
		bill.ID.Bytes(),
		bill.Amount,
		bill.UserID.Bytes(),
		bill.ProjectID.Bytes(),
		bill.Description,
		string(bill.Status),
		bill.SuccessURL,
		bill.CancelURL,
		bill.CreatedAt.UTC(),
	)
	return err
}

func (r *BillRepository) executeQuery(ctx context.Context, options domain.BillQueryOptions, baseFilter string, baseArgs []any) (domain.BillQueryResult, error) {
	limit := 20
	if options.Limit != nil {
		limit = *options.Limit
	}
	conditions := []string{baseFilter}
	args := append([]any{}, baseArgs...)
	if options.Cursor != nil && *options.Cursor != "" {
		cursor, err := decodeCursor(*options.Cursor)
		if err == nil && cursor != nil {
			conditions = append(conditions, "(created_at < ? OR (created_at = ? AND id < ?))")
			args = append(args, cursor.CreatedAt, cursor.CreatedAt, cursor.ID.Bytes())
		}
	}
	if options.Status != nil {
		conditions = append(conditions, "status = ?")
		args = append(args, string(*options.Status))
	}

	query := "SELECT id, amount, user_id, project_id, description, status, success_url, cancel_url, created_at FROM bills"
	query += " WHERE " + strings.Join(conditions, " AND ")
	query += " ORDER BY created_at DESC, id DESC LIMIT ?"
	args = append(args, limit+1)

	var rows []billRow
	if err := r.db.SelectContext(ctx, &rows, query, args...); err != nil {
		return domain.BillQueryResult{}, err
	}
	items := make([]domain.Bill, 0, len(rows))
	for _, row := range rows {
		item, err := row.toDomain()
		if err != nil {
			return domain.BillQueryResult{}, err
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
	return domain.BillQueryResult{Items: items, NextCursor: nextCursor}, nil
}

type billRow struct {
	ID          []byte         `db:"id"`
	Amount      int64          `db:"amount"`
	UserID      []byte         `db:"user_id"`
	ProjectID   []byte         `db:"project_id"`
	Description sql.NullString `db:"description"`
	Status      string         `db:"status"`
	SuccessURL  sql.NullString `db:"success_url"`
	CancelURL   sql.NullString `db:"cancel_url"`
	CreatedAt   time.Time      `db:"created_at"`
}

func (r billRow) toDomain() (domain.Bill, error) {
	id, err := domain.IDFromBytes(r.ID)
	if err != nil {
		return domain.Bill{}, err
	}
	userID, err := domain.IDFromBytes(r.UserID)
	if err != nil {
		return domain.Bill{}, err
	}
	projectID, err := domain.IDFromBytes(r.ProjectID)
	if err != nil {
		return domain.Bill{}, err
	}
	var description *string
	if r.Description.Valid {
		description = &r.Description.String
	}
	var successURL *string
	if r.SuccessURL.Valid {
		successURL = &r.SuccessURL.String
	}
	var cancelURL *string
	if r.CancelURL.Valid {
		cancelURL = &r.CancelURL.String
	}
	return domain.Bill{
		ID:          domain.BillID(id),
		Amount:      r.Amount,
		UserID:      domain.UserID(userID),
		ProjectID:   domain.ProjectID(projectID),
		Description: description,
		Status:      domain.BillStatus(r.Status),
		SuccessURL:  successURL,
		CancelURL:   cancelURL,
		CreatedAt:   r.CreatedAt.UTC(),
	}, nil
}
