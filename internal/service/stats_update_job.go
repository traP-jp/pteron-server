package service

import (
	"context"
	"log/slog"
	"sort"
	"time"

	"github.com/traP-jp/pteron-server/internal/domain"
)

type StatsCacheWriter interface {
	SaveSystemStats(ctx context.Context, term domain.Term, stats domain.SystemStats) error
	SaveUsersAggregateStats(ctx context.Context, term domain.Term, stats domain.AggregateStats) error
	SaveProjectsAggregateStats(ctx context.Context, term domain.Term, stats domain.AggregateStats) error
	ClearUserRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType) error
	ClearProjectRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType) error
	SaveUserRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType, entries []domain.UserRankingEntry) error
	SaveProjectRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType, entries []domain.ProjectRankingEntry) error
}

type StatsTransactionStore interface {
	GetStats(ctx context.Context, since time.Time) (domain.TransactionStatsData, error)
	GetUsersStats(ctx context.Context, since time.Time) (domain.TransactionStatsData, error)
	GetProjectsStats(ctx context.Context, since time.Time) (domain.TransactionStatsData, error)
	GetUserStats(ctx context.Context, userID domain.UserID, since time.Time, until time.Time) (domain.TransactionStatsData, error)
	GetProjectStats(ctx context.Context, projectID domain.ProjectID, since time.Time, until time.Time) (domain.TransactionStatsData, error)
	GetUserBalanceChangeAfter(ctx context.Context, userID domain.UserID, after time.Time) (domain.BalanceChangeData, error)
	GetProjectBalanceChangeAfter(ctx context.Context, projectID domain.ProjectID, after time.Time) (domain.BalanceChangeData, error)
}

type StatsUpdateJob struct {
	cache interface {
		StatsCacheWriter
	}
	transactions StatsTransactionStore
	users        UserStore
	projects     ProjectStore
	accounts     *AccountService
	logger       *slog.Logger
}

type userStatsData struct {
	UserID     domain.UserID
	Balance    int64
	Difference int64
	InAmount   int64
	OutAmount  int64
	Count      int64
	Total      int64
	Ratio      int64
}

type projectStatsData struct {
	ProjectID  domain.ProjectID
	Balance    int64
	Difference int64
	InAmount   int64
	OutAmount  int64
	Count      int64
	Total      int64
	Ratio      int64
}

func NewStatsUpdateJob(cache StatsCacheWriter, transactions StatsTransactionStore, users UserStore, projects ProjectStore, accounts *AccountService, logger *slog.Logger) *StatsUpdateJob {
	return &StatsUpdateJob{
		cache:        cache,
		transactions: transactions,
		users:        users,
		projects:     projects,
		accounts:     accounts,
		logger:       logger,
	}
}

func (j *StatsUpdateJob) Start(ctx context.Context) {
	go func() {
		timer := time.NewTimer(10 * time.Second)
		defer timer.Stop()
		select {
		case <-ctx.Done():
			return
		case <-timer.C:
		}

		ticker := time.NewTicker(time.Minute)
		defer ticker.Stop()
		for {
			if err := j.UpdateAllStats(ctx); err != nil {
				j.logger.Error("failed to update stats cache", "error", err)
			}
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
			}
		}
	}()
}

func (j *StatsUpdateJob) UpdateAllStats(ctx context.Context) error {
	j.logger.Info("starting stats cache update")
	start := time.Now()

	users, err := j.users.FindAll(ctx)
	if err != nil {
		return err
	}
	projects, err := j.projects.FindAll(ctx)
	if err != nil {
		return err
	}
	accountIDs := make([]domain.AccountID, 0, len(users)+len(projects))
	for _, user := range users {
		accountIDs = append(accountIDs, user.AccountID)
	}
	for _, project := range projects {
		accountIDs = append(accountIDs, project.AccountID)
	}
	accounts, err := j.accounts.GetAccountsByIDs(ctx, uniqueComparable(accountIDs))
	if err != nil {
		return err
	}
	accountByID := make(map[domain.AccountID]domain.Account, len(accounts))
	for _, account := range accounts {
		accountByID[account.ID] = account
	}

	for _, term := range domain.Terms() {
		if err := j.updateTerm(ctx, term, users, projects, accountByID); err != nil {
			j.logger.Error("failed to update stats term", "term", term, "error", err)
		}
	}
	j.logger.Info("stats cache update completed", "elapsed_ms", time.Since(start).Milliseconds())
	return nil
}

func (j *StatsUpdateJob) updateTerm(ctx context.Context, term domain.Term, users []domain.User, projects []domain.Project, accountByID map[domain.AccountID]domain.Account) error {
	now := time.Now().UTC()
	since := now.Add(-time.Duration(term.Hours()) * time.Hour)

	allBalance := int64(0)
	for _, user := range users {
		allBalance += accountByID[user.AccountID].Balance
	}
	for _, project := range projects {
		allBalance += accountByID[project.AccountID].Balance
	}
	systemStats, err := j.transactions.GetStats(ctx, since)
	if err != nil {
		return err
	}
	if err := j.cache.SaveSystemStats(ctx, term, domain.SystemStats{
		Balance: allBalance, Difference: systemStats.NetChange, Count: systemStats.Count, Total: systemStats.Total, Ratio: ratio(systemStats.NetChange, allBalance-systemStats.NetChange),
	}); err != nil {
		return err
	}

	userBalance := int64(0)
	for _, user := range users {
		userBalance += accountByID[user.AccountID].Balance
	}
	userStats, err := j.transactions.GetUsersStats(ctx, since)
	if err != nil {
		return err
	}
	if err := j.cache.SaveUsersAggregateStats(ctx, term, domain.AggregateStats{
		Number: int64(len(users)), Balance: userBalance, Difference: userStats.NetChange, Count: userStats.Count, Total: userStats.Total, Ratio: ratio(userStats.NetChange, userBalance-userStats.NetChange),
	}); err != nil {
		return err
	}

	projectBalance := int64(0)
	for _, project := range projects {
		projectBalance += accountByID[project.AccountID].Balance
	}
	projectStats, err := j.transactions.GetProjectsStats(ctx, since)
	if err != nil {
		return err
	}
	if err := j.cache.SaveProjectsAggregateStats(ctx, term, domain.AggregateStats{
		Number: int64(len(projects)), Balance: projectBalance, Difference: projectStats.NetChange, Count: projectStats.Count, Total: projectStats.Total, Ratio: ratio(projectStats.NetChange, projectBalance-projectStats.NetChange),
	}); err != nil {
		return err
	}

	currentUsers, err := j.calculateAllUserRankings(ctx, term, users, accountByID, false)
	if err != nil {
		return err
	}
	previousUsers, err := j.calculateAllUserRankings(ctx, term, users, accountByID, true)
	if err != nil {
		return err
	}
	currentProjects, err := j.calculateAllProjectRankings(ctx, term, projects, accountByID, false)
	if err != nil {
		return err
	}
	previousProjects, err := j.calculateAllProjectRankings(ctx, term, projects, accountByID, true)
	if err != nil {
		return err
	}

	for _, rankingType := range domain.RankingTypes() {
		if err := j.updateUserRankings(ctx, term, rankingType, currentUsers, previousUsers); err != nil {
			return err
		}
		if err := j.updateProjectRankings(ctx, term, rankingType, currentProjects, previousProjects); err != nil {
			return err
		}
	}
	return nil
}

func (j *StatsUpdateJob) calculateAllUserRankings(ctx context.Context, term domain.Term, users []domain.User, accountByID map[domain.AccountID]domain.Account, previous bool) ([]userStatsData, error) {
	now := time.Now().UTC()
	offset := time.Duration(0)
	if previous {
		offset = time.Duration(term.Hours()) * time.Hour
	}
	since := now.Add(-(time.Duration(term.Hours())*time.Hour + offset))
	until := now.Add(-offset)
	result := make([]userStatsData, 0, len(users))
	for _, user := range users {
		balanceNow := accountByID[user.AccountID].Balance
		stats, err := j.transactions.GetUserStats(ctx, user.ID, since, until)
		if err != nil {
			return nil, err
		}
		balanceAtEnd := balanceNow
		if previous {
			change, err := j.transactions.GetUserBalanceChangeAfter(ctx, user.ID, until)
			if err != nil {
				return nil, err
			}
			balanceAtEnd = balanceNow - change.NetChange()
		}
		result = append(result, userStatsData{
			UserID: user.ID, Balance: balanceAtEnd, Difference: stats.NetChange, InAmount: stats.InAmount, OutAmount: stats.OutAmount, Count: stats.Count, Total: stats.Total, Ratio: ratio(stats.NetChange, balanceAtEnd-stats.NetChange),
		})
	}
	return result, nil
}

func (j *StatsUpdateJob) calculateAllProjectRankings(ctx context.Context, term domain.Term, projects []domain.Project, accountByID map[domain.AccountID]domain.Account, previous bool) ([]projectStatsData, error) {
	now := time.Now().UTC()
	offset := time.Duration(0)
	if previous {
		offset = time.Duration(term.Hours()) * time.Hour
	}
	since := now.Add(-(time.Duration(term.Hours())*time.Hour + offset))
	until := now.Add(-offset)
	result := make([]projectStatsData, 0, len(projects))
	for _, project := range projects {
		balanceNow := accountByID[project.AccountID].Balance
		stats, err := j.transactions.GetProjectStats(ctx, project.ID, since, until)
		if err != nil {
			return nil, err
		}
		balanceAtEnd := balanceNow
		if previous {
			change, err := j.transactions.GetProjectBalanceChangeAfter(ctx, project.ID, until)
			if err != nil {
				return nil, err
			}
			balanceAtEnd = balanceNow - change.NetChange()
		}
		result = append(result, projectStatsData{
			ProjectID: project.ID, Balance: balanceAtEnd, Difference: stats.NetChange, InAmount: stats.InAmount, OutAmount: stats.OutAmount, Count: stats.Count, Total: stats.Total, Ratio: ratio(stats.NetChange, balanceAtEnd-stats.NetChange),
		})
	}
	return result, nil
}

func (j *StatsUpdateJob) updateUserRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType, current []userStatsData, previous []userStatsData) error {
	currentRanks := ranks(current, func(data userStatsData) domain.ID { return data.UserID }, func(data userStatsData) int64 { return userRankingValue(data, rankingType) })
	previousRanks := ranks(previous, func(data userStatsData) domain.ID { return data.UserID }, func(data userStatsData) int64 { return userRankingValue(data, rankingType) })
	entries := make([]domain.UserRankingEntry, 0, len(current))
	for _, data := range current {
		currentRank := currentRanks[data.UserID]
		previousRank := previousRanks[data.UserID]
		if previousRank == 0 {
			previousRank = currentRank
		}
		entries = append(entries, domain.UserRankingEntry{Rank: currentRank, Value: userRankingValue(data, rankingType), Difference: previousRank - currentRank, UserID: data.UserID})
	}
	if err := j.cache.ClearUserRankings(ctx, term, rankingType); err != nil {
		return err
	}
	return j.cache.SaveUserRankings(ctx, term, rankingType, entries)
}

func (j *StatsUpdateJob) updateProjectRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType, current []projectStatsData, previous []projectStatsData) error {
	currentRanks := ranks(current, func(data projectStatsData) domain.ID { return data.ProjectID }, func(data projectStatsData) int64 { return projectRankingValue(data, rankingType) })
	previousRanks := ranks(previous, func(data projectStatsData) domain.ID { return data.ProjectID }, func(data projectStatsData) int64 { return projectRankingValue(data, rankingType) })
	entries := make([]domain.ProjectRankingEntry, 0, len(current))
	for _, data := range current {
		currentRank := currentRanks[data.ProjectID]
		previousRank := previousRanks[data.ProjectID]
		if previousRank == 0 {
			previousRank = currentRank
		}
		entries = append(entries, domain.ProjectRankingEntry{Rank: currentRank, Value: projectRankingValue(data, rankingType), Difference: previousRank - currentRank, ProjectID: data.ProjectID})
	}
	if err := j.cache.ClearProjectRankings(ctx, term, rankingType); err != nil {
		return err
	}
	return j.cache.SaveProjectRankings(ctx, term, rankingType, entries)
}

func ranks[T any](items []T, id func(T) domain.ID, value func(T) int64) map[domain.ID]int64 {
	sorted := append([]T(nil), items...)
	sort.SliceStable(sorted, func(i, j int) bool { return value(sorted[i]) > value(sorted[j]) })
	result := make(map[domain.ID]int64, len(sorted))
	for i, item := range sorted {
		if i > 0 && value(item) == value(sorted[i-1]) {
			result[id(item)] = result[id(sorted[i-1])]
		} else {
			result[id(item)] = int64(i + 1)
		}
	}
	return result
}

func userRankingValue(data userStatsData, rankingType domain.RankingType) int64 {
	switch rankingType {
	case domain.RankingBalance:
		return data.Balance
	case domain.RankingDifference:
		return data.Difference
	case domain.RankingIn:
		return data.InAmount
	case domain.RankingOut:
		return data.OutAmount
	case domain.RankingCount:
		return data.Count
	case domain.RankingTotal:
		return data.Total
	case domain.RankingRatio:
		return data.Ratio
	default:
		return 0
	}
}

func projectRankingValue(data projectStatsData, rankingType domain.RankingType) int64 {
	switch rankingType {
	case domain.RankingBalance:
		return data.Balance
	case domain.RankingDifference:
		return data.Difference
	case domain.RankingIn:
		return data.InAmount
	case domain.RankingOut:
		return data.OutAmount
	case domain.RankingCount:
		return data.Count
	case domain.RankingTotal:
		return data.Total
	case domain.RankingRatio:
		return data.Ratio
	default:
		return 0
	}
}

func ratio(difference int64, previousBalance int64) int64 {
	if previousBalance == 0 {
		return 0
	}
	return int64((float64(difference) / float64(previousBalance)) * 100)
}

func uniqueComparable[T comparable](values []T) []T {
	seen := make(map[T]struct{}, len(values))
	out := make([]T, 0, len(values))
	for _, value := range values {
		if _, ok := seen[value]; ok {
			continue
		}
		seen[value] = struct{}{}
		out = append(out, value)
	}
	return out
}
