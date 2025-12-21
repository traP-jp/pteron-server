package jp.trap.plutus.pteron.features.stats.domain.model

/**
 * ランキングの種別を表すenum
 */
enum class RankingType(val key: String) {
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
            entries.find { it.key == value }
                ?: throw IllegalArgumentException("Unknown ranking type: $value")
    }
}
