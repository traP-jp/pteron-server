package jp.trap.plutus.pteron.features.transaction.domain.model

/**
 * 取引の種類
 */
enum class TransactionType {
    /**
     * プロジェクトからユーザーへの送金
     */
    TRANSFER,

    /**
     * プロジェクトへの請求の支払い
     */
    BILL_PAYMENT,
}
