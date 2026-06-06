package service

import (
	"context"

	"github.com/traP-jp/pteron-server/internal/app"
	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/gateway"
)

type UserStore interface {
	FindAll(ctx context.Context) ([]domain.User, error)
	FindByID(ctx context.Context, id domain.UserID) (*domain.User, error)
	FindByIDs(ctx context.Context, ids []domain.UserID) ([]domain.User, error)
	FindByUsername(ctx context.Context, username domain.Username) (*domain.User, error)
	Save(ctx context.Context, user domain.User) error
}

type UserService struct {
	users    UserStore
	economic gateway.Economic
	bonus    interface {
		SendWelcomeBonusToUser(ctx context.Context, userID domain.UserID, userAccountID domain.AccountID)
	}
}

func NewUserService(users UserStore, economic gateway.Economic, bonus interface {
	SendWelcomeBonusToUser(ctx context.Context, userID domain.UserID, userAccountID domain.AccountID)
}) *UserService {
	return &UserService{users: users, economic: economic, bonus: bonus}
}

func (s *UserService) EnsureUser(ctx context.Context, username domain.Username) error {
	existing, err := s.users.FindByUsername(ctx, username)
	if err != nil {
		return err
	}
	if existing != nil {
		return nil
	}

	account, err := s.economic.CreateAccount(ctx, true)
	if err != nil {
		return err
	}
	user := domain.User{
		ID:        domain.MustNewID(),
		Name:      username,
		AccountID: account.ID,
	}
	if err := s.users.Save(ctx, user); err != nil {
		return err
	}
	if s.bonus != nil {
		s.bonus.SendWelcomeBonusToUser(ctx, user.ID, user.AccountID)
	}
	return nil
}

func (s *UserService) GetUser(ctx context.Context, idOrName string) (domain.User, error) {
	if id, err := domain.ParseID(idOrName); err == nil {
		return s.GetUserByID(ctx, domain.UserID(id))
	}
	username, err := domain.NewUsername(idOrName)
	if err != nil {
		return domain.User{}, app.WrapError(app.CodeBadRequest, "Invalid user", err)
	}
	return s.GetUserByName(ctx, username)
}

func (s *UserService) GetUserByID(ctx context.Context, id domain.UserID) (domain.User, error) {
	user, err := s.users.FindByID(ctx, id)
	if err != nil {
		return domain.User{}, err
	}
	if user == nil {
		return domain.User{}, app.NewError(app.CodeNotFound, "User not found")
	}
	return *user, nil
}

func (s *UserService) GetUserByName(ctx context.Context, username domain.Username) (domain.User, error) {
	user, err := s.users.FindByUsername(ctx, username)
	if err != nil {
		return domain.User{}, err
	}
	if user == nil {
		return domain.User{}, app.NewError(app.CodeNotFound, "User not found")
	}
	return *user, nil
}

func (s *UserService) GetUsersByIDs(ctx context.Context, ids []domain.UserID) ([]domain.User, error) {
	return s.users.FindByIDs(ctx, ids)
}

func (s *UserService) GetAllUsers(ctx context.Context) ([]domain.User, error) {
	return s.users.FindAll(ctx)
}
