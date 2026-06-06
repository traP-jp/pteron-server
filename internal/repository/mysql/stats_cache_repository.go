package mysql

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/jmoiron/sqlx"

	"github.com/traP-jp/pteron-server/internal/domain"
)

func (r *StatsCacheRepository) SaveSystemStats(ctx context.Context, term domain.Term, stats domain.SystemStats) error {
	_, err := r.db.ExecContext(ctx, `
INSERT INTO stats_cache_system (term, balance, difference, count, total, ratio, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    balance = VALUES(balance),
    difference = VALUES(difference),
    count = VALUES(count),
    total = VALUES(total),
    ratio = VALUES(ratio),
    updated_at = VALUES(updated_at)`,
		string(term), stats.Balance, stats.Difference, stats.Count, stats.Total, stats.Ratio, time.Now().UTC(),
	)
	return err
}

func (r *StatsCacheRepository) SaveUsersAggregateStats(ctx context.Context, term domain.Term, stats domain.AggregateStats) error {
	return r.saveAggregateStats(ctx, "stats_cache_users_aggregate", term, stats)
}

func (r *StatsCacheRepository) SaveProjectsAggregateStats(ctx context.Context, term domain.Term, stats domain.AggregateStats) error {
	return r.saveAggregateStats(ctx, "stats_cache_projects_aggregate", term, stats)
}

func (r *StatsCacheRepository) saveAggregateStats(ctx context.Context, table string, term domain.Term, stats domain.AggregateStats) error {
	_, err := r.db.ExecContext(ctx, `
INSERT INTO `+table+` (term, number, balance, difference, count, total, ratio, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    number = VALUES(number),
    balance = VALUES(balance),
    difference = VALUES(difference),
    count = VALUES(count),
    total = VALUES(total),
    ratio = VALUES(ratio),
    updated_at = VALUES(updated_at)`,
		string(term), stats.Number, stats.Balance, stats.Difference, stats.Count, stats.Total, stats.Ratio, time.Now().UTC(),
	)
	return err
}

func (r *StatsCacheRepository) ClearUserRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM stats_cache_user_rankings WHERE term = ? AND ranking_type = ?", string(term), string(rankingType))
	return err
}

func (r *StatsCacheRepository) ClearProjectRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM stats_cache_project_rankings WHERE term = ? AND ranking_type = ?", string(term), string(rankingType))
	return err
}

func (r *StatsCacheRepository) SaveUserRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType, entries []domain.UserRankingEntry) error {
	for _, entry := range entries {
		if _, err := r.db.ExecContext(ctx, `
INSERT INTO stats_cache_user_rankings (term, ranking_type, user_id, `+"`rank`"+`, value, difference)
VALUES (?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    `+"`rank`"+` = VALUES(`+"`rank`"+`),
    value = VALUES(value),
    difference = VALUES(difference)`,
			string(term), string(rankingType), entry.UserID.Bytes(), entry.Rank, entry.Value, entry.Difference,
		); err != nil {
			return err
		}
	}
	return nil
}

func (r *StatsCacheRepository) SaveProjectRankings(ctx context.Context, term domain.Term, rankingType domain.RankingType, entries []domain.ProjectRankingEntry) error {
	for _, entry := range entries {
		if _, err := r.db.ExecContext(ctx, `
INSERT INTO stats_cache_project_rankings (term, ranking_type, project_id, `+"`rank`"+`, value, difference)
VALUES (?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
    `+"`rank`"+` = VALUES(`+"`rank`"+`),
    value = VALUES(value),
    difference = VALUES(difference)`,
			string(term), string(rankingType), entry.ProjectID.Bytes(), entry.Rank, entry.Value, entry.Difference,
		); err != nil {
			return err
		}
	}
	return nil
}

type StatsCacheRepository struct {
	db *sqlx.DB
}

func NewStatsCacheRepository(db *sqlx.DB) *StatsCacheRepository {
	return &StatsCacheRepository{db: db}
}

func (r *StatsCacheRepository) GetSystemStats(ctx context.Context, term domain.Term) (*domain.SystemStats, error) {
	var row systemStatsRow
	if err := r.db.GetContext(ctx, &row, "SELECT balance, difference, count, total, ratio FROM stats_cache_system WHERE term = ?", string(term)); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &domain.SystemStats{Balance: row.Balance, Difference: row.Difference, Count: row.Count, Total: row.Total, Ratio: row.Ratio}, nil
}

func (r *StatsCacheRepository) GetUsersAggregateStats(ctx context.Context, term domain.Term) (*domain.AggregateStats, error) {
	return r.getAggregateStats(ctx, "stats_cache_users_aggregate", term)
}

func (r *StatsCacheRepository) GetProjectsAggregateStats(ctx context.Context, term domain.Term) (*domain.AggregateStats, error) {
	return r.getAggregateStats(ctx, "stats_cache_projects_aggregate", term)
}

func (r *StatsCacheRepository) getAggregateStats(ctx context.Context, table string, term domain.Term) (*domain.AggregateStats, error) {
	var row aggregateStatsRow
	if err := r.db.GetContext(ctx, &row, "SELECT number, balance, difference, count, total, ratio FROM "+table+" WHERE term = ?", string(term)); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return &domain.AggregateStats{Number: row.Number, Balance: row.Balance, Difference: row.Difference, Count: row.Count, Total: row.Total, Ratio: row.Ratio}, nil
}

func (r *StatsCacheRepository) GetUserRankings(ctx context.Context, rankingType domain.RankingType, term domain.Term, ascending bool, limit int, cursor *string) (domain.RankingQueryResult[domain.UserRankingEntry], error) {
	rows, nextCursor, err := r.queryRankings(ctx, "stats_cache_user_rankings", "user_id", rankingType, term, ascending, limit, cursor)
	if err != nil {
		return domain.RankingQueryResult[domain.UserRankingEntry]{}, err
	}
	items := make([]domain.UserRankingEntry, 0, len(rows))
	for _, row := range rows {
		id, err := domain.IDFromBytes(row.TargetID)
		if err != nil {
			return domain.RankingQueryResult[domain.UserRankingEntry]{}, err
		}
		items = append(items, domain.UserRankingEntry{Rank: row.Rank, Value: row.Value, Difference: row.Difference, UserID: domain.UserID(id)})
	}
	return domain.RankingQueryResult[domain.UserRankingEntry]{Items: items, NextCursor: nextCursor}, nil
}

func (r *StatsCacheRepository) GetProjectRankings(ctx context.Context, rankingType domain.RankingType, term domain.Term, ascending bool, limit int, cursor *string) (domain.RankingQueryResult[domain.ProjectRankingEntry], error) {
	rows, nextCursor, err := r.queryRankings(ctx, "stats_cache_project_rankings", "project_id", rankingType, term, ascending, limit, cursor)
	if err != nil {
		return domain.RankingQueryResult[domain.ProjectRankingEntry]{}, err
	}
	items := make([]domain.ProjectRankingEntry, 0, len(rows))
	for _, row := range rows {
		id, err := domain.IDFromBytes(row.TargetID)
		if err != nil {
			return domain.RankingQueryResult[domain.ProjectRankingEntry]{}, err
		}
		items = append(items, domain.ProjectRankingEntry{Rank: row.Rank, Value: row.Value, Difference: row.Difference, ProjectID: domain.ProjectID(id)})
	}
	return domain.RankingQueryResult[domain.ProjectRankingEntry]{Items: items, NextCursor: nextCursor}, nil
}

func (r *StatsCacheRepository) queryRankings(ctx context.Context, table string, idColumn string, rankingType domain.RankingType, term domain.Term, ascending bool, limit int, cursor *string) ([]rankingRow, *string, error) {
	args := []any{string(term), string(rankingType)}
	where := "term = ? AND ranking_type = ?"
	if cursor != nil && *cursor != "" {
		rank, err := decodeRankingCursor(*cursor)
		if err == nil {
			where += " AND `rank` > ?"
			args = append(args, rank)
		}
	}
	order := "ASC"
	if ascending {
		order = "DESC"
	}
	query := "SELECT `rank`, value, difference, " + idColumn + " AS target_id FROM " + table + " WHERE " + where + " ORDER BY `rank` " + order + " LIMIT ?"
	args = append(args, limit+1)
	var rows []rankingRow
	if err := r.db.SelectContext(ctx, &rows, query, args...); err != nil {
		return nil, nil, err
	}
	var nextCursor *string
	if len(rows) > limit {
		rows = rows[:limit]
		last := rows[len(rows)-1]
		id, err := domain.IDFromBytes(last.TargetID)
		if err != nil {
			return nil, nil, err
		}
		value := encodeRankingCursor(last.Rank, id)
		nextCursor = &value
	}
	return rows, nextCursor, nil
}

func (r *StatsCacheRepository) GetUserStats(ctx context.Context, userID domain.UserID, term domain.Term) (*domain.IndividualStats, error) {
	return r.getIndividualStats(ctx, "stats_cache_user_rankings", "user_id", userID.Bytes(), term)
}

func (r *StatsCacheRepository) GetProjectStats(ctx context.Context, projectID domain.ProjectID, term domain.Term) (*domain.IndividualStats, error) {
	return r.getIndividualStats(ctx, "stats_cache_project_rankings", "project_id", projectID.Bytes(), term)
}

func (r *StatsCacheRepository) getIndividualStats(ctx context.Context, table string, idColumn string, id []byte, term domain.Term) (*domain.IndividualStats, error) {
	query := "SELECT ranking_type, `rank`, value, difference FROM " + table + " WHERE term = ? AND " + idColumn + " = ?"
	var rows []individualStatsRow
	if err := r.db.SelectContext(ctx, &rows, query, string(term), id); err != nil {
		return nil, err
	}
	if len(rows) < 7 {
		return nil, nil
	}
	byType := make(map[domain.RankingType]domain.RankingPosition, len(rows))
	for _, row := range rows {
		byType[domain.RankingType(row.RankingType)] = domain.RankingPosition{Rank: row.Rank, Value: row.Value, Difference: row.Difference}
	}
	return &domain.IndividualStats{
		Balance:    byType[domain.RankingBalance],
		Difference: byType[domain.RankingDifference],
		InAmount:   byType[domain.RankingIn],
		OutAmount:  byType[domain.RankingOut],
		Count:      byType[domain.RankingCount],
		Total:      byType[domain.RankingTotal],
		Ratio:      byType[domain.RankingRatio],
	}, nil
}

type systemStatsRow struct {
	Balance    int64 `db:"balance"`
	Difference int64 `db:"difference"`
	Count      int64 `db:"count"`
	Total      int64 `db:"total"`
	Ratio      int64 `db:"ratio"`
}

type aggregateStatsRow struct {
	Number     int64 `db:"number"`
	Balance    int64 `db:"balance"`
	Difference int64 `db:"difference"`
	Count      int64 `db:"count"`
	Total      int64 `db:"total"`
	Ratio      int64 `db:"ratio"`
}

type rankingRow struct {
	Rank       int64  `db:"rank"`
	Value      int64  `db:"value"`
	Difference int64  `db:"difference"`
	TargetID   []byte `db:"target_id"`
}

type individualStatsRow struct {
	RankingType string `db:"ranking_type"`
	Rank        int64  `db:"rank"`
	Value       int64  `db:"value"`
	Difference  int64  `db:"difference"`
}
