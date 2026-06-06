package service

import (
	"context"
	"fmt"

	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/gateway"
)

type AccountService struct {
	economic gateway.Economic
}

func NewAccountService(economic gateway.Economic) *AccountService {
	return &AccountService{economic: economic}
}

func (s *AccountService) GetAccountByID(ctx context.Context, id domain.AccountID) (domain.Account, error) {
	account, err := s.economic.FindAccountByID(ctx, id)
	if err != nil {
		return domain.Account{}, err
	}
	if account == nil {
		return domain.Account{}, fmt.Errorf("account not found: %s", id.String())
	}
	return *account, nil
}

func (s *AccountService) GetAccountsByIDs(ctx context.Context, ids []domain.AccountID) ([]domain.Account, error) {
	return s.economic.FindAccountsByIDs(ctx, ids)
}
