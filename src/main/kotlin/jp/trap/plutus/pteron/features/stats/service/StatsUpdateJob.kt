package jp.trap.plutus.pteron.features.stats.service

import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.account.domain.model.Account
import jp.trap.plutus.pteron.features.project.domain.model.Project
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import jp.trap.plutus.pteron.features.stats.domain.model.*
import jp.trap.plutus.pteron.features.stats.domain.repository.StatsCacheRepository
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionRepository
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Single
class StatsUpdateJob(
    private val cacheRepository: StatsCacheRepository,
    private val transactionRepository: TransactionRepository,
    private val economicGateway: EconomicGateway,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val unitOfWork: UnitOfWork,
) {
    private val logger = LoggerFactory.getLogger(StatsUpdateJob::class.java)

    fun start(scope: CoroutineScope) {
        scope.launch {
            delay(10_000.milliseconds)

            while (isActive) {
                try {
                    updateAllStats()
                } catch (e: Exception) {
                    logger.error("Failed to update stats cache", e)
                }
                delay(1.minutes)
            }
        }
    }

    suspend fun updateAllStats() {
        logger.info("Starting stats cache update...")
        val startTime = System.currentTimeMillis()

        val users = unitOfWork.runInTransaction { userRepository.findAll() }
        val projects = unitOfWork.runInTransaction { projectRepository.findAll() }
        val accountIds = (users.map { it.accountId } + projects.map { it.accountId }).distinct()
        val accountBalanceMap = economicGateway.findAccountsByIds(accountIds).associateBy { it.accountId }

        for (term in Term.entries) {
            try {
                unitOfWork.runInTransaction {
                    updateSystemStats(term, users, projects, accountBalanceMap)
                    updateUsersAggregateStats(term, users, accountBalanceMap)
                    updateProjectsAggregateStats(term, projects, accountBalanceMap)

                    val userRankingsCurrentTerm = calculateAllUserRankings(term, users, accountBalanceMap)
                    val userRankingsPreviousTerm = calculateAllUserRankings(term, users, accountBalanceMap, previous = true)
                    val projectRankingsCurrentTerm = calculateAllProjectRankings(term, projects, accountBalanceMap)
                    val projectRankingsPreviousTerm =
                        calculateAllProjectRankings(term, projects, accountBalanceMap, previous = true)

                    for (rankingType in RankingType.entries) {
                        updateUserRankings(term, rankingType, userRankingsCurrentTerm, userRankingsPreviousTerm)
                        updateProjectRankings(
                            term,
                            rankingType,
                            projectRankingsCurrentTerm,
                            projectRankingsPreviousTerm,
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to update stats for term ${term.key}", e)
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("Stats cache update completed in ${elapsed}ms")
    }

    private suspend fun updateSystemStats(
        term: Term,
        users: List<User>,
        projects: List<Project>,
        accountBalanceMap: Map<AccountId, Account>,
    ) {
        val now = Clock.System.now()
        val since = now.minus(term.hours.hours)

        val currentBalance =
            (users.map { it.accountId } + projects.map { it.accountId })
                .distinct()
                .sumOf { accountBalanceMap[it]?.balance ?: 0L }

        // 期間内の取引統計
        val transactionStats = transactionRepository.getStats(since)

        // 期間開始時の残高を推定（現在残高 - 期間内変動）
        val previousBalance = currentBalance - transactionStats.netChange
        val difference = transactionStats.netChange
        val ratio =
            if (previousBalance != 0L) {
                ((difference.toDouble() / previousBalance) * 100).toLong()
            } else {
                0L
            }

        cacheRepository.saveSystemStats(
            term,
            SystemStats(
                balance = currentBalance,
                difference = difference,
                count = transactionStats.count,
                total = transactionStats.total,
                ratio = ratio,
            ),
        )
    }

    private suspend fun updateUsersAggregateStats(
        term: Term,
        users: List<User>,
        accountBalanceMap: Map<AccountId, Account>,
    ) {
        val now = Clock.System.now()
        val since = now.minus(term.hours.hours)

        val currentBalance = users.sumOf { accountBalanceMap[it.accountId]?.balance ?: 0L }
        val transactionStats = transactionRepository.getUsersStats(since)

        val previousBalance = currentBalance - transactionStats.netChange
        val difference = transactionStats.netChange
        val ratio =
            if (previousBalance != 0L) {
                ((difference.toDouble() / previousBalance) * 100).toLong()
            } else {
                0L
            }

        cacheRepository.saveUsersAggregateStats(
            term,
            AggregateStats(
                number = users.size.toLong(),
                balance = currentBalance,
                difference = difference,
                count = transactionStats.count,
                total = transactionStats.total,
                ratio = ratio,
            ),
        )
    }

    private suspend fun updateProjectsAggregateStats(
        term: Term,
        projects: List<Project>,
        accountBalanceMap: Map<AccountId, Account>,
    ) {
        val now = Clock.System.now()
        val since = now.minus(term.hours.hours)

        val currentBalance = projects.sumOf { accountBalanceMap[it.accountId]?.balance ?: 0L }
        val transactionStats = transactionRepository.getProjectsStats(since)

        val previousBalance = currentBalance - transactionStats.netChange
        val difference = transactionStats.netChange
        val ratio =
            if (previousBalance != 0L) {
                ((difference.toDouble() / previousBalance) * 100).toLong()
            } else {
                0L
            }

        cacheRepository.saveProjectsAggregateStats(
            term,
            AggregateStats(
                number = projects.size.toLong(),
                balance = currentBalance,
                difference = difference,
                count = transactionStats.count,
                total = transactionStats.total,
                ratio = ratio,
            ),
        )
    }

    private data class UserStatsData(
        val userId: UserId,
        val balance: Long,
        val difference: Long,
        val inAmount: Long,
        val outAmount: Long,
        val count: Long,
        val total: Long,
        val ratio: Long,
    )

    private data class ProjectStatsData(
        val projectId: ProjectId,
        val balance: Long,
        val difference: Long,
        val inAmount: Long,
        val outAmount: Long,
        val count: Long,
        val total: Long,
        val ratio: Long,
    )

    private suspend fun calculateAllUserRankings(
        term: Term,
        users: List<User>,
        accountBalanceMap: Map<AccountId, Account>,
        previous: Boolean = false,
    ): List<UserStatsData> {
        val now = Clock.System.now()
        val offset = if (previous) term.hours.hours else 0.hours
        val since = now.minus(term.hours.hours + offset)
        val until = if (previous) now.minus(offset) else now

        return users.map { user ->
            val balanceNow = accountBalanceMap[user.accountId]?.balance ?: 0L
            val stats = transactionRepository.getUserStats(user.id, since, until)

            // 期間終了時点の残高を計算
            // previous=true の場合: until時点の残高 = 今の残高 - (untilから今までの変化)
            val balanceAtEnd =
                if (previous) {
                    val changesAfterUntil = transactionRepository.getUserBalanceChangeAfter(user.id, until)
                    balanceNow - changesAfterUntil.netChange
                } else {
                    balanceNow
                }

            // 期間開始時点の残高 = 期間終了時点の残高 - 期間中の変化
            val balanceAtStart = balanceAtEnd - stats.netChange
            val ratio =
                if (balanceAtStart != 0L) {
                    ((stats.netChange.toDouble() / balanceAtStart) * 100).toLong()
                } else {
                    0L
                }

            UserStatsData(
                userId = user.id,
                balance = balanceAtEnd,
                difference = stats.netChange,
                inAmount = stats.inAmount,
                outAmount = stats.outAmount,
                count = stats.count,
                total = stats.total,
                ratio = ratio,
            )
        }
    }

    private suspend fun calculateAllProjectRankings(
        term: Term,
        projects: List<Project>,
        accountBalanceMap: Map<AccountId, Account>,
        previous: Boolean = false,
    ): List<ProjectStatsData> {
        val now = Clock.System.now()
        val offset = if (previous) term.hours.hours else 0.hours
        val since = now.minus(term.hours.hours + offset)
        val until = if (previous) now.minus(offset) else now

        return projects.map { project ->
            val balanceNow = accountBalanceMap[project.accountId]?.balance ?: 0L
            val stats = transactionRepository.getProjectStats(project.id, since, until)

            // 期間終了時点の残高を計算
            val balanceAtEnd =
                if (previous) {
                    val changesAfterUntil = transactionRepository.getProjectBalanceChangeAfter(project.id, until)
                    balanceNow - changesAfterUntil.netChange
                } else {
                    balanceNow
                }

            // 期間開始時点の残高 = 期間終了時点の残高 - 期間中の変化
            val balanceAtStart = balanceAtEnd - stats.netChange
            val ratio =
                if (balanceAtStart != 0L) {
                    ((stats.netChange.toDouble() / balanceAtStart) * 100).toLong()
                } else {
                    0L
                }

            ProjectStatsData(
                projectId = project.id,
                balance = balanceAtEnd,
                difference = stats.netChange,
                inAmount = stats.inAmount,
                outAmount = stats.outAmount,
                count = stats.count,
                total = stats.total,
                ratio = ratio,
            )
        }
    }

    private suspend fun updateUserRankings(
        term: Term,
        rankingType: RankingType,
        currentData: List<UserStatsData>,
        previousData: List<UserStatsData>,
    ) {
        val getValue: (UserStatsData) -> Long = { data ->
            when (rankingType) {
                RankingType.BALANCE -> data.balance
                RankingType.DIFFERENCE -> data.difference
                RankingType.IN -> data.inAmount
                RankingType.OUT -> data.outAmount
                RankingType.COUNT -> data.count
                RankingType.TOTAL -> data.total
                RankingType.RATIO -> data.ratio
            }
        }

        // 現在のランキング（降順）
        val sortedCurrent = currentData.sortedByDescending { getValue(it) }
        val currentRanked = mutableMapOf<UserId, Long>()
        sortedCurrent.forEachIndexed { index, data ->
            if (index > 0 && getValue(data) == getValue(sortedCurrent[index - 1])) {
                currentRanked[data.userId] = currentRanked[sortedCurrent[index - 1].userId]!!
            } else {
                currentRanked[data.userId] = index + 1L
            }
        }

        // 前期間のランキング
        val sortedPrevious = previousData.sortedByDescending { getValue(it) }
        val previousRanked = mutableMapOf<UserId, Long>()
        sortedPrevious.forEachIndexed { index, data ->
            if (index > 0 && getValue(data) == getValue(sortedPrevious[index - 1])) {
                previousRanked[data.userId] = previousRanked[sortedPrevious[index - 1].userId]!!
            } else {
                previousRanked[data.userId] = index + 1L
            }
        }

        val entries =
            currentData.map { data ->
                val currentRank = currentRanked[data.userId] ?: 0L
                val previousRank = previousRanked[data.userId] ?: currentRank
                val rankDifference = previousRank - currentRank // プラスなら順位上昇

                UserRankingEntry(
                    rank = currentRank,
                    value = getValue(data),
                    difference = rankDifference,
                    userId = data.userId,
                )
            }

        cacheRepository.clearUserRankings(term, rankingType)
        cacheRepository.saveUserRankings(term, rankingType, entries)
    }

    private suspend fun updateProjectRankings(
        term: Term,
        rankingType: RankingType,
        currentData: List<ProjectStatsData>,
        previousData: List<ProjectStatsData>,
    ) {
        val getValue: (ProjectStatsData) -> Long = { data ->
            when (rankingType) {
                RankingType.BALANCE -> data.balance
                RankingType.DIFFERENCE -> data.difference
                RankingType.IN -> data.inAmount
                RankingType.OUT -> data.outAmount
                RankingType.COUNT -> data.count
                RankingType.TOTAL -> data.total
                RankingType.RATIO -> data.ratio
            }
        }

        val sortedCurrent = currentData.sortedByDescending { getValue(it) }
        val currentRanked = mutableMapOf<ProjectId, Long>()
        sortedCurrent.forEachIndexed { index, data ->
            if (index > 0 && getValue(data) == getValue(sortedCurrent[index - 1])) {
                currentRanked[data.projectId] = currentRanked[sortedCurrent[index - 1].projectId]!!
            } else {
                currentRanked[data.projectId] = index + 1L
            }
        }

        val sortedPrevious = previousData.sortedByDescending { getValue(it) }
        val previousRanked = mutableMapOf<ProjectId, Long>()
        sortedPrevious.forEachIndexed { index, data ->
            if (index > 0 && getValue(data) == getValue(sortedPrevious[index - 1])) {
                previousRanked[data.projectId] = previousRanked[sortedPrevious[index - 1].projectId]!!
            } else {
                previousRanked[data.projectId] = index + 1L
            }
        }

        val entries =
            currentData.map { data ->
                val currentRank = currentRanked[data.projectId] ?: 0L
                val previousRank = previousRanked[data.projectId] ?: currentRank
                val rankDifference = previousRank - currentRank

                ProjectRankingEntry(
                    rank = currentRank,
                    value = getValue(data),
                    difference = rankDifference,
                    projectId = data.projectId,
                )
            }

        cacheRepository.clearProjectRankings(term, rankingType)
        cacheRepository.saveProjectRankings(term, rankingType, entries)
    }
}
