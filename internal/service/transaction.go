package service

import (
	"context"
	"time"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/traP-jp/pteron-server/internal/app"
	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/gateway"
)

type TransactionStore interface {
	FindAll(ctx context.Context, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error)
	FindByUserID(ctx context.Context, userID domain.UserID, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error)
	FindByProjectID(ctx context.Context, projectID domain.ProjectID, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error)
	Save(ctx context.Context, transaction domain.Transaction) error
}

type TransactionService struct {
	transactions TransactionStore
	users        UserStore
	projects     ProjectStore
	economic     gateway.Economic
}

func NewTransactionService(transactions TransactionStore, users UserStore, projects ProjectStore, economic gateway.Economic) *TransactionService {
	return &TransactionService{
		transactions: transactions,
		users:        users,
		projects:     projects,
		economic:     economic,
	}
}

func (s *TransactionService) GetTransactions(ctx context.Context, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error) {
	return s.transactions.FindAll(ctx, options)
}

func (s *TransactionService) GetUserTransactions(ctx context.Context, userID domain.UserID, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error) {
	return s.transactions.FindByUserID(ctx, userID, options)
}

func (s *TransactionService) GetProjectTransactions(ctx context.Context, projectID domain.ProjectID, options domain.TransactionQueryOptions) (domain.TransactionQueryResult, error) {
	return s.transactions.FindByProjectID(ctx, projectID, options)
}

func (s *TransactionService) Transfer(ctx context.Context, projectID domain.ProjectID, toUserID domain.UserID, amount int64, description *string) (domain.Transaction, error) {
	if amount <= 0 {
		return domain.Transaction{}, app.NewError(app.CodeBadRequest, "Amount must be greater than zero")
	}
	project, err := s.projects.FindByID(ctx, projectID)
	if err != nil {
		return domain.Transaction{}, err
	}
	if project == nil {
		return domain.Transaction{}, app.NewError(app.CodeNotFound, "Project not found")
	}
	user, err := s.users.FindByID(ctx, toUserID)
	if err != nil {
		return domain.Transaction{}, err
	}
	if user == nil {
		return domain.Transaction{}, app.NewError(app.CodeNotFound, "User not found")
	}

	if err := s.economic.Transfer(ctx, project.AccountID, user.AccountID, amount); err != nil {
		switch status.Code(err) {
		case codes.FailedPrecondition:
			return domain.Transaction{}, app.NewError(app.CodeBadRequest, "Insufficient balance for project")
		case codes.NotFound:
			return domain.Transaction{}, app.NewError(app.CodeNotFound, "Account not found")
		default:
			return domain.Transaction{}, err
		}
	}

	transaction := domain.Transaction{
		ID:          domain.MustNewID(),
		Type:        domain.TransactionTypeTransfer,
		Amount:      amount,
		ProjectID:   &projectID,
		UserID:      &toUserID,
		Description: description,
		CreatedAt:   time.Now().UTC(),
	}
	if err := s.transactions.Save(ctx, transaction); err != nil {
		return domain.Transaction{}, err
	}
	return transaction, nil
}
