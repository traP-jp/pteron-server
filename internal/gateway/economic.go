package gateway

import (
	"context"

	"github.com/traP-jp/pteron-server/internal/domain"
)

type Economic interface {
	Verify(ctx context.Context) error
	FindAccountByID(ctx context.Context, accountID domain.AccountID) (*domain.Account, error)
	FindAccountsByIDs(ctx context.Context, accountIDs []domain.AccountID) ([]domain.Account, error)
	CreateAccount(ctx context.Context, canOverdraft bool) (domain.Account, error)
	Transfer(ctx context.Context, from domain.AccountID, to domain.AccountID, amount int64) error
}
