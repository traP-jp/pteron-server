package jp.trap.plutus.pteron.features.transaction.domain.model

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import kotlin.time.Instant

/**
 * 完了した取引を表すドメインモデル
 */
class Transaction(
    val id: TransactionId,
    val type: TransactionType,
    val amount: Long,
    val projectId: ProjectId?,
    val userId: UserId?,
    val description: String?,
    val createdAt: Instant,
) {
    init {
        require(amount > 0) { "Transaction amount must be positive" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transaction) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
