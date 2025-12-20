package jp.trap.plutus.pteron.features.stats.service

import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import jp.trap.plutus.pteron.features.stats.domain.model.*
import jp.trap.plutus.pteron.features.stats.domain.repository.StatsCacheRepository
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionType
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionQueryOptions
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionRepository
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import kotlinx.coroutines.*
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * 統計更新バックグラウンドジョブ
 */
@Single
class StatsUpdateJob(
    private val statsCacheRepository: StatsCacheRepository,
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val economicGateway: EconomicGateway,
    private val unitOfWork: UnitOfWork,
) {
    private val logger = LoggerFactory.getLogger(StatsUpdateJob::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    /**
     * 更新間隔（デフォルト: 5分）
     */
    private val updateInterval = 5.minutes

    /**
     * バックグラウンドジョブを開始
     */
    fun start() {
        if (job != null) {
            logger.warn("Stats update job is already running")
            return
        }

        job =
            scope.launch {
                logger.info("Starting stats update job with interval: $updateInterval")
                updateAllStats()

                while (isActive) {
                    delay(updateInterval)
                    updateAllStats()
                }
            }
    }

    /**
     * バックグラウンドジョブを停止
     */
    fun stop() {
        job?.cancel()
        job = null
        logger.info("Stats update job stopped")
    }

    /**
     * 全期間の統計を更新
     */
    private suspend fun updateAllStats() {
        logger.info("Updating all stats...")
        val startTime = Clock.System.now()

        try {
            StatsTerm.entries.forEach { term ->
                updateStatsForTerm(term)
            }

            val duration = Clock.System.now() - startTime
            logger.info("Stats update completed in $duration")
        } catch (e: Exception) {
            logger.error("Failed to update stats", e)
        }
    }

    /**
     * 特定期間の統計を更新
     */
    private suspend fun updateStatsForTerm(term: StatsTerm) {
        val now = Clock.System.now()
        val since = now - term.toDuration()
        val previousSince = since - term.toDuration()

        updateSystemStats(term, since, previousSince, now)
        updateUsersStats(term, since, previousSince, now)
        updateProjectsStats(term, since, previousSince, now)
        updateUserRankings(term, since, previousSince, now)
        updateProjectRankings(term, since, previousSince, now)
    }

    private suspend fun updateSystemStats(
        term: StatsTerm,
        since: Instant,
        previousSince: Instant,
        now: Instant,
    ) {
        unitOfWork.runInTransaction {
            val currentTransactions =
                transactionRepository
                    .findAll(
                        TransactionQueryOptions(limit = null, cursor = null, since = since),
                    ).items

            val previousTransactions =
                transactionRepository
                    .findAll(
                        TransactionQueryOptions(limit = null, cursor = null, since = previousSince),
                    ).items
                    .filter { it.createdAt < since }

            val users = userRepository.findAll()
            val projects = projectRepository.findAll()
            val allAccountIds = users.map { it.accountId } + projects.map { it.accountId }
            val accounts = economicGateway.findAccountsByIds(allAccountIds)
            val currentBalance = accounts.sumOf { it.balance }

            val currentTotal = currentTransactions.sumOf { it.amount }
            val previousTotal = previousTransactions.sumOf { it.amount }
            val difference = currentTotal - previousTotal

            val previousBalance = currentBalance - currentTotal + previousTotal
            val ratio =
                if (previousBalance != 0L) {
                    ((currentBalance - previousBalance) * 100 / previousBalance)
                } else {
                    0L
                }

            val stats =
                SystemStats(
                    term = term,
                    balance = currentBalance,
                    difference = difference,
                    count = currentTransactions.size.toLong(),
                    total = currentTotal,
                    ratio = ratio,
                    updatedAt = now,
                )

            statsCacheRepository.saveSystemStats(stats)
        }
    }

    private suspend fun updateUsersStats(
        term: StatsTerm,
        since: Instant,
        previousSince: Instant,
        now: Instant,
    ) {
        unitOfWork.runInTransaction {
            val users = userRepository.findAll()
            val userAccountIds = users.map { it.accountId }
            val accounts = economicGateway.findAccountsByIds(userAccountIds)
            val accountMap = accounts.associateBy { it.accountId }

            val currentBalance = users.sumOf { accountMap[it.accountId]?.balance ?: 0L }

            val currentTransactions =
                transactionRepository
                    .findAll(
                        TransactionQueryOptions(limit = null, cursor = null, since = since),
                    ).items

            val previousTransactions =
                transactionRepository
                    .findAll(
                        TransactionQueryOptions(limit = null, cursor = null, since = previousSince),
                    ).items
                    .filter { it.createdAt < since }

            val currentTotal = currentTransactions.sumOf { it.amount }
            val previousTotal = previousTransactions.sumOf { it.amount }
            val difference = currentTotal - previousTotal

            val previousBalance = currentBalance - currentTotal + previousTotal
            val ratio =
                if (previousBalance != 0L) {
                    ((currentBalance - previousBalance) * 100 / previousBalance)
                } else {
                    0L
                }

            val stats =
                UsersStats(
                    term = term,
                    number = users.size.toLong(),
                    balance = currentBalance,
                    difference = difference,
                    count = currentTransactions.size.toLong(),
                    total = currentTotal,
                    ratio = ratio,
                    updatedAt = now,
                )

            statsCacheRepository.saveUsersStats(stats)
        }
    }

    private suspend fun updateProjectsStats(
        term: StatsTerm,
        since: Instant,
        previousSince: Instant,
        now: Instant,
    ) {
        unitOfWork.runInTransaction {
            val projects = projectRepository.findAll()
            val projectAccountIds = projects.map { it.accountId }
            val accounts = economicGateway.findAccountsByIds(projectAccountIds)
            val accountMap = accounts.associateBy { it.accountId }

            val currentBalance = projects.sumOf { accountMap[it.accountId]?.balance ?: 0L }

            val currentTransactions =
                transactionRepository
                    .findAll(
                        TransactionQueryOptions(limit = null, cursor = null, since = since),
                    ).items

            val previousTransactions =
                transactionRepository
                    .findAll(
                        TransactionQueryOptions(limit = null, cursor = null, since = previousSince),
                    ).items
                    .filter { it.createdAt < since }

            val currentTotal = currentTransactions.sumOf { it.amount }
            val previousTotal = previousTransactions.sumOf { it.amount }
            val difference = currentTotal - previousTotal

            val previousBalance = currentBalance - currentTotal + previousTotal
            val ratio =
                if (previousBalance != 0L) {
                    ((currentBalance - previousBalance) * 100 / previousBalance)
                } else {
                    0L
                }

            val stats =
                ProjectsStats(
                    term = term,
                    number = projects.size.toLong(),
                    balance = currentBalance,
                    difference = difference,
                    count = currentTransactions.size.toLong(),
                    total = currentTotal,
                    ratio = ratio,
                    updatedAt = now,
                )

            statsCacheRepository.saveProjectsStats(stats)
        }
    }

    private suspend fun updateUserRankings(
        term: StatsTerm,
        since: Instant,
        previousSince: Instant,
        now: Instant,
    ) {
        unitOfWork.runInTransaction {
            val users = userRepository.findAll()
            val userAccountIds = users.map { it.accountId }
            val accounts = economicGateway.findAccountsByIds(userAccountIds)
            val accountMap = accounts.associateBy { it.accountId }

            // パフォーマンス: 全取引を一度に取得してからフィルタリング（N+1回避）
            val allCurrentTransactions =
                transactionRepository
                    .findAll(TransactionQueryOptions(limit = null, cursor = null, since = since))
                    .items

            val allPreviousTransactions =
                transactionRepository
                    .findAll(TransactionQueryOptions(limit = null, cursor = null, since = previousSince))
                    .items
                    .filter { it.createdAt < since }

            val currentTransactionsByUser = allCurrentTransactions.groupBy { it.userId }
            val previousTransactionsByUser = allPreviousTransactions.groupBy { it.userId }

            val userStats =
                users.map { user ->
                    val userCurrentTransactions = currentTransactionsByUser[user.id] ?: emptyList()
                    val userPreviousTransactions = previousTransactionsByUser[user.id] ?: emptyList()

                    val balance = accountMap[user.accountId]?.balance ?: 0L

                    val inAmount =
                        userCurrentTransactions
                            .filter { it.type == TransactionType.TRANSFER }
                            .sumOf { it.amount }
                    val outAmount =
                        userCurrentTransactions
                            .filter { it.type == TransactionType.BILL_PAYMENT }
                            .sumOf { it.amount }

                    val count = userCurrentTransactions.size.toLong()
                    val total = inAmount + outAmount

                    val netChange = inAmount - outAmount
                    val balanceAtStart = balance - netChange

                    val previousInAmount =
                        userPreviousTransactions
                            .filter { it.type == TransactionType.TRANSFER }
                            .sumOf { it.amount }
                    val previousOutAmount =
                        userPreviousTransactions
                            .filter { it.type == TransactionType.BILL_PAYMENT }
                            .sumOf { it.amount }
                    val previousNetChange = previousInAmount - previousOutAmount
                    val balanceAtPreviousStart = balanceAtStart - previousNetChange

                    val difference = netChange
                    val ratio =
                        if (balanceAtStart != 0L) {
                            (netChange * 100) / balanceAtStart
                        } else {
                            0L
                        }

                    UserStatsData(
                        userId = user.id,
                        balance = balance,
                        inAmount = inAmount,
                        outAmount = outAmount,
                        count = count,
                        total = total,
                        difference = difference,
                        ratio = ratio,
                    )
                }

            RankingType.entries.forEach { rankingType ->
                val previousRankMap = mutableMapOf<UserId, Long>()
                userStats.forEach { data ->
                    val previousRank = statsCacheRepository.getUserRank(term, rankingType, data.userId, false)
                    if (previousRank != null) {
                        previousRankMap[data.userId] = previousRank
                    }
                }

                val sortedStats =
                    when (rankingType) {
                        RankingType.BALANCE -> userStats.sortedByDescending { it.balance }
                        RankingType.DIFFERENCE -> userStats.sortedByDescending { it.difference }
                        RankingType.IN -> userStats.sortedByDescending { it.inAmount }
                        RankingType.OUT -> userStats.sortedByDescending { it.outAmount }
                        RankingType.COUNT -> userStats.sortedByDescending { it.count }
                        RankingType.TOTAL -> userStats.sortedByDescending { it.total }
                        RankingType.RATIO -> userStats.sortedByDescending { it.ratio }
                    }

                val entries =
                    sortedStats.mapIndexed { index, data ->
                        val newRank = index + 1L
                        val previousRank = previousRankMap[data.userId]
                        // difference: 前回順位 - 今回順位（正の値 = 順位が上がった）
                        val rankDifference = if (previousRank != null) previousRank - newRank else 0L

                        UserRankingEntry(
                            term = term,
                            rankingType = rankingType,
                            userId = data.userId,
                            rankValue =
                                when (rankingType) {
                                    RankingType.BALANCE -> data.balance
                                    RankingType.DIFFERENCE -> data.difference
                                    RankingType.IN -> data.inAmount
                                    RankingType.OUT -> data.outAmount
                                    RankingType.COUNT -> data.count
                                    RankingType.TOTAL -> data.total
                                    RankingType.RATIO -> data.ratio
                                },
                            difference = rankDifference,
                            updatedAt = now,
                        )
                    }

                statsCacheRepository.clearUserRankings(term, rankingType)
                statsCacheRepository.saveUserRankings(entries)
            }
        }
    }

    private suspend fun updateProjectRankings(
        term: StatsTerm,
        since: Instant,
        previousSince: Instant,
        now: Instant,
    ) {
        unitOfWork.runInTransaction {
            val projects = projectRepository.findAll()
            val projectAccountIds = projects.map { it.accountId }
            val accounts = economicGateway.findAccountsByIds(projectAccountIds)
            val accountMap = accounts.associateBy { it.accountId }

            val allCurrentTransactions =
                transactionRepository
                    .findAll(TransactionQueryOptions(limit = null, cursor = null, since = since))
                    .items

            val allPreviousTransactions =
                transactionRepository
                    .findAll(TransactionQueryOptions(limit = null, cursor = null, since = previousSince))
                    .items
                    .filter { it.createdAt < since }

            val currentTransactionsByProject = allCurrentTransactions.groupBy { it.projectId }
            val previousTransactionsByProject = allPreviousTransactions.groupBy { it.projectId }

            val projectStats =
                projects.map { project ->
                    val projectCurrentTransactions = currentTransactionsByProject[project.id] ?: emptyList()
                    val projectPreviousTransactions = previousTransactionsByProject[project.id] ?: emptyList()

                    val balance = accountMap[project.accountId]?.balance ?: 0L

                    val outAmount =
                        projectCurrentTransactions
                            .filter { it.type == TransactionType.TRANSFER }
                            .sumOf { it.amount }
                    val inAmount =
                        projectCurrentTransactions
                            .filter { it.type == TransactionType.BILL_PAYMENT }
                            .sumOf { it.amount }

                    val count = projectCurrentTransactions.size.toLong()
                    val total = inAmount + outAmount

                    val netChange = inAmount - outAmount
                    val balanceAtStart = balance - netChange

                    val previousOutAmount =
                        projectPreviousTransactions
                            .filter { it.type == TransactionType.TRANSFER }
                            .sumOf { it.amount }
                    val previousInAmount =
                        projectPreviousTransactions
                            .filter { it.type == TransactionType.BILL_PAYMENT }
                            .sumOf { it.amount }
                    val previousNetChange = previousInAmount - previousOutAmount

                    val difference = netChange
                    val ratio =
                        if (balanceAtStart != 0L) {
                            (netChange * 100) / balanceAtStart
                        } else {
                            0L
                        }

                    ProjectStatsData(
                        projectId = project.id,
                        balance = balance,
                        inAmount = inAmount,
                        outAmount = outAmount,
                        count = count,
                        total = total,
                        difference = difference,
                        ratio = ratio,
                    )
                }

            RankingType.entries.forEach { rankingType ->
                // 更新前の現在のランキングを取得（順位比較のため）
                val previousRankMap = mutableMapOf<ProjectId, Long>()
                projectStats.forEach { data ->
                    val previousRank = statsCacheRepository.getProjectRank(term, rankingType, data.projectId, false)
                    if (previousRank != null) {
                        previousRankMap[data.projectId] = previousRank
                    }
                }

                val sortedStats =
                    when (rankingType) {
                        RankingType.BALANCE -> projectStats.sortedByDescending { it.balance }
                        RankingType.DIFFERENCE -> projectStats.sortedByDescending { it.difference }
                        RankingType.IN -> projectStats.sortedByDescending { it.inAmount }
                        RankingType.OUT -> projectStats.sortedByDescending { it.outAmount }
                        RankingType.COUNT -> projectStats.sortedByDescending { it.count }
                        RankingType.TOTAL -> projectStats.sortedByDescending { it.total }
                        RankingType.RATIO -> projectStats.sortedByDescending { it.ratio }
                    }

                val entries =
                    sortedStats.mapIndexed { index, data ->
                        val newRank = index + 1L
                        val previousRank = previousRankMap[data.projectId]
                        // difference: 前回順位 - 今回順位（正の値 = 順位が上がった）
                        val rankDifference = if (previousRank != null) previousRank - newRank else 0L

                        ProjectRankingEntry(
                            term = term,
                            rankingType = rankingType,
                            projectId = data.projectId,
                            rankValue =
                                when (rankingType) {
                                    RankingType.BALANCE -> data.balance
                                    RankingType.DIFFERENCE -> data.difference
                                    RankingType.IN -> data.inAmount
                                    RankingType.OUT -> data.outAmount
                                    RankingType.COUNT -> data.count
                                    RankingType.TOTAL -> data.total
                                    RankingType.RATIO -> data.ratio
                                },
                            difference = rankDifference,
                            updatedAt = now,
                        )
                    }

                statsCacheRepository.clearProjectRankings(term, rankingType)
                statsCacheRepository.saveProjectRankings(entries)
            }
        }
    }

    private fun StatsTerm.toDuration() =
        when (this) {
            StatsTerm.HOURS_24 -> 24.hours
            StatsTerm.DAYS_7 -> 7.days
            StatsTerm.DAYS_30 -> 30.days
            StatsTerm.DAYS_365 -> 365.days
        }

    private data class UserStatsData(
        val userId: UserId,
        val balance: Long,
        val inAmount: Long,
        val outAmount: Long,
        val count: Long,
        val total: Long,
        val difference: Long,
        val ratio: Long,
    )

    private data class ProjectStatsData(
        val projectId: ProjectId,
        val balance: Long,
        val inAmount: Long,
        val outAmount: Long,
        val count: Long,
        val total: Long,
        val difference: Long,
        val ratio: Long,
    )
}
