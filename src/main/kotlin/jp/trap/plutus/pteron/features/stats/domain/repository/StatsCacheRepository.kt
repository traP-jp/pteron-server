package jp.trap.plutus.pteron.features.stats.domain.repository

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.stats.domain.model.*

/**
 * ランキングクエリ結果
 */
data class RankingQueryResult<T>(
    val items: List<T>,
    val nextCursor: String?,
)

/**
 * 統計キャッシュリポジトリ
 */
interface StatsCacheRepository {
    // --- Read operations (for API) ---

    suspend fun getSystemStats(term: StatsTerm): SystemStats?

    suspend fun getUsersStats(term: StatsTerm): UsersStats?

    suspend fun getProjectsStats(term: StatsTerm): ProjectsStats?

    suspend fun getUserRankings(
        term: StatsTerm,
        rankingType: RankingType,
        ascending: Boolean = false,
        limit: Int = 20,
        cursor: String? = null,
    ): RankingQueryResult<UserRankingEntry>

    suspend fun getProjectRankings(
        term: StatsTerm,
        rankingType: RankingType,
        ascending: Boolean = false,
        limit: Int = 20,
        cursor: String? = null,
    ): RankingQueryResult<ProjectRankingEntry>

    suspend fun getUserRankingEntry(
        term: StatsTerm,
        rankingType: RankingType,
        userId: UserId,
    ): UserRankingEntry?

    suspend fun getProjectRankingEntry(
        term: StatsTerm,
        rankingType: RankingType,
        projectId: ProjectId,
    ): ProjectRankingEntry?

    // --- Write operations (for background job) ---

    suspend fun saveSystemStats(stats: SystemStats)

    suspend fun saveUsersStats(stats: UsersStats)

    suspend fun saveProjectsStats(stats: ProjectsStats)

    suspend fun saveUserRankings(entries: List<UserRankingEntry>)

    suspend fun saveProjectRankings(entries: List<ProjectRankingEntry>)

    suspend fun clearUserRankings(
        term: StatsTerm,
        rankingType: RankingType,
    )

    suspend fun clearProjectRankings(
        term: StatsTerm,
        rankingType: RankingType,
    )
}
