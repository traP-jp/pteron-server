package jp.trap.plutus.pteron.features.project.domain.transaction

interface ProjectTransaction {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
