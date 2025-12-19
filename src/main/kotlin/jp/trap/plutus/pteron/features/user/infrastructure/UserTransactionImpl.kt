package jp.trap.plutus.pteron.features.user.infrastructure

import jp.trap.plutus.pteron.features.user.domain.transaction.UserTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.koin.core.annotation.Single
import java.sql.Connection

@Single(binds = [UserTransaction::class])
class UserTransactionImpl : UserTransaction {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        suspendTransaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
            block()
        }
}
