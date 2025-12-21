package jp.trap.plutus.pteron.features.stats.infrastructure

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.infrastructure.RankingCursor
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import jp.trap.plutus.pteron.features.stats.domain.model.*
import jp.trap.plutus.pteron.features.stats.domain.repository.StatsCacheRepository
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionRepository
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Single(binds = [StatsCacheRepository::class])
class DatabaseStatsCacheRepository(
    private val economicGateway: EconomicGateway,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val transactionRepository: TransactionRepository,
) : StatsCacheRepository {
    // === 読み取りメソッド ===

    override suspend fun getSystemStats(term: Term): SystemStats? =
        StatsCacheSystemTable
            .selectAll()
            .where { StatsCacheSystemTable.term eq term.key }
            .map { row ->
                SystemStats(
                    balance = row[StatsCacheSystemTable.balance],
                    difference = row[StatsCacheSystemTable.difference],
                    count = row[StatsCacheSystemTable.count],
                    total = row[StatsCacheSystemTable.total],
                    ratio = row[StatsCacheSystemTable.ratio],
                )
            }.singleOrNull()

    override suspend fun getUsersAggregateStats(term: Term): AggregateStats? =
        StatsCacheUsersAggregateTable
            .selectAll()
            .where { StatsCacheUsersAggregateTable.term eq term.key }
            .map { row ->
                AggregateStats(
                    number = row[StatsCacheUsersAggregateTable.number],
                    balance = row[StatsCacheUsersAggregateTable.balance],
                    difference = row[StatsCacheUsersAggregateTable.difference],
                    count = row[StatsCacheUsersAggregateTable.count],
                    total = row[StatsCacheUsersAggregateTable.total],
                    ratio = row[StatsCacheUsersAggregateTable.ratio],
                )
            }.singleOrNull()

    override suspend fun getProjectsAggregateStats(term: Term): AggregateStats? =
        StatsCacheProjectsAggregateTable
            .selectAll()
            .where { StatsCacheProjectsAggregateTable.term eq term.key }
            .map { row ->
                AggregateStats(
                    number = row[StatsCacheProjectsAggregateTable.number],
                    balance = row[StatsCacheProjectsAggregateTable.balance],
                    difference = row[StatsCacheProjectsAggregateTable.difference],
                    count = row[StatsCacheProjectsAggregateTable.count],
                    total = row[StatsCacheProjectsAggregateTable.total],
                    ratio = row[StatsCacheProjectsAggregateTable.ratio],
                )
            }.singleOrNull()

    override suspend fun getUserRankings(
        rankingType: RankingType,
        term: Term,
        ascending: Boolean,
        limit: Int,
        cursor: String?,
    ): RankingQueryResult<UserRankingEntry> {
        val cursorData = cursor?.let { RankingCursor.decode(it) }

        val query =
            StatsCacheUserRankingsTable
                .selectAll()
                .where {
                    (StatsCacheUserRankingsTable.term eq term.key) and
                        (StatsCacheUserRankingsTable.rankingType eq rankingType.key)
                }.apply {
                    cursorData?.let { (cursorRank, _) ->
                        andWhere { StatsCacheUserRankingsTable.rank greater cursorRank }
                    }
                }.orderBy(StatsCacheUserRankingsTable.rank, if (ascending) SortOrder.ASC else SortOrder.DESC)
                .limit(limit + 1)

        val results =
            query.map { row ->
                UserRankingEntry(
                    rank = row[StatsCacheUserRankingsTable.rank],
                    value = row[StatsCacheUserRankingsTable.value],
                    difference = row[StatsCacheUserRankingsTable.difference],
                    userId = UserId(row[StatsCacheUserRankingsTable.userId].toKotlinUuid()),
                )
            }

        val hasNext = results.size > limit
        val items = if (hasNext) results.dropLast(1) else results
        val nextCursor =
            if (hasNext) {
                items.lastOrNull()?.let { RankingCursor.encode(it.rank, it.userId.value) }
            } else {
                null
            }

        return RankingQueryResult(items, nextCursor)
    }

    override suspend fun getProjectRankings(
        rankingType: RankingType,
        term: Term,
        ascending: Boolean,
        limit: Int,
        cursor: String?,
    ): RankingQueryResult<ProjectRankingEntry> {
        val cursorData = cursor?.let { RankingCursor.decode(it) }

        val query =
            StatsCacheProjectRankingsTable
                .selectAll()
                .where {
                    (StatsCacheProjectRankingsTable.term eq term.key) and
                        (StatsCacheProjectRankingsTable.rankingType eq rankingType.key)
                }.apply {
                    cursorData?.let { (cursorRank, _) ->
                        andWhere { StatsCacheProjectRankingsTable.rank greater cursorRank }
                    }
                }.orderBy(StatsCacheProjectRankingsTable.rank, if (ascending) SortOrder.ASC else SortOrder.DESC)
                .limit(limit + 1)

        val results =
            query.map { row ->
                ProjectRankingEntry(
                    rank = row[StatsCacheProjectRankingsTable.rank],
                    value = row[StatsCacheProjectRankingsTable.value],
                    difference = row[StatsCacheProjectRankingsTable.difference],
                    projectId = ProjectId(row[StatsCacheProjectRankingsTable.projectId].toKotlinUuid()),
                )
            }

        val hasNext = results.size > limit
        val items = if (hasNext) results.dropLast(1) else results
        val nextCursor =
            if (hasNext) {
                items.lastOrNull()?.let { RankingCursor.encode(it.rank, it.projectId.value) }
            } else {
                null
            }

        return RankingQueryResult(items, nextCursor)
    }

    override suspend fun getUserStats(
        userId: UserId,
        term: Term,
    ): IndividualStats? {
        val rankings =
            StatsCacheUserRankingsTable
                .selectAll()
                .where {
                    (StatsCacheUserRankingsTable.term eq term.key) and
                        (StatsCacheUserRankingsTable.userId eq userId.value.toJavaUuid())
                }.associate { row ->
                    row[StatsCacheUserRankingsTable.rankingType] to
                        RankingPosition(
                            rank = row[StatsCacheUserRankingsTable.rank],
                            value = row[StatsCacheUserRankingsTable.value],
                            difference = row[StatsCacheUserRankingsTable.difference],
                        )
                }

        if (rankings.size < RankingType.entries.size) return null

        return IndividualStats(
            balance = rankings[RankingType.BALANCE.key]!!,
            difference = rankings[RankingType.DIFFERENCE.key]!!,
            inAmount = rankings[RankingType.IN.key]!!,
            outAmount = rankings[RankingType.OUT.key]!!,
            count = rankings[RankingType.COUNT.key]!!,
            total = rankings[RankingType.TOTAL.key]!!,
            ratio = rankings[RankingType.RATIO.key]!!,
        )
    }

    override suspend fun getProjectStats(
        projectId: ProjectId,
        term: Term,
    ): IndividualStats? {
        val rankings =
            StatsCacheProjectRankingsTable
                .selectAll()
                .where {
                    (StatsCacheProjectRankingsTable.term eq term.key) and
                        (StatsCacheProjectRankingsTable.projectId eq projectId.value.toJavaUuid())
                }.associate { row ->
                    row[StatsCacheProjectRankingsTable.rankingType] to
                        RankingPosition(
                            rank = row[StatsCacheProjectRankingsTable.rank],
                            value = row[StatsCacheProjectRankingsTable.value],
                            difference = row[StatsCacheProjectRankingsTable.difference],
                        )
                }

        if (rankings.size < RankingType.entries.size) return null

        return IndividualStats(
            balance = rankings[RankingType.BALANCE.key]!!,
            difference = rankings[RankingType.DIFFERENCE.key]!!,
            inAmount = rankings[RankingType.IN.key]!!,
            outAmount = rankings[RankingType.OUT.key]!!,
            count = rankings[RankingType.COUNT.key]!!,
            total = rankings[RankingType.TOTAL.key]!!,
            ratio = rankings[RankingType.RATIO.key]!!,
        )
    }

    override suspend fun getUserBalanceAt(
        userId: UserId,
        at: Instant,
    ): Long? {
        val user = userRepository.findById(userId) ?: return null
        val currentBalance = economicGateway.findAccountById(user.accountId)?.balance ?: return null

        val balanceChange = transactionRepository.getUserBalanceChangeAfter(userId, at)
        return currentBalance - balanceChange.netChange
    }

    override suspend fun getProjectBalanceAt(
        projectId: ProjectId,
        at: Instant,
    ): Long? {
        val project = projectRepository.findById(projectId) ?: return null
        val currentBalance = economicGateway.findAccountById(project.accountId)?.balance ?: return null

        val balanceChange = transactionRepository.getProjectBalanceChangeAfter(projectId, at)
        return currentBalance - balanceChange.netChange
    }

    // === 書き込みメソッド ===

    override suspend fun saveSystemStats(
        term: Term,
        stats: SystemStats,
    ) {
        val now = Clock.System.now()
        StatsCacheSystemTable.upsert {
            it[StatsCacheSystemTable.term] = term.key
            it[balance] = stats.balance
            it[difference] = stats.difference
            it[count] = stats.count
            it[total] = stats.total
            it[ratio] = stats.ratio
            it[updatedAt] = now
        }
    }

    override suspend fun saveUsersAggregateStats(
        term: Term,
        stats: AggregateStats,
    ) {
        val now = Clock.System.now()
        StatsCacheUsersAggregateTable.upsert {
            it[StatsCacheUsersAggregateTable.term] = term.key
            it[number] = stats.number
            it[balance] = stats.balance
            it[difference] = stats.difference
            it[count] = stats.count
            it[total] = stats.total
            it[ratio] = stats.ratio
            it[updatedAt] = now
        }
    }

    override suspend fun saveProjectsAggregateStats(
        term: Term,
        stats: AggregateStats,
    ) {
        val now = Clock.System.now()
        StatsCacheProjectsAggregateTable.upsert {
            it[StatsCacheProjectsAggregateTable.term] = term.key
            it[number] = stats.number
            it[balance] = stats.balance
            it[difference] = stats.difference
            it[count] = stats.count
            it[total] = stats.total
            it[ratio] = stats.ratio
            it[updatedAt] = now
        }
    }

    override suspend fun saveUserRankings(
        term: Term,
        rankingType: RankingType,
        entries: List<UserRankingEntry>,
    ) {
        entries.forEach { entry ->
            StatsCacheUserRankingsTable.upsert {
                it[StatsCacheUserRankingsTable.term] = term.key
                it[StatsCacheUserRankingsTable.rankingType] = rankingType.key
                it[userId] = entry.userId.value.toJavaUuid()
                it[rank] = entry.rank
                it[value] = entry.value
                it[difference] = entry.difference
            }
        }
    }

    override suspend fun saveProjectRankings(
        term: Term,
        rankingType: RankingType,
        entries: List<ProjectRankingEntry>,
    ) {
        entries.forEach { entry ->
            StatsCacheProjectRankingsTable.upsert {
                it[StatsCacheProjectRankingsTable.term] = term.key
                it[StatsCacheProjectRankingsTable.rankingType] = rankingType.key
                it[projectId] = entry.projectId.value.toJavaUuid()
                it[rank] = entry.rank
                it[value] = entry.value
                it[difference] = entry.difference
            }
        }
    }

    override suspend fun clearUserRankings(
        term: Term,
        rankingType: RankingType,
    ) {
        StatsCacheUserRankingsTable.deleteWhere {
            (StatsCacheUserRankingsTable.term eq term.key) and
                (StatsCacheUserRankingsTable.rankingType eq rankingType.key)
        }
    }

    override suspend fun clearProjectRankings(
        term: Term,
        rankingType: RankingType,
    ) {
        StatsCacheProjectRankingsTable.deleteWhere {
            (StatsCacheProjectRankingsTable.term eq term.key) and
                (StatsCacheProjectRankingsTable.rankingType eq rankingType.key)
        }
    }
}
