package jp.trap.plutus.pteron.features.stats.domain.model

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId

/**
 * ユーザーランキングエントリ
 */
data class UserRankingEntry(
    val rank: Long,
    val value: Long,
    val difference: Long,
    val userId: UserId,
)

/**
 * プロジェクトランキングエントリ
 */
data class ProjectRankingEntry(
    val rank: Long,
    val value: Long,
    val difference: Long,
    val projectId: ProjectId,
)

/**
 * ランキングクエリ結果
 */
data class RankingQueryResult<T>(
    val items: List<T>,
    val nextCursor: String?,
)
