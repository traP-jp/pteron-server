package jp.trap.plutus.pteron.features.stats.infrastructure

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.stats.domain.model.*
import jp.trap.plutus.pteron.features.stats.domain.repository.RankingQueryResult
import jp.trap.plutus.pteron.features.stats.domain.repository.StatsCacheRepository
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.koin.core.annotation.Single
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import java.util.UUID as JavaUuid

@Single
class DatabaseStatsCacheRepository : StatsCacheRepository {
    // --- Read operations ---

    override suspend fun getSystemStats(term: StatsTerm): SystemStats? =
        StatsCacheSystemTable
            .selectAll()
            .where { StatsCacheSystemTable.term eq term.value }
            .singleOrNull()
            ?.let { row ->
                SystemStats(
                    term = StatsTerm.fromString(row[StatsCacheSystemTable.term]),
                    balance = row[StatsCacheSystemTable.balance],
                    difference = row[StatsCacheSystemTable.difference],
                    count = row[StatsCacheSystemTable.count],
                    total = row[StatsCacheSystemTable.total],
                    ratio = row[StatsCacheSystemTable.ratio],
                    updatedAt = row[StatsCacheSystemTable.updatedAt],
                )
            }

    override suspend fun getUsersStats(term: StatsTerm): UsersStats? =
        StatsCacheUsersTable
            .selectAll()
            .where { StatsCacheUsersTable.term eq term.value }
            .singleOrNull()
            ?.let { row ->
                UsersStats(
                    term = StatsTerm.fromString(row[StatsCacheUsersTable.term]),
                    number = row[StatsCacheUsersTable.number],
                    balance = row[StatsCacheUsersTable.balance],
                    difference = row[StatsCacheUsersTable.difference],
                    count = row[StatsCacheUsersTable.count],
                    total = row[StatsCacheUsersTable.total],
                    ratio = row[StatsCacheUsersTable.ratio],
                    updatedAt = row[StatsCacheUsersTable.updatedAt],
                )
            }

    override suspend fun getProjectsStats(term: StatsTerm): ProjectsStats? =
        StatsCacheProjectsTable
            .selectAll()
            .where { StatsCacheProjectsTable.term eq term.value }
            .singleOrNull()
            ?.let { row ->
                ProjectsStats(
                    term = StatsTerm.fromString(row[StatsCacheProjectsTable.term]),
                    number = row[StatsCacheProjectsTable.number],
                    balance = row[StatsCacheProjectsTable.balance],
                    difference = row[StatsCacheProjectsTable.difference],
                    count = row[StatsCacheProjectsTable.count],
                    total = row[StatsCacheProjectsTable.total],
                    ratio = row[StatsCacheProjectsTable.ratio],
                    updatedAt = row[StatsCacheProjectsTable.updatedAt],
                )
            }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getUserRankings(
        term: StatsTerm,
        rankingType: RankingType,
        ascending: Boolean,
        limit: Int,
        cursor: String?,
    ): RankingQueryResult<UserRankingEntry> {
        val sortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC

        // Parse cursor (format: "rankValue:userId")
        val cursorCondition: Op<Boolean> =
            if (cursor != null) {
                val parts = cursor.split(":")
                val cursorRankValue = parts[0].toLong()
                val cursorUserId = JavaUuid.fromString(parts[1])

                if (ascending) {
                    (StatsCacheUserRankingsTable.rankValue greater cursorRankValue) or
                        (
                            (StatsCacheUserRankingsTable.rankValue eq cursorRankValue) and
                                (StatsCacheUserRankingsTable.userId greater cursorUserId)
                        )
                } else {
                    (StatsCacheUserRankingsTable.rankValue less cursorRankValue) or
                        (
                            (StatsCacheUserRankingsTable.rankValue eq cursorRankValue) and
                                (StatsCacheUserRankingsTable.userId less cursorUserId)
                        )
                }
            } else {
                Op.TRUE
            }

        val rows =
            StatsCacheUserRankingsTable
                .selectAll()
                .where {
                    (StatsCacheUserRankingsTable.term eq term.value) and
                        (StatsCacheUserRankingsTable.rankingType eq rankingType.value) and
                        cursorCondition
                }.orderBy(StatsCacheUserRankingsTable.rankValue, sortOrder)
                .orderBy(StatsCacheUserRankingsTable.userId, sortOrder)
                .limit(limit + 1)
                .toList()

        val hasNext = rows.size > limit
        val items = rows.take(limit)

        val entries =
            items.map { row ->
                UserRankingEntry(
                    term = StatsTerm.fromString(row[StatsCacheUserRankingsTable.term]),
                    rankingType = RankingType.fromString(row[StatsCacheUserRankingsTable.rankingType]),
                    userId = UserId(row[StatsCacheUserRankingsTable.userId].toKotlinUuid()),
                    rankValue = row[StatsCacheUserRankingsTable.rankValue],
                    difference = row[StatsCacheUserRankingsTable.difference],
                    updatedAt = row[StatsCacheUserRankingsTable.updatedAt],
                )
            }

        val nextCursor =
            if (hasNext && items.isNotEmpty()) {
                val last = items.last()
                "${last[StatsCacheUserRankingsTable.rankValue]}:${last[StatsCacheUserRankingsTable.userId]}"
            } else {
                null
            }

        return RankingQueryResult(entries, nextCursor)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getProjectRankings(
        term: StatsTerm,
        rankingType: RankingType,
        ascending: Boolean,
        limit: Int,
        cursor: String?,
    ): RankingQueryResult<ProjectRankingEntry> {
        val sortOrder = if (ascending) SortOrder.ASC else SortOrder.DESC

        val cursorCondition: Op<Boolean> =
            if (cursor != null) {
                val parts = cursor.split(":")
                val cursorRankValue = parts[0].toLong()
                val cursorProjectId = JavaUuid.fromString(parts[1])

                if (ascending) {
                    (StatsCacheProjectRankingsTable.rankValue greater cursorRankValue) or
                        (
                            (StatsCacheProjectRankingsTable.rankValue eq cursorRankValue) and
                                (StatsCacheProjectRankingsTable.projectId greater cursorProjectId)
                        )
                } else {
                    (StatsCacheProjectRankingsTable.rankValue less cursorRankValue) or
                        (
                            (StatsCacheProjectRankingsTable.rankValue eq cursorRankValue) and
                                (StatsCacheProjectRankingsTable.projectId less cursorProjectId)
                        )
                }
            } else {
                Op.TRUE
            }

        val rows =
            StatsCacheProjectRankingsTable
                .selectAll()
                .where {
                    (StatsCacheProjectRankingsTable.term eq term.value) and
                        (StatsCacheProjectRankingsTable.rankingType eq rankingType.value) and
                        cursorCondition
                }.orderBy(StatsCacheProjectRankingsTable.rankValue, sortOrder)
                .orderBy(StatsCacheProjectRankingsTable.projectId, sortOrder)
                .limit(limit + 1)
                .toList()

        val hasNext = rows.size > limit
        val items = rows.take(limit)

        val entries =
            items.map { row ->
                ProjectRankingEntry(
                    term = StatsTerm.fromString(row[StatsCacheProjectRankingsTable.term]),
                    rankingType = RankingType.fromString(row[StatsCacheProjectRankingsTable.rankingType]),
                    projectId = ProjectId(row[StatsCacheProjectRankingsTable.projectId].toKotlinUuid()),
                    rankValue = row[StatsCacheProjectRankingsTable.rankValue],
                    difference = row[StatsCacheProjectRankingsTable.difference],
                    updatedAt = row[StatsCacheProjectRankingsTable.updatedAt],
                )
            }

        val nextCursor =
            if (hasNext && items.isNotEmpty()) {
                val last = items.last()
                "${last[StatsCacheProjectRankingsTable.rankValue]}:${last[StatsCacheProjectRankingsTable.projectId]}"
            } else {
                null
            }

        return RankingQueryResult(entries, nextCursor)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getUserRankingEntry(
        term: StatsTerm,
        rankingType: RankingType,
        userId: UserId,
    ): UserRankingEntry? =
        StatsCacheUserRankingsTable
            .selectAll()
            .where {
                (StatsCacheUserRankingsTable.term eq term.value) and
                    (StatsCacheUserRankingsTable.rankingType eq rankingType.value) and
                    (StatsCacheUserRankingsTable.userId eq userId.value.toJavaUuid())
            }.singleOrNull()
            ?.let { row ->
                UserRankingEntry(
                    term = StatsTerm.fromString(row[StatsCacheUserRankingsTable.term]),
                    rankingType = RankingType.fromString(row[StatsCacheUserRankingsTable.rankingType]),
                    userId = UserId(row[StatsCacheUserRankingsTable.userId].toKotlinUuid()),
                    rankValue = row[StatsCacheUserRankingsTable.rankValue],
                    difference = row[StatsCacheUserRankingsTable.difference],
                    updatedAt = row[StatsCacheUserRankingsTable.updatedAt],
                )
            }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getProjectRankingEntry(
        term: StatsTerm,
        rankingType: RankingType,
        projectId: ProjectId,
    ): ProjectRankingEntry? =
        StatsCacheProjectRankingsTable
            .selectAll()
            .where {
                (StatsCacheProjectRankingsTable.term eq term.value) and
                    (StatsCacheProjectRankingsTable.rankingType eq rankingType.value) and
                    (StatsCacheProjectRankingsTable.projectId eq projectId.value.toJavaUuid())
            }.singleOrNull()
            ?.let { row ->
                ProjectRankingEntry(
                    term = StatsTerm.fromString(row[StatsCacheProjectRankingsTable.term]),
                    rankingType = RankingType.fromString(row[StatsCacheProjectRankingsTable.rankingType]),
                    projectId = ProjectId(row[StatsCacheProjectRankingsTable.projectId].toKotlinUuid()),
                    rankValue = row[StatsCacheProjectRankingsTable.rankValue],
                    difference = row[StatsCacheProjectRankingsTable.difference],
                    updatedAt = row[StatsCacheProjectRankingsTable.updatedAt],
                )
            }

    // --- Write operations ---

    override suspend fun saveSystemStats(stats: SystemStats) {
        StatsCacheSystemTable.upsert {
            it[term] = stats.term.value
            it[balance] = stats.balance
            it[difference] = stats.difference
            it[count] = stats.count
            it[total] = stats.total
            it[ratio] = stats.ratio
            it[updatedAt] = stats.updatedAt
        }
    }

    override suspend fun saveUsersStats(stats: UsersStats) {
        StatsCacheUsersTable.upsert {
            it[term] = stats.term.value
            it[number] = stats.number
            it[balance] = stats.balance
            it[difference] = stats.difference
            it[count] = stats.count
            it[total] = stats.total
            it[ratio] = stats.ratio
            it[updatedAt] = stats.updatedAt
        }
    }

    override suspend fun saveProjectsStats(stats: ProjectsStats) {
        StatsCacheProjectsTable.upsert {
            it[term] = stats.term.value
            it[number] = stats.number
            it[balance] = stats.balance
            it[difference] = stats.difference
            it[count] = stats.count
            it[total] = stats.total
            it[ratio] = stats.ratio
            it[updatedAt] = stats.updatedAt
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveUserRankings(entries: List<UserRankingEntry>) {
        entries.forEach { entry ->
            StatsCacheUserRankingsTable.upsert {
                it[term] = entry.term.value
                it[rankingType] = entry.rankingType.value
                it[userId] = entry.userId.value.toJavaUuid()
                it[rankValue] = entry.rankValue
                it[difference] = entry.difference
                it[updatedAt] = entry.updatedAt
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveProjectRankings(entries: List<ProjectRankingEntry>) {
        entries.forEach { entry ->
            StatsCacheProjectRankingsTable.upsert {
                it[term] = entry.term.value
                it[rankingType] = entry.rankingType.value
                it[projectId] = entry.projectId.value.toJavaUuid()
                it[rankValue] = entry.rankValue
                it[difference] = entry.difference
                it[updatedAt] = entry.updatedAt
            }
        }
    }

    override suspend fun clearUserRankings(
        term: StatsTerm,
        rankingType: RankingType,
    ) {
        StatsCacheUserRankingsTable.deleteWhere {
            (StatsCacheUserRankingsTable.term eq term.value) and
                (StatsCacheUserRankingsTable.rankingType eq rankingType.value)
        }
    }

    override suspend fun clearProjectRankings(
        term: StatsTerm,
        rankingType: RankingType,
    ) {
        StatsCacheProjectRankingsTable.deleteWhere {
            (StatsCacheProjectRankingsTable.term eq term.value) and
                (StatsCacheProjectRankingsTable.rankingType eq rankingType.value)
        }
    }
}
