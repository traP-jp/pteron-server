package jp.trap.plutus.pteron.features.stats.domain.model

/**
 * ランキング内での位置情報
 */
data class RankingPosition(
    val rank: Long,
    val value: Long,
    val difference: Long,
)

/**
 * 個別ユーザー/プロジェクトの全ランキング情報
 */
data class IndividualStats(
    val balance: RankingPosition,
    val difference: RankingPosition,
    val inAmount: RankingPosition,
    val outAmount: RankingPosition,
    val count: RankingPosition,
    val total: RankingPosition,
    val ratio: RankingPosition,
)
