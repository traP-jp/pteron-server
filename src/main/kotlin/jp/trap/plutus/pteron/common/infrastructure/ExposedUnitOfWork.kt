package jp.trap.plutus.pteron.common.infrastructure

import jp.trap.plutus.pteron.common.domain.UnitOfWork
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.koin.core.annotation.Single
import java.sql.Connection

@Single(binds = [UnitOfWork::class])
class ExposedUnitOfWork(
    private val database: Database,
) : UnitOfWork {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        suspendTransaction(
            transactionIsolation = Connection.TRANSACTION_SERIALIZABLE,
            db = database,
        ) {
            block()
        }
}
