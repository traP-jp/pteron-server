package jp.trap.plutus.pteron.features.stats.service

import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.exception.NotFoundException
import jp.trap.plutus.pteron.features.stats.domain.model.*
import jp.trap.plutus.pteron.features.stats.domain.repository.RankingQueryResult
import jp.trap.plutus.pteron.features.stats.domain.repository.StatsCacheRepository
import org.koin.core.annotation.Single

/**
 * 統計サービス - キャッシュからの読み取り専用
 */
@Single
class StatsService(
    private val statsCacheRepository: StatsCacheRepository,
    private val unitOfWork: UnitOfWork,
) {
    /**
     * システム全体の統計を取得
     */
    suspend fun getSystemStats(term: StatsTerm): SystemStats =
        unitOfWork.runInTransaction {
            statsCacheRepository.getSystemStats(term)
        } ?: throw NotFoundException("System stats not found for term: ${term.value}")

    /**
     * ユーザー関連の統計を取得
     */
    suspend fun getUsersStats(term: StatsTerm): UsersStats =
        unitOfWork.runInTransaction {
            statsCacheRepository.getUsersStats(term)
        } ?: throw NotFoundException("Users stats not found for term: ${term.value}")

    /**
     * プロジェクト関連の統計を取得
     */
    suspend fun getProjectsStats(term: StatsTerm): ProjectsStats =
        unitOfWork.runInTransaction {
            statsCacheRepository.getProjectsStats(term)
        } ?: throw NotFoundException("Projects stats not found for term: ${term.value}")

    /**
     * ユーザーランキングを取得
     */
    suspend fun getUserRankings(
        rankingType: RankingType,
        term: StatsTerm,
        ascending: Boolean = false,
        limit: Int = 20,
        cursor: String? = null,
    ): RankingQueryResult<UserRankingEntry> =
        unitOfWork.runInTransaction {
            statsCacheRepository.getUserRankings(term, rankingType, ascending, limit, cursor)
        }

    /**
     * プロジェクトランキングを取得
     */
    suspend fun getProjectRankings(
        rankingType: RankingType,
        term: StatsTerm,
        ascending: Boolean = false,
        limit: Int = 20,
        cursor: String? = null,
    ): RankingQueryResult<ProjectRankingEntry> =
        unitOfWork.runInTransaction {
            statsCacheRepository.getProjectRankings(term, rankingType, ascending, limit, cursor)
        }

    /**
     * 特定ユーザーの全ランキング情報を取得
     */
    suspend fun getUserStats(
        userId: UserId,
        term: StatsTerm,
    ): Map<RankingType, UserRankingEntry> =
        unitOfWork.runInTransaction {
            RankingType.entries
                .mapNotNull { rankingType ->
                    statsCacheRepository.getUserRankingEntry(term, rankingType, userId)?.let {
                        rankingType to it
                    }
                }.toMap()
        }

    /**
     * 特定プロジェクトの全ランキング情報を取得
     */
    suspend fun getProjectStats(
        projectId: ProjectId,
        term: StatsTerm,
    ): Map<RankingType, ProjectRankingEntry> =
        unitOfWork.runInTransaction {
            RankingType.entries
                .mapNotNull { rankingType ->
                    statsCacheRepository.getProjectRankingEntry(term, rankingType, projectId)?.let {
                        rankingType to it
                    }
                }.toMap()
        }

    /**
     * 特定ユーザーの順位を取得
     */
    suspend fun getUserRank(
        userId: UserId,
        term: StatsTerm,
        rankingType: RankingType,
        ascending: Boolean = false,
    ): Long? =
        unitOfWork.runInTransaction {
            statsCacheRepository.getUserRank(term, rankingType, userId, ascending)
        }

    /**
     * 特定プロジェクトの順位を取得
     */
    suspend fun getProjectRank(
        projectId: ProjectId,
        term: StatsTerm,
        rankingType: RankingType,
        ascending: Boolean = false,
    ): Long? =
        unitOfWork.runInTransaction {
            statsCacheRepository.getProjectRank(term, rankingType, projectId, ascending)
        }
}
