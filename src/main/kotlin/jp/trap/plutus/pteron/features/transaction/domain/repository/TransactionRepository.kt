package jp.trap.plutus.pteron.features.transaction.domain.repository

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.transaction.domain.model.Transaction
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionId
import kotlin.time.Instant

/**
 * 取引リポジトリのクエリオプション
 */
data class TransactionQueryOptions(
    /**
     * 結果の最大件数。nullの場合は制限なし
     */
    val limit: Int? = null,
    /**
     * ページング用のカーソル
     */
    val cursor: String? = null,
    /**
     * 指定した日時以降の取引を取得
     */
    val since: Instant? = null,
)

/**
 * 取引リポジトリのクエリ結果
 */
data class TransactionQueryResult(
    val items: List<Transaction>,
    /**
     * 次のページを取得するためのカーソル（次のページがない場合はnull）
     */
    val nextCursor: String?,
)

/**
 * 取引統計データ
 */
data class TransactionStatsData(
    val count: Long,
    val total: Long,
    val netChange: Long,
    val inAmount: Long = 0L,
    val outAmount: Long = 0L,
)

/**
 * 残高変動データ
 */
data class BalanceChangeData(
    val inAmount: Long,
    val outAmount: Long,
) {
    val netChange: Long get() = inAmount - outAmount
}

/**
 * 取引リポジトリインターフェース
 */
interface TransactionRepository {
    /**
     * IDで取引を検索
     */
    suspend fun findById(id: TransactionId): Transaction?

    /**
     * 全取引を取得
     */
    suspend fun findAll(options: TransactionQueryOptions = TransactionQueryOptions()): TransactionQueryResult

    /**
     * 特定のユーザーの取引履歴を取得
     */
    suspend fun findByUserId(
        userId: UserId,
        options: TransactionQueryOptions = TransactionQueryOptions(),
    ): TransactionQueryResult

    /**
     * 特定のプロジェクトの取引履歴を取得
     */
    suspend fun findByProjectId(
        projectId: ProjectId,
        options: TransactionQueryOptions = TransactionQueryOptions(),
    ): TransactionQueryResult

    /**
     * 取引を保存
     */
    suspend fun save(transaction: Transaction): Transaction

    // === 統計用集計メソッド ===

    /**
     * 期間内の全取引統計を取得
     */
    suspend fun getStats(since: Instant): TransactionStatsData

    /**
     * 期間内のユーザー全体の取引統計を取得
     */
    suspend fun getUsersStats(since: Instant): TransactionStatsData

    /**
     * 期間内のプロジェクト全体の取引統計を取得
     */
    suspend fun getProjectsStats(since: Instant): TransactionStatsData

    /**
     * 特定ユーザーの期間内取引統計を取得
     */
    suspend fun getUserStats(
        userId: UserId,
        since: Instant,
        until: Instant,
    ): TransactionStatsData

    /**
     * 特定プロジェクトの期間内取引統計を取得
     */
    suspend fun getProjectStats(
        projectId: ProjectId,
        since: Instant,
        until: Instant,
    ): TransactionStatsData

    /**
     * 指定時点以降のユーザーの残高変動を取得
     */
    suspend fun getUserBalanceChangeAfter(
        userId: UserId,
        after: Instant,
    ): BalanceChangeData

    /**
     * 指定時点以降のプロジェクトの残高変動を取得
     */
    suspend fun getProjectBalanceChangeAfter(
        projectId: ProjectId,
        after: Instant,
    ): BalanceChangeData
}
