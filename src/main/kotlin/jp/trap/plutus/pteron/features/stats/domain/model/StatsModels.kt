package jp.trap.plutus.pteron.features.stats.domain.model

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import kotlin.time.Instant

/**
 * 統計期間
 */
enum class StatsTerm(
    val value: String,
) {
    HOURS_24("24hours"),
    DAYS_7("7days"),
    DAYS_30("30days"),
    DAYS_365("365days"),
    ;

    companion object {
        fun fromString(value: String): StatsTerm =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid term: $value")
    }
}

/**
 * ランキングタイプ
 */
enum class RankingType(
    val value: String,
) {
    BALANCE("balance"),
    DIFFERENCE("difference"),
    IN("in"),
    OUT("out"),
    COUNT("count"),
    TOTAL("total"),
    RATIO("ratio"),
    ;

    companion object {
        fun fromString(value: String): RankingType =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid ranking type: $value")
    }
}

/**
 * システム統計
 */
data class SystemStats(
    val term: StatsTerm,
    val balance: Long,
    val difference: Long,
    val count: Long,
    val total: Long,
    val ratio: Long,
    val updatedAt: Instant,
)

/**
 * ユーザー統計
 */
data class UsersStats(
    val term: StatsTerm,
    val number: Long,
    val balance: Long,
    val difference: Long,
    val count: Long,
    val total: Long,
    val ratio: Long,
    val updatedAt: Instant,
)

/**
 * プロジェクト統計
 */
data class ProjectsStats(
    val term: StatsTerm,
    val number: Long,
    val balance: Long,
    val difference: Long,
    val count: Long,
    val total: Long,
    val ratio: Long,
    val updatedAt: Instant,
)

/**
 * ユーザーランキングエントリ
 */
data class UserRankingEntry(
    val term: StatsTerm,
    val rankingType: RankingType,
    val userId: UserId,
    val rankValue: Long,
    val difference: Long,
    val updatedAt: Instant,
)

/**
 * プロジェクトランキングエントリ
 */
data class ProjectRankingEntry(
    val term: StatsTerm,
    val rankingType: RankingType,
    val projectId: ProjectId,
    val rankValue: Long,
    val difference: Long,
    val updatedAt: Instant,
)
