package jp.trap.plutus.pteron.features.stats.domain.model

/**
 * 経済圏全体の統計情報
 */
data class SystemStats(
    val balance: Long,
    val difference: Long,
    val count: Long,
    val total: Long,
    val ratio: Long,
)
