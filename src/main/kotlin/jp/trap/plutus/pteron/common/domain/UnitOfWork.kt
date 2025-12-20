package jp.trap.plutus.pteron.common.domain

/**
 * 複数の操作をアトミックに実行するためのインターフェース
 */
interface UnitOfWork {
    /**
     * トランザクション内でブロックを実行
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
