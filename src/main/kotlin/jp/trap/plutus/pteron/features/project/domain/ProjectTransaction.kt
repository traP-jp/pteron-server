package jp.trap.plutus.pteron.features.project.domain

interface ProjectTransaction {
    suspend fun <T> runInTransaction(block: () -> T): T
}