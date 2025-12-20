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
}
