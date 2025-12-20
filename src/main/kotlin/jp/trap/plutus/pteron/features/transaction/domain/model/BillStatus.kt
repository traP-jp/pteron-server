package jp.trap.plutus.pteron.features.transaction.domain.model

/**
 * 請求のステータス
 */
enum class BillStatus {
    /**
     * 処理待ち
     */
    PENDING,

    /**
     * 処理中
     */
    PROCESSING,

    /**
     * 支払い完了
     */
    COMPLETED,

    /**
     * 拒否された
     */
    REJECTED,

    /**
     * 処理失敗
     */
    FAILED,
}
