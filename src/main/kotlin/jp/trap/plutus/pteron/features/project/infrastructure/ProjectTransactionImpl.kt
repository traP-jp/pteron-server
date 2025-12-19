package jp.trap.plutus.pteron.features.project.infrastructure

import jp.trap.plutus.pteron.features.project.domain.transaction.ProjectTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.koin.core.annotation.Single
import java.sql.Connection

@Single(binds = [ProjectTransaction::class])
class ProjectTransactionImpl : ProjectTransaction {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        suspendTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
            block()
        }
}
