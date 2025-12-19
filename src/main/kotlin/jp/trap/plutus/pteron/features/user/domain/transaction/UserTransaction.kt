package jp.trap.plutus.pteron.features.user.domain.transaction

interface UserTransaction {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
