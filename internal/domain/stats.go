package domain

type Term string

const (
	Term24Hours Term = "24hours"
	Term7Days   Term = "7days"
	Term30Days  Term = "30days"
	Term365Days Term = "365days"
)

type RankingType string

const (
	RankingBalance    RankingType = "balance"
	RankingDifference RankingType = "difference"
	RankingIn         RankingType = "in"
	RankingOut        RankingType = "out"
	RankingCount      RankingType = "count"
	RankingTotal      RankingType = "total"
	RankingRatio      RankingType = "ratio"
)

type SystemStats struct {
	Balance    int64
	Difference int64
	Count      int64
	Total      int64
	Ratio      int64
}

type AggregateStats struct {
	Number     int64
	Balance    int64
	Difference int64
	Count      int64
	Total      int64
	Ratio      int64
}

type RankingPosition struct {
	Rank       int64
	Value      int64
	Difference int64
}

type IndividualStats struct {
	Balance    RankingPosition
	Difference RankingPosition
	InAmount   RankingPosition
	OutAmount  RankingPosition
	Count      RankingPosition
	Total      RankingPosition
	Ratio      RankingPosition
}

type UserRankingEntry struct {
	Rank       int64
	Value      int64
	Difference int64
	UserID     UserID
}

type ProjectRankingEntry struct {
	Rank       int64
	Value      int64
	Difference int64
	ProjectID  ProjectID
}

type TransactionStatsData struct {
	Count     int64
	Total     int64
	NetChange int64
	InAmount  int64
	OutAmount int64
}

func (t Term) Hours() int {
	switch t {
	case Term24Hours:
		return 24
	case Term7Days:
		return 24 * 7
	case Term30Days:
		return 24 * 30
	case Term365Days:
		return 24 * 365
	default:
		return 24
	}
}

func Terms() []Term {
	return []Term{Term24Hours, Term7Days, Term30Days, Term365Days}
}

func RankingTypes() []RankingType {
	return []RankingType{RankingBalance, RankingDifference, RankingIn, RankingOut, RankingCount, RankingTotal, RankingRatio}
}
