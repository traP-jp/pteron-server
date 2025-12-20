package jp.trap.plutus.pteron.features.transaction.infrastructure

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.infrastructure.PaginationCursor
import jp.trap.plutus.pteron.features.transaction.domain.model.Bill
import jp.trap.plutus.pteron.features.transaction.domain.model.BillId
import jp.trap.plutus.pteron.features.transaction.domain.model.BillStatus
import jp.trap.plutus.pteron.features.transaction.domain.repository.BillQueryOptions
import jp.trap.plutus.pteron.features.transaction.domain.repository.BillQueryResult
import jp.trap.plutus.pteron.features.transaction.domain.repository.BillRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.koin.core.annotation.Single
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Single(binds = [BillRepository::class])
class DatabaseBillRepository : BillRepository {
    override suspend fun findById(id: BillId): Bill? =
        BillTable
            .selectAll()
            .where { BillTable.id eq id.value.toJavaUuid() }
            .map { it.toBill() }
            .singleOrNull()

    override suspend fun findByUserId(
        userId: UserId,
        options: BillQueryOptions,
    ): BillQueryResult =
        executeQuery(options, BillTable.userId eq userId.value.toJavaUuid())

    override suspend fun findByProjectId(
        projectId: ProjectId,
        options: BillQueryOptions,
    ): BillQueryResult =
        executeQuery(options, BillTable.projectId eq projectId.value.toJavaUuid())

    override suspend fun save(bill: Bill): Bill {
        BillTable.upsert {
            it[id] = bill.id.value.toJavaUuid()
            it[amount] = bill.amount
            it[userId] = bill.userId.value.toJavaUuid()
            it[projectId] = bill.projectId.value.toJavaUuid()
            it[description] = bill.description
            it[status] = bill.status.name
            it[createdAt] = bill.createdAt
        }
        return bill
    }

    private fun executeQuery(
        options: BillQueryOptions,
        baseFilter: Op<Boolean>,
    ): BillQueryResult {
        val limit = options.limit ?: 20
        val cursorData = options.cursor?.let { PaginationCursor.decode(it) }
        val statusFilter = options.status

        val query =
            BillTable
                .selectAll()
                .apply {
                    val conditions = mutableListOf(baseFilter)

                    cursorData?.let { (cursorCreatedAt, cursorId) ->
                        val cursorCreatedAtKt = cursorCreatedAt
                        val cursorIdJava = cursorId.toJavaUuid()
                        // createdAt DESC, id DESC でソートするため、
                        // カーソルより「前」のレコードを取得
                        conditions.add(
                            (BillTable.createdAt less cursorCreatedAtKt) or
                                ((BillTable.createdAt eq cursorCreatedAtKt) and
                                    (BillTable.id less cursorIdJava))
                        )
                    }
                    statusFilter?.let { conditions.add(BillTable.status eq it.name) }

                    where { conditions.reduce { acc, op -> acc and op } }
                }
                .orderBy(BillTable.createdAt, SortOrder.DESC)
                .orderBy(BillTable.id, SortOrder.DESC)
                .limit(limit + 1)

        val results = query.map { it.toBill() }
        val hasNext = results.size > limit
        val items = if (hasNext) results.dropLast(1) else results
        val nextCursor = if (hasNext) {
            items.lastOrNull()?.let { PaginationCursor.encode(it.createdAt, it.id.value) }
        } else null

        return BillQueryResult(items, nextCursor)
    }

    private fun ResultRow.toBill(): Bill =
        Bill(
            id = BillId(this[BillTable.id].value.toKotlinUuid()),
            amount = this[BillTable.amount],
            userId = UserId(this[BillTable.userId].toKotlinUuid()),
            projectId = ProjectId(this[BillTable.projectId].toKotlinUuid()),
            description = this[BillTable.description],
            status = BillStatus.valueOf(this[BillTable.status]),
            createdAt = this[BillTable.createdAt],
        )
}

