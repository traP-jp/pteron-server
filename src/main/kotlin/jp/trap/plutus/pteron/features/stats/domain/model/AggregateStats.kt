package jp.trap.plutus.pteron.features.stats.domain.model

/**
 * ユーザーまたはプロジェクトの集計統計情報
 */
data class AggregateStats(
    val number: Long,
    val balance: Long,
    val difference: Long,
    val count: Long,
    val total: Long,
    val ratio: Long,
)
