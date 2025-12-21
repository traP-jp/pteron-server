package jp.trap.plutus.pteron.features.transaction.infrastructure

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.infrastructure.PaginationCursor
import jp.trap.plutus.pteron.features.transaction.domain.model.Transaction
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionId
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionType
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionQueryOptions
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionQueryResult
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.core.annotation.Single
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Single(binds = [TransactionRepository::class])
class DatabaseTransactionRepository : TransactionRepository {
    override suspend fun findById(id: TransactionId): Transaction? =
        TransactionTable
            .selectAll()
            .where { TransactionTable.id eq id.value.toJavaUuid() }
            .map { it.toTransaction() }
            .singleOrNull()

    override suspend fun findAll(options: TransactionQueryOptions): TransactionQueryResult = executeQuery(options, null)

    override suspend fun findByUserId(
        userId: UserId,
        options: TransactionQueryOptions,
    ): TransactionQueryResult = executeQuery(options, TransactionTable.userId eq userId.value.toJavaUuid())

    override suspend fun findByProjectId(
        projectId: ProjectId,
        options: TransactionQueryOptions,
    ): TransactionQueryResult = executeQuery(options, TransactionTable.projectId eq projectId.value.toJavaUuid())

    override suspend fun save(transaction: Transaction): Transaction {
        TransactionTable.insert {
            it[id] = transaction.id.value.toJavaUuid()
            it[type] = transaction.type.name
            it[amount] = transaction.amount
            it[projectId] = transaction.projectId?.value?.toJavaUuid()
            it[userId] = transaction.userId?.value?.toJavaUuid()
            it[description] = transaction.description
            it[createdAt] = transaction.createdAt
        }
        return transaction
    }

    private fun executeQuery(
        options: TransactionQueryOptions,
        baseFilter: Op<Boolean>?,
    ): TransactionQueryResult {
        val limit = options.limit ?: 20
        val cursorData = options.cursor?.let { PaginationCursor.decode(it) }
        val since = options.since

        val query =
            TransactionTable
                .selectAll()
                .apply {
                    val conditions = mutableListOf<Op<Boolean>>()

                    baseFilter?.let { conditions.add(it) }
                    cursorData?.let { (cursorCreatedAt, cursorId) ->
                        val cursorCreatedAtKt = cursorCreatedAt
                        val cursorIdJava = cursorId.toJavaUuid()
                        // createdAt DESC, id DESC でソートするため、
                        // カーソルより「前」のレコードを取得
                        conditions.add(
                            (TransactionTable.createdAt less cursorCreatedAtKt) or
                                (
                                    (TransactionTable.createdAt eq cursorCreatedAtKt) and
                                        (TransactionTable.id less cursorIdJava)
                                ),
                        )
                    }
                    since?.let { conditions.add(TransactionTable.createdAt greater it) }

                    if (conditions.isNotEmpty()) {
                        where { conditions.reduce { acc, op -> acc and op } }
                    }
                }.orderBy(TransactionTable.createdAt, SortOrder.DESC)
                .orderBy(TransactionTable.id, SortOrder.DESC)
                .limit(limit + 1)

        val results = query.map { it.toTransaction() }
        val hasNext = results.size > limit
        val items = if (hasNext) results.dropLast(1) else results
        val nextCursor =
            if (hasNext) {
                items.lastOrNull()?.let { PaginationCursor.encode(it.createdAt, it.id.value) }
            } else {
                null
            }

        return TransactionQueryResult(items, nextCursor)
    }

    private fun ResultRow.toTransaction(): Transaction =
        Transaction(
            id = TransactionId(this[TransactionTable.id].value.toKotlinUuid()),
            type = TransactionType.valueOf(this[TransactionTable.type]),
            amount = this[TransactionTable.amount],
            projectId = this[TransactionTable.projectId]?.let { ProjectId(it.toKotlinUuid()) },
            userId = this[TransactionTable.userId]?.let { UserId(it.toKotlinUuid()) },
            description = this[TransactionTable.description],
            createdAt = this[TransactionTable.createdAt],
        )
}
