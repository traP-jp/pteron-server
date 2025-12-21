package jp.trap.plutus.pteron.features.stats.domain.repository

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.stats.domain.model.*
import kotlin.time.Instant

/**
 * 統計キャッシュリポジトリ（読み書き両方）
 */
interface StatsCacheRepository {
    // === 読み取り ===

    suspend fun getSystemStats(term: Term): SystemStats?

    suspend fun getUsersAggregateStats(term: Term): AggregateStats?

    suspend fun getProjectsAggregateStats(term: Term): AggregateStats?

    suspend fun getUserRankings(
        rankingType: RankingType,
        term: Term,
        ascending: Boolean = false,
        limit: Int = 20,
        cursor: String? = null,
    ): RankingQueryResult<UserRankingEntry>

    suspend fun getProjectRankings(
        rankingType: RankingType,
        term: Term,
        ascending: Boolean = false,
        limit: Int = 20,
        cursor: String? = null,
    ): RankingQueryResult<ProjectRankingEntry>

    suspend fun getUserStats(
        userId: UserId,
        term: Term,
    ): IndividualStats?

    suspend fun getProjectStats(
        projectId: ProjectId,
        term: Term,
    ): IndividualStats?

    // === 特定時点の残高（都度計算） ===

    suspend fun getUserBalanceAt(
        userId: UserId,
        at: Instant,
    ): Long?

    suspend fun getProjectBalanceAt(
        projectId: ProjectId,
        at: Instant,
    ): Long?

    // === 書き込み ===

    suspend fun saveSystemStats(
        term: Term,
        stats: SystemStats,
    )

    suspend fun saveUsersAggregateStats(
        term: Term,
        stats: AggregateStats,
    )

    suspend fun saveProjectsAggregateStats(
        term: Term,
        stats: AggregateStats,
    )

    suspend fun saveUserRankings(
        term: Term,
        rankingType: RankingType,
        entries: List<UserRankingEntry>,
    )

    suspend fun saveProjectRankings(
        term: Term,
        rankingType: RankingType,
        entries: List<ProjectRankingEntry>,
    )

    suspend fun clearUserRankings(
        term: Term,
        rankingType: RankingType,
    )

    suspend fun clearProjectRankings(
        term: Term,
        rankingType: RankingType,
    )
}
