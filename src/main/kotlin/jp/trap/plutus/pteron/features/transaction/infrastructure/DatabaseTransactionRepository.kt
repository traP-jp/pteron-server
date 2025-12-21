package jp.trap.plutus.pteron.features.transaction.infrastructure

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.infrastructure.PaginationCursor
import jp.trap.plutus.pteron.features.transaction.domain.model.Transaction
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionId
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionType
import jp.trap.plutus.pteron.features.transaction.domain.repository.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.core.annotation.Single
import kotlin.time.Instant
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

    // === 統計用集計メソッド ===

    override suspend fun getStats(since: Instant): TransactionStatsData {
        val countCol = TransactionTable.id.count()
        val sumCol = TransactionTable.amount.sum()

        // 入金（TRANSFER, SYSTEM）
        val inResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.createdAt greaterEq since) and
                        ((TransactionTable.type eq "TRANSFER") or (TransactionTable.type eq "SYSTEM"))
                }.singleOrNull()

        val inCount = inResult?.get(countCol) ?: 0L
        val inAmount = inResult?.get(sumCol) ?: 0L

        // 出金（BILL_PAYMENT）
        val outResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.type eq "BILL_PAYMENT")
                }.singleOrNull()

        val outCount = outResult?.get(countCol) ?: 0L
        val outAmount = outResult?.get(sumCol) ?: 0L

        return TransactionStatsData(
            count = inCount + outCount,
            total = inAmount + outAmount,
            netChange = inAmount - outAmount,
        )
    }

    override suspend fun getUsersStats(since: Instant): TransactionStatsData {
        val countCol = TransactionTable.id.count()
        val sumCol = TransactionTable.amount.sum()

        // TRANSFERとSYSTEMはユーザーへの入金（ユーザーに関連する取引のみ）
        val inResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.userId.isNotNull()) and
                        ((TransactionTable.type eq "TRANSFER") or (TransactionTable.type eq "SYSTEM"))
                }.singleOrNull()

        val inCount = inResult?.get(countCol) ?: 0L
        val inAmount = inResult?.get(sumCol) ?: 0L

        // BILL_PAYMENTはユーザーからの出金（ユーザーに関連する取引のみ）
        val outResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.userId.isNotNull()) and
                        (TransactionTable.type eq "BILL_PAYMENT")
                }.singleOrNull()

        val outCount = outResult?.get(countCol) ?: 0L
        val outAmount = outResult?.get(sumCol) ?: 0L

        return TransactionStatsData(
            count = inCount + outCount,
            total = inAmount + outAmount,
            netChange = inAmount - outAmount,
        )
    }

    override suspend fun getProjectsStats(since: Instant): TransactionStatsData {
        val countCol = TransactionTable.id.count()
        val sumCol = TransactionTable.amount.sum()

        // BILL_PAYMENTとSYSTEMはプロジェクトへの入金（プロジェクトに関連する取引のみ）
        val inResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.projectId.isNotNull()) and
                        ((TransactionTable.type eq "BILL_PAYMENT") or (TransactionTable.type eq "SYSTEM"))
                }.singleOrNull()

        val inCount = inResult?.get(countCol) ?: 0L
        val inAmount = inResult?.get(sumCol) ?: 0L

        // TRANSFERはプロジェクトからの出金（プロジェクトに関連する取引のみ）
        val outResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.projectId.isNotNull()) and
                        (TransactionTable.type eq "TRANSFER")
                }.singleOrNull()

        val outCount = outResult?.get(countCol) ?: 0L
        val outAmount = outResult?.get(sumCol) ?: 0L

        return TransactionStatsData(
            count = inCount + outCount,
            total = inAmount + outAmount,
            netChange = inAmount - outAmount,
        )
    }

    override suspend fun getUserStats(
        userId: UserId,
        since: Instant,
        until: Instant,
    ): TransactionStatsData {
        val countCol = TransactionTable.id.count()
        val sumCol = TransactionTable.amount.sum()
        val userIdJava = userId.value.toJavaUuid()

        // TRANSFERとSYSTEMはユーザーへの入金
        val inResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.userId eq userIdJava) and
                        (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.createdAt less until) and
                        ((TransactionTable.type eq "TRANSFER") or (TransactionTable.type eq "SYSTEM"))
                }.singleOrNull()

        val inCount = inResult?.get(countCol) ?: 0L
        val inAmount = inResult?.get(sumCol) ?: 0L

        // BILL_PAYMENTはユーザーからの出金
        val outResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.userId eq userIdJava) and
                        (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.createdAt less until) and
                        (TransactionTable.type eq "BILL_PAYMENT")
                }.singleOrNull()

        val outCount = outResult?.get(countCol) ?: 0L
        val outAmount = outResult?.get(sumCol) ?: 0L

        return TransactionStatsData(
            count = inCount + outCount,
            total = inAmount + outAmount,
            netChange = inAmount - outAmount,
            inAmount = inAmount,
            outAmount = outAmount,
        )
    }

    override suspend fun getProjectStats(
        projectId: ProjectId,
        since: Instant,
        until: Instant,
    ): TransactionStatsData {
        val countCol = TransactionTable.id.count()
        val sumCol = TransactionTable.amount.sum()
        val projectIdJava = projectId.value.toJavaUuid()

        // BILL_PAYMENTとSYSTEMはプロジェクトへの入金
        val inResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.projectId eq projectIdJava) and
                        (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.createdAt less until) and
                        ((TransactionTable.type eq "BILL_PAYMENT") or (TransactionTable.type eq "SYSTEM"))
                }.singleOrNull()

        val inCount = inResult?.get(countCol) ?: 0L
        val inAmount = inResult?.get(sumCol) ?: 0L

        // TRANSFERはプロジェクトからの出金
        val outResult =
            TransactionTable
                .select(countCol, sumCol)
                .where {
                    (TransactionTable.projectId eq projectIdJava) and
                        (TransactionTable.createdAt greaterEq since) and
                        (TransactionTable.createdAt less until) and
                        (TransactionTable.type eq "TRANSFER")
                }.singleOrNull()

        val outCount = outResult?.get(countCol) ?: 0L
        val outAmount = outResult?.get(sumCol) ?: 0L

        return TransactionStatsData(
            count = inCount + outCount,
            total = inAmount + outAmount,
            netChange = inAmount - outAmount,
            inAmount = inAmount,
            outAmount = outAmount,
        )
    }

    // === Private helper methods ===

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

    override suspend fun getUserBalanceChangeAfter(
        userId: UserId,
        after: Instant,
    ): BalanceChangeData {
        val sumCol = TransactionTable.amount.sum()
        val userIdJava = userId.value.toJavaUuid()

        // TRANSFERとSYSTEMはユーザーへの入金
        val inResult =
            TransactionTable
                .select(sumCol)
                .where {
                    (TransactionTable.userId eq userIdJava) and
                        (TransactionTable.createdAt greater after) and
                        ((TransactionTable.type eq "TRANSFER") or (TransactionTable.type eq "SYSTEM"))
                }.singleOrNull()

        val inAmount = inResult?.get(sumCol) ?: 0L

        // BILL_PAYMENTはユーザーからの出金
        val outResult =
            TransactionTable
                .select(sumCol)
                .where {
                    (TransactionTable.userId eq userIdJava) and
                        (TransactionTable.createdAt greater after) and
                        (TransactionTable.type eq "BILL_PAYMENT")
                }.singleOrNull()

        val outAmount = outResult?.get(sumCol) ?: 0L

        return BalanceChangeData(inAmount = inAmount, outAmount = outAmount)
    }

    override suspend fun getProjectBalanceChangeAfter(
        projectId: ProjectId,
        after: Instant,
    ): BalanceChangeData {
        val sumCol = TransactionTable.amount.sum()
        val projectIdJava = projectId.value.toJavaUuid()

        // BILL_PAYMENTとSYSTEMはプロジェクトへの入金
        val inResult =
            TransactionTable
                .select(sumCol)
                .where {
                    (TransactionTable.projectId eq projectIdJava) and
                        (TransactionTable.createdAt greater after) and
                        ((TransactionTable.type eq "BILL_PAYMENT") or (TransactionTable.type eq "SYSTEM"))
                }.singleOrNull()

        val inAmount = inResult?.get(sumCol) ?: 0L

        // TRANSFERはプロジェクトからの出金
        val outResult =
            TransactionTable
                .select(sumCol)
                .where {
                    (TransactionTable.projectId eq projectIdJava) and
                        (TransactionTable.createdAt greater after) and
                        (TransactionTable.type eq "TRANSFER")
                }.singleOrNull()

        val outAmount = outResult?.get(sumCol) ?: 0L

        return BalanceChangeData(inAmount = inAmount, outAmount = outAmount)
    }
}
