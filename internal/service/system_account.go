package service

import (
	"context"
	"log/slog"
	"time"

	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/gateway"
)

const systemAccountIDKey = "SYSTEM_ACCOUNT_ID"

type SystemConfigStore interface {
	Find(ctx context.Context, key string) (*string, error)
	Save(ctx context.Context, key string, value string) error
}

type SystemAccountService struct {
	economic            gateway.Economic
	configs             SystemConfigStore
	transactions        TransactionStore
	welcomeBonusUser    int64
	welcomeBonusProject int64
	logger              *slog.Logger
}

func NewSystemAccountService(economic gateway.Economic, configs SystemConfigStore, transactions TransactionStore, welcomeBonusUser int64, welcomeBonusProject int64, logger *slog.Logger) *SystemAccountService {
	return &SystemAccountService{
		economic:            economic,
		configs:             configs,
		transactions:        transactions,
		welcomeBonusUser:    welcomeBonusUser,
		welcomeBonusProject: welcomeBonusProject,
		logger:              logger,
	}
}

func (s *SystemAccountService) Initialize(ctx context.Context) error {
	existing, err := s.configs.Find(ctx, systemAccountIDKey)
	if err != nil {
		return err
	}
	if existing != nil {
		s.logger.Info("system account already exists", "account_id", *existing)
		return nil
	}

	s.logger.Info("initializing system account")
	account, err := s.economic.CreateAccount(ctx, true)
	if err != nil {
		return err
	}
	if err := s.configs.Save(ctx, systemAccountIDKey, account.ID.String()); err != nil {
		return err
	}
	s.logger.Info("system account created", "account_id", account.ID.String())
	return nil
}

func (s *SystemAccountService) SendWelcomeBonusToUser(ctx context.Context, userID domain.UserID, userAccountID domain.AccountID) {
	if s.welcomeBonusUser <= 0 {
		return
	}
	s.sendWelcomeBonus(ctx, s.welcomeBonusUser, nil, &userID, userAccountID, "user")
}

func (s *SystemAccountService) SendWelcomeBonusToProject(ctx context.Context, projectID domain.ProjectID, projectAccountID domain.AccountID) {
	if s.welcomeBonusProject <= 0 {
		return
	}
	s.sendWelcomeBonus(ctx, s.welcomeBonusProject, &projectID, nil, projectAccountID, "project")
}

func (s *SystemAccountService) sendWelcomeBonus(ctx context.Context, amount int64, projectID *domain.ProjectID, userID *domain.UserID, targetAccountID domain.AccountID, target string) {
	systemAccountID, err := s.systemAccountID(ctx)
	if err != nil || systemAccountID == nil {
		s.logger.Error("system account id not found; cannot send welcome bonus", "target", target, "error", err)
		return
	}
	if err := s.economic.Transfer(ctx, *systemAccountID, targetAccountID, amount); err != nil {
		s.logger.Error("failed to send welcome bonus", "target", target, "error", err)
		return
	}
	description := "Welcome Bonus"
	transaction := domain.Transaction{
		ID:          domain.MustNewID(),
		Type:        domain.TransactionTypeSystem,
		Amount:      amount,
		ProjectID:   projectID,
		UserID:      userID,
		Description: &description,
		CreatedAt:   time.Now().UTC(),
	}
	if err := s.transactions.Save(ctx, transaction); err != nil {
		s.logger.Error("failed to save welcome bonus transaction", "target", target, "error", err)
		return
	}
	s.logger.Info("sent welcome bonus", "target", target, "amount", amount)
}

func (s *SystemAccountService) systemAccountID(ctx context.Context) (*domain.AccountID, error) {
	value, err := s.configs.Find(ctx, systemAccountIDKey)
	if err != nil || value == nil {
		return nil, err
	}
	id, err := domain.ParseID(*value)
	if err != nil {
		return nil, err
	}
	accountID := domain.AccountID(id)
	return &accountID, nil
}
