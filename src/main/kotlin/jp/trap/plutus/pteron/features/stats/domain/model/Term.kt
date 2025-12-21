package jp.trap.plutus.pteron.features.stats.domain.model

/**
 * 統計の期間を表すenum
 */
enum class Term(val hours: Long, val key: String) {
    HOURS_24(24, "24hours"),
    DAYS_7(24 * 7, "7days"),
    DAYS_30(24 * 30, "30days"),
    DAYS_365(24 * 365, "365days"),
    ;

    companion object {
        fun fromString(value: String): Term =
            entries.find { it.key == value }
                ?: throw IllegalArgumentException("Unknown term: $value")
    }
}
