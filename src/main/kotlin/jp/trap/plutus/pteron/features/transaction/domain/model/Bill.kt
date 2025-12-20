package jp.trap.plutus.pteron.features.transaction.domain.model

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import kotlin.time.Instant

/**
 * 請求承認の結果
 */
sealed interface BillApprovalResult {
    data class Success(
        val bill: Bill,
    ) : BillApprovalResult

    sealed interface Failure : BillApprovalResult {
        /**
         * 既に処理済み（PENDINGではない）
         */
        object AlreadyProcessed : Failure

        /**
         * 残高不足
         */
        object InsufficientBalance : Failure
    }
}

/**
 * 請求完了の結果
 */
sealed interface BillCompleteResult {
    data class Success(
        val bill: Bill,
    ) : BillCompleteResult

    sealed interface Failure : BillCompleteResult {
        /**
         * 処理中状態ではない
         */
        object NotProcessing : Failure
    }
}

/**
 * 請求拒否の結果
 */
sealed interface BillDeclineResult {
    data class Success(
        val bill: Bill,
    ) : BillDeclineResult

    sealed interface Failure : BillDeclineResult {
        /**
         * 既に処理済み（PENDINGではない）
         */
        object AlreadyProcessed : Failure
    }
}

/**
 * 請求失敗マークの結果
 */
sealed interface BillMarkAsFailedResult {
    data class Success(
        val bill: Bill,
    ) : BillMarkAsFailedResult

    sealed interface Failure : BillMarkAsFailedResult {
        /**
         * 処理中状態ではない
         */
        object NotProcessing : Failure
    }
}

/**
 * 請求を表すドメインモデル
 */
class Bill(
    val id: BillId,
    val amount: Long,
    val userId: UserId,
    val projectId: ProjectId,
    val description: String?,
    val status: BillStatus,
    val createdAt: Instant,
) {
    init {
        require(amount > 0) { "Bill amount must be positive" }
    }

    /**
     * 請求が処理待ち状態かどうか
     */
    fun isPending(): Boolean = status == BillStatus.PENDING

    /**
     * 請求が処理中状態かどうか
     */
    fun isProcessing(): Boolean = status == BillStatus.PROCESSING

    /**
     * 請求を承認し、処理中状態に変更する
     */
    fun approve(): BillApprovalResult {
        if (!isPending()) {
            return BillApprovalResult.Failure.AlreadyProcessed
        }
        return BillApprovalResult.Success(
            Bill(
                id = id,
                amount = amount,
                userId = userId,
                projectId = projectId,
                description = description,
                status = BillStatus.PROCESSING,
                createdAt = createdAt,
            ),
        )
    }

    /**
     * 処理中の請求を完了状態に変更する
     */
    fun complete(): BillCompleteResult {
        if (!isProcessing()) {
            return BillCompleteResult.Failure.NotProcessing
        }
        return BillCompleteResult.Success(
            Bill(
                id = id,
                amount = amount,
                userId = userId,
                projectId = projectId,
                description = description,
                status = BillStatus.COMPLETED,
                createdAt = createdAt,
            ),
        )
    }

    /**
     * 請求を拒否する
     */
    fun decline(): BillDeclineResult {
        if (!isPending()) {
            return BillDeclineResult.Failure.AlreadyProcessed
        }
        return BillDeclineResult.Success(
            Bill(
                id = id,
                amount = amount,
                userId = userId,
                projectId = projectId,
                description = description,
                status = BillStatus.REJECTED,
                createdAt = createdAt,
            ),
        )
    }

    /**
     * 請求処理を失敗状態に変更する
     */
    fun markAsFailed(): BillMarkAsFailedResult {
        if (!isProcessing()) {
            return BillMarkAsFailedResult.Failure.NotProcessing
        }
        return BillMarkAsFailedResult.Success(
            Bill(
                id = id,
                amount = amount,
                userId = userId,
                projectId = projectId,
                description = description,
                status = BillStatus.FAILED,
                createdAt = createdAt,
            ),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bill) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
