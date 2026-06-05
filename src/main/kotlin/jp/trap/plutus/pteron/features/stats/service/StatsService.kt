package jp.trap.plutus.pteron.features.stats.service

import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.exception.NotFoundException
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.account.domain.model.Account
import jp.trap.plutus.pteron.features.project.domain.model.Project
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import jp.trap.plutus.pteron.features.stats.domain.model.*
import jp.trap.plutus.pteron.features.stats.domain.repository.StatsCacheRepository
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionRepository
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import org.koin.core.annotation.Single
import kotlin.time.Instant

/**
 * ユーザーランキング結果（ユーザー情報付き）
 */
data class UserRankingResult(
    val items: List<UserRankingItem>,
    val nextCursor: String?,
)

data class UserRankingItem(
    val rank: Long,
    val value: Long,
    val difference: Long,
    val user: User,
    val balance: Long,
)

/**
 * プロジェクトランキング結果（プロジェクト情報付き）
 */
data class ProjectRankingResult(
    val items: List<ProjectRankingItem>,
    val nextCursor: String?,
)

data class ProjectRankingItem(
    val rank: Long,
    val value: Long,
    val difference: Long,
    val project: Project,
    val projectBalance: Long,
    val owner: User,
    val ownerBalance: Long,
    val admins: List<AdminInfo>,
)

data class AdminInfo(
    val user: User,
    val balance: Long,
)

/**
 * ユーザー個別統計結果
 */
data class UserStatsResult(
    val stats: IndividualStats,
    val user: User,
    val balance: Long,
)

/**
 * プロジェクト個別統計結果
 */
data class ProjectStatsResult(
    val stats: IndividualStats,
    val project: Project,
    val projectBalance: Long,
    val owner: User,
    val ownerBalance: Long,
    val admins: List<AdminInfo>,
)

@Single
class StatsService(
    private val statsCacheRepository: StatsCacheRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val transactionRepository: TransactionRepository,
    private val economicGateway: EconomicGateway,
    private val unitOfWork: UnitOfWork,
) {
    suspend fun getSystemStats(term: Term): SystemStats =
        unitOfWork.runInTransaction {
            statsCacheRepository.getSystemStats(term)
        } ?: throw NotFoundException("Stats not available yet")

    suspend fun getUsersAggregateStats(term: Term): AggregateStats =
        unitOfWork.runInTransaction {
            statsCacheRepository.getUsersAggregateStats(term)
        } ?: throw NotFoundException("Stats not available yet")

    suspend fun getProjectsAggregateStats(term: Term): AggregateStats =
        unitOfWork.runInTransaction {
            statsCacheRepository.getProjectsAggregateStats(term)
        } ?: throw NotFoundException("Stats not available yet")

    suspend fun getUserRankings(
        rankingType: RankingType,
        term: Term,
        ascending: Boolean = false,
        limit: Int = 20,
        cursor: String? = null,
    ): UserRankingResult {
        val (result, users) =
            unitOfWork.runInTransaction {
                val result = statsCacheRepository.getUserRankings(rankingType, term, ascending, limit, cursor)
                val userIds = result.items.map { it.userId }
                val users = userRepository.findByIds(userIds)

                result to users
            }

        val usersMap = users.associateBy { it.id }
        val accountBalanceMap = findAccountBalanceMap(users.map { it.accountId })

        val items =
            result.items.mapNotNull { entry ->
                val user = usersMap[entry.userId] ?: return@mapNotNull null
                val balance = accountBalanceMap[user.accountId]?.balance ?: 0L
                UserRankingItem(
                    rank = entry.rank,
                    value = entry.value,
                    difference = entry.difference,
                    user = user,
                    balance = balance,
                )
            }

        return UserRankingResult(items, result.nextCursor)
    }

    suspend fun getProjectRankings(
        rankingType: RankingType,
        term: Term,
        ascending: Boolean = false,
        limit: Int = 20,
        cursor: String? = null,
    ): ProjectRankingResult {
        val rankingData =
            unitOfWork.runInTransaction {
                val result = statsCacheRepository.getProjectRankings(rankingType, term, ascending, limit, cursor)
                val projects = projectRepository.findAll()
                val ownerIds = projects.map { it.ownerId }.distinct()
                val owners = userRepository.findByIds(ownerIds)
                val allAdminIds = projects.flatMap { it.adminIds }.distinct()
                val allAdmins = userRepository.findByIds(allAdminIds)

                ProjectRankingData(
                    result = result,
                    projects = projects,
                    owners = owners,
                    admins = allAdmins,
                )
            }

        val projectsMap = rankingData.projects.associateBy { it.id }
        val ownersMap = rankingData.owners.associateBy { it.id }
        val adminsMap = rankingData.admins.associateBy { it.id }
        val accountIds =
            rankingData.projects.map { it.accountId } +
                rankingData.owners.map { it.accountId } +
                rankingData.admins.map { it.accountId }
        val accountBalanceMap = findAccountBalanceMap(accountIds)

        val items =
            rankingData.result.items.mapNotNull { entry ->
                val project = projectsMap[entry.projectId] ?: return@mapNotNull null
                val owner = ownersMap[project.ownerId] ?: return@mapNotNull null
                val projectBalance = accountBalanceMap[project.accountId]?.balance ?: 0L
                val ownerBalance = accountBalanceMap[owner.accountId]?.balance ?: 0L

                val admins =
                    project.adminIds.mapNotNull { adminId ->
                        val admin = adminsMap[adminId] ?: return@mapNotNull null
                        val adminBalance = accountBalanceMap[admin.accountId]?.balance ?: 0L
                        AdminInfo(admin, adminBalance)
                    }

                ProjectRankingItem(
                    rank = entry.rank,
                    value = entry.value,
                    difference = entry.difference,
                    project = project,
                    projectBalance = projectBalance,
                    owner = owner,
                    ownerBalance = ownerBalance,
                    admins = admins,
                )
            }

        return ProjectRankingResult(items, rankingData.result.nextCursor)
    }

    suspend fun getUserStats(
        userId: UserId,
        term: Term,
    ): UserStatsResult {
        val (user, stats) =
            unitOfWork.runInTransaction {
                val user =
                    userRepository.findById(userId)
                        ?: throw NotFoundException("User not found")

                val stats =
                    statsCacheRepository.getUserStats(userId, term)
                        ?: throw NotFoundException("Stats not available for this user")

                user to stats
            }

        val balance = economicGateway.findAccountById(user.accountId)?.balance ?: 0L

        return UserStatsResult(stats, user, balance)
    }

    suspend fun getProjectStats(
        projectId: ProjectId,
        term: Term,
    ): ProjectStatsResult {
        val statsData =
            unitOfWork.runInTransaction {
                val project =
                    projectRepository.findById(projectId)
                        ?: throw NotFoundException("Project not found")

                val stats =
                    statsCacheRepository.getProjectStats(projectId, term)
                        ?: throw NotFoundException("Stats not available for this project")

                val owner =
                    userRepository.findById(project.ownerId)
                        ?: throw NotFoundException("Owner not found")

                val adminUsers = userRepository.findByIds(project.adminIds)

                ProjectStatsData(
                    project = project,
                    stats = stats,
                    owner = owner,
                    adminUsers = adminUsers,
                )
            }

        val accountIds =
            listOf(statsData.project.accountId, statsData.owner.accountId) + statsData.adminUsers.map { it.accountId }
        val accountBalanceMap = findAccountBalanceMap(accountIds)

        val projectBalance = accountBalanceMap[statsData.project.accountId]?.balance ?: 0L
        val ownerBalance = accountBalanceMap[statsData.owner.accountId]?.balance ?: 0L

        val admins =
            statsData.adminUsers.map { admin ->
                val adminBalance = accountBalanceMap[admin.accountId]?.balance ?: 0L
                AdminInfo(admin, adminBalance)
            }

        return ProjectStatsResult(
            statsData.stats,
            statsData.project,
            projectBalance,
            statsData.owner,
            ownerBalance,
            admins,
        )
    }

    private data class ProjectRankingData(
        val result: RankingQueryResult<ProjectRankingEntry>,
        val projects: List<Project>,
        val owners: List<User>,
        val admins: List<User>,
    )

    private data class ProjectStatsData(
        val project: Project,
        val stats: IndividualStats,
        val owner: User,
        val adminUsers: List<User>,
    )

    private suspend fun findAccountBalanceMap(accountIds: List<AccountId>): Map<AccountId, Account> =
        economicGateway.findAccountsByIds(accountIds.distinct()).associateBy { it.accountId }

    suspend fun getUserBalanceAt(
        userId: UserId,
        at: Instant,
    ): Long {
        val (accountId, balanceChange) =
            unitOfWork.runInTransaction {
                val user = userRepository.findById(userId) ?: throw NotFoundException("User not found")
                val balanceChange = transactionRepository.getUserBalanceChangeAfter(userId, at)

                user.accountId to balanceChange
            }

        val currentBalance = economicGateway.findAccountById(accountId)?.balance ?: throw NotFoundException("User not found")
        return currentBalance - balanceChange.netChange
    }

    suspend fun getProjectBalanceAt(
        projectId: ProjectId,
        at: Instant,
    ): Long {
        val (accountId, balanceChange) =
            unitOfWork.runInTransaction {
                val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
                val balanceChange = transactionRepository.getProjectBalanceChangeAfter(projectId, at)

                project.accountId to balanceChange
            }

        val currentBalance = economicGateway.findAccountById(accountId)?.balance ?: throw NotFoundException("Project not found")
        return currentBalance - balanceChange.netChange
    }
}
