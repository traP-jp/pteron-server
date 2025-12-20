package jp.trap.plutus.pteron.features.transaction.domain.repository

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.transaction.domain.model.Bill
import jp.trap.plutus.pteron.features.transaction.domain.model.BillId
import jp.trap.plutus.pteron.features.transaction.domain.model.BillStatus

/**
 * 請求リポジトリのクエリオプション
 */
data class BillQueryOptions(
    /**
     * 結果の最大件数。nullの場合は制限なし
     */
    val limit: Int? = null,
    /**
     * ページング用のカーソル
     */
    val cursor: String? = null,
    /**
     * ステータスでフィルタ
     */
    val status: BillStatus? = null,
)

/**
 * 請求リポジトリのクエリ結果
 */
data class BillQueryResult(
    val items: List<Bill>,
    /**
     * 次のページを取得するためのカーソル（次のページがない場合はnull）
     */
    val nextCursor: String?,
)

/**
 * 請求リポジトリインターフェース
 */
interface BillRepository {
    /**
     * IDで請求を検索
     */
    suspend fun findById(id: BillId): Bill?

    /**
     * 特定のユーザーへの請求一覧を取得
     */
    suspend fun findByUserId(
        userId: UserId,
        options: BillQueryOptions = BillQueryOptions(),
    ): BillQueryResult

    /**
     * 特定のプロジェクトが発行した請求一覧を取得
     */
    suspend fun findByProjectId(
        projectId: ProjectId,
        options: BillQueryOptions = BillQueryOptions(),
    ): BillQueryResult

    /**
     * 請求を保存
     */
    suspend fun save(bill: Bill): Bill
}
