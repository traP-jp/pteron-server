package service

import (
	"context"
	"time"

	"github.com/traP-jp/pteron-server/internal/app"
	"github.com/traP-jp/pteron-server/internal/domain"
	"github.com/traP-jp/pteron-server/internal/gateway"
)

type StatsCacheStore interface {
	GetSystemStats(ctx context.Context, term domain.Term) (*domain.SystemStats, error)
	GetUsersAggregateStats(ctx context.Context, term domain.Term) (*domain.AggregateStats, error)
	GetProjectsAggregateStats(ctx context.Context, term domain.Term) (*domain.AggregateStats, error)
	GetUserRankings(ctx context.Context, rankingType domain.RankingType, term domain.Term, ascending bool, limit int, cursor *string) (domain.RankingQueryResult[domain.UserRankingEntry], error)
	GetProjectRankings(ctx context.Context, rankingType domain.RankingType, term domain.Term, ascending bool, limit int, cursor *string) (domain.RankingQueryResult[domain.ProjectRankingEntry], error)
	GetUserStats(ctx context.Context, userID domain.UserID, term domain.Term) (*domain.IndividualStats, error)
	GetProjectStats(ctx context.Context, projectID domain.ProjectID, term domain.Term) (*domain.IndividualStats, error)
}

type BalanceChangeStore interface {
	GetUserBalanceChangeAfter(ctx context.Context, userID domain.UserID, after time.Time) (domain.BalanceChangeData, error)
	GetProjectBalanceChangeAfter(ctx context.Context, projectID domain.ProjectID, after time.Time) (domain.BalanceChangeData, error)
}

type UserRankingItem struct {
	Rank       int64
	Value      int64
	Difference int64
	User       domain.User
	Balance    int64
}

type ProjectRankingItem struct {
	Rank           int64
	Value          int64
	Difference     int64
	Project        domain.Project
	ProjectBalance int64
}

type UserRankingResult struct {
	Items      []UserRankingItem
	NextCursor *string
}

type ProjectRankingResult struct {
	Items      []ProjectRankingItem
	NextCursor *string
}

type StatsService struct {
	cache    StatsCacheStore
	users    UserStore
	projects ProjectStore
	changes  BalanceChangeStore
	economic gateway.Economic
}

func NewStatsService(cache StatsCacheStore, users UserStore, projects ProjectStore, changes BalanceChangeStore, economic gateway.Economic) *StatsService {
	return &StatsService{cache: cache, users: users, projects: projects, changes: changes, economic: economic}
}

func (s *StatsService) GetSystemStats(ctx context.Context, term domain.Term) (domain.SystemStats, error) {
	stats, err := s.cache.GetSystemStats(ctx, term)
	if err != nil {
		return domain.SystemStats{}, err
	}
	if stats == nil {
		return domain.SystemStats{}, app.NewError(app.CodeNotFound, "Stats not available yet")
	}
	return *stats, nil
}

func (s *StatsService) GetUsersAggregateStats(ctx context.Context, term domain.Term) (domain.AggregateStats, error) {
	stats, err := s.cache.GetUsersAggregateStats(ctx, term)
	if err != nil {
		return domain.AggregateStats{}, err
	}
	if stats == nil {
		return domain.AggregateStats{}, app.NewError(app.CodeNotFound, "Stats not available yet")
	}
	return *stats, nil
}

func (s *StatsService) GetProjectsAggregateStats(ctx context.Context, term domain.Term) (domain.AggregateStats, error) {
	stats, err := s.cache.GetProjectsAggregateStats(ctx, term)
	if err != nil {
		return domain.AggregateStats{}, err
	}
	if stats == nil {
		return domain.AggregateStats{}, app.NewError(app.CodeNotFound, "Stats not available yet")
	}
	return *stats, nil
}

func (s *StatsService) GetUserRankings(ctx context.Context, rankingType domain.RankingType, term domain.Term, ascending bool, limit int, cursor *string) (UserRankingResult, error) {
	result, err := s.cache.GetUserRankings(ctx, rankingType, term, ascending, limit, cursor)
	if err != nil {
		return UserRankingResult{}, err
	}
	userIDs := make([]domain.UserID, 0, len(result.Items))
	for _, item := range result.Items {
		userIDs = append(userIDs, item.UserID)
	}
	users, err := s.users.FindByIDs(ctx, userIDs)
	if err != nil {
		return UserRankingResult{}, err
	}
	userByID := make(map[domain.UserID]domain.User, len(users))
	accountIDs := make([]domain.AccountID, 0, len(users))
	for _, user := range users {
		userByID[user.ID] = user
		accountIDs = append(accountIDs, user.AccountID)
	}
	accounts, err := s.economic.FindAccountsByIDs(ctx, accountIDs)
	if err != nil {
		return UserRankingResult{}, err
	}
	accountByID := make(map[domain.AccountID]domain.Account, len(accounts))
	for _, account := range accounts {
		accountByID[account.ID] = account
	}
	items := make([]UserRankingItem, 0, len(result.Items))
	for _, entry := range result.Items {
		user, ok := userByID[entry.UserID]
		if !ok {
			continue
		}
		items = append(items, UserRankingItem{Rank: entry.Rank, Value: entry.Value, Difference: entry.Difference, User: user, Balance: accountByID[user.AccountID].Balance})
	}
	return UserRankingResult{Items: items, NextCursor: result.NextCursor}, nil
}

func (s *StatsService) GetProjectRankings(ctx context.Context, rankingType domain.RankingType, term domain.Term, ascending bool, limit int, cursor *string) (ProjectRankingResult, error) {
	result, err := s.cache.GetProjectRankings(ctx, rankingType, term, ascending, limit, cursor)
	if err != nil {
		return ProjectRankingResult{}, err
	}
	items := make([]ProjectRankingItem, 0, len(result.Items))
	for _, entry := range result.Items {
		project, err := s.projects.FindByID(ctx, entry.ProjectID)
		if err != nil {
			return ProjectRankingResult{}, err
		}
		if project == nil {
			continue
		}
		account, err := s.economic.FindAccountByID(ctx, project.AccountID)
		if err != nil {
			return ProjectRankingResult{}, err
		}
		var balance int64
		if account != nil {
			balance = account.Balance
		}
		items = append(items, ProjectRankingItem{Rank: entry.Rank, Value: entry.Value, Difference: entry.Difference, Project: *project, ProjectBalance: balance})
	}
	return ProjectRankingResult{Items: items, NextCursor: result.NextCursor}, nil
}

func (s *StatsService) GetUserStats(ctx context.Context, userID domain.UserID, term domain.Term) (domain.IndividualStats, error) {
	stats, err := s.cache.GetUserStats(ctx, userID, term)
	if err != nil {
		return domain.IndividualStats{}, err
	}
	if stats == nil {
		return domain.IndividualStats{}, app.NewError(app.CodeNotFound, "Stats not available for this user")
	}
	return *stats, nil
}

func (s *StatsService) GetProjectStats(ctx context.Context, projectID domain.ProjectID, term domain.Term) (domain.IndividualStats, error) {
	stats, err := s.cache.GetProjectStats(ctx, projectID, term)
	if err != nil {
		return domain.IndividualStats{}, err
	}
	if stats == nil {
		return domain.IndividualStats{}, app.NewError(app.CodeNotFound, "Stats not available for this project")
	}
	return *stats, nil
}

func (s *StatsService) GetUserBalanceAt(ctx context.Context, userID domain.UserID, at time.Time) (int64, error) {
	user, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return 0, err
	}
	if user == nil {
		return 0, app.NewError(app.CodeNotFound, "User not found")
	}
	account, err := s.economic.FindAccountByID(ctx, user.AccountID)
	if err != nil {
		return 0, err
	}
	if account == nil {
		return 0, app.NewError(app.CodeNotFound, "User not found")
	}
	change, err := s.changes.GetUserBalanceChangeAfter(ctx, userID, at)
	if err != nil {
		return 0, err
	}
	return account.Balance - change.NetChange(), nil
}

func (s *StatsService) GetProjectBalanceAt(ctx context.Context, projectID domain.ProjectID, at time.Time) (int64, error) {
	project, err := s.projects.FindByID(ctx, projectID)
	if err != nil {
		return 0, err
	}
	if project == nil {
		return 0, app.NewError(app.CodeNotFound, "Project not found")
	}
	account, err := s.economic.FindAccountByID(ctx, project.AccountID)
	if err != nil {
		return 0, err
	}
	if account == nil {
		return 0, app.NewError(app.CodeNotFound, "Project not found")
	}
	change, err := s.changes.GetProjectBalanceChangeAfter(ctx, projectID, at)
	if err != nil {
		return 0, err
	}
	return account.Balance - change.NetChange(), nil
}
