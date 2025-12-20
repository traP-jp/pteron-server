package jp.trap.plutus.pteron.features.stats.controller

import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.account.domain.model.Account
import jp.trap.plutus.pteron.features.account.service.AccountService
import jp.trap.plutus.pteron.features.project.domain.model.Project
import jp.trap.plutus.pteron.features.project.service.ProjectService
import jp.trap.plutus.pteron.features.stats.domain.model.ProjectRankingEntry
import jp.trap.plutus.pteron.features.stats.domain.model.RankingType
import jp.trap.plutus.pteron.features.stats.domain.model.StatsTerm
import jp.trap.plutus.pteron.features.stats.domain.model.UserRankingEntry
import jp.trap.plutus.pteron.features.stats.service.StatsService
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.service.UserService
import jp.trap.plutus.pteron.openapi.internal.Paths
import jp.trap.plutus.pteron.openapi.internal.models.*
import org.koin.ktor.ext.inject
import jp.trap.plutus.pteron.openapi.internal.models.Project as ProjectDto
import jp.trap.plutus.pteron.openapi.internal.models.User as UserDto

fun Route.statsRoutes() {
    val statsService by inject<StatsService>()
    val userService by inject<UserService>()
    val projectService by inject<ProjectService>()
    val accountService by inject<AccountService>()

    // GET /stats - 経済圏全体の統計情報
    get<Paths.getSystemStats> { params ->
        val term = StatsTerm.fromString(params.term)
        val stats = statsService.getSystemStats(term)

        call.respond(
            GetSystemStats200Response(
                balance = stats.balance,
                difference = stats.difference,
                count = stats.count,
                total = stats.total,
                ratio = stats.ratio,
            ),
        )
    }

    // GET /stats/users - ユーザー関連の統計情報
    get<Paths.getUsersStats> { params ->
        val term = StatsTerm.fromString(params.term)
        val stats = statsService.getUsersStats(term)

        call.respond(
            GetUsersStats200Response(
                number = stats.number,
                balance = stats.balance,
                difference = stats.difference,
                count = stats.count,
                total = stats.total,
                ratio = stats.ratio,
            ),
        )
    }

    // GET /stats/projects - プロジェクト関連の統計情報
    get<Paths.getProjectsStats> { params ->
        val term = StatsTerm.fromString(params.term)
        val stats = statsService.getProjectsStats(term)

        call.respond(
            GetUsersStats200Response(
                // Same structure as users stats
                number = stats.number,
                balance = stats.balance,
                difference = stats.difference,
                count = stats.count,
                total = stats.total,
                ratio = stats.ratio,
            ),
        )
    }

    // GET /stats/users/{rankingName} - ユーザーランキング
    get<Paths.getUserRankings> { params ->
        val rankingType = RankingType.fromString(params.rankingName)
        val term = StatsTerm.fromString(params.term)
        val ascending = params.order == "asc"
        val limit = params.limit ?: 20

        val result = statsService.getUserRankings(rankingType, term, ascending, limit, params.cursor)

        // ユーザー情報を取得
        val userIds = result.items.map { it.userId }
        val users = userService.getUsersByIds(userIds)
        val userMap = users.associateBy { it.id }

        // アカウント情報を取得
        val accountIds = users.map { it.accountId }
        val accounts = accountService.getAccountsByIds(accountIds)
        val accountMap = accounts.associateBy { it.accountId }

        val items =
            result.items.mapIndexedNotNull { index, entry ->
                val user = userMap[entry.userId] ?: return@mapIndexedNotNull null
                val account = accountMap[user.accountId] ?: return@mapIndexedNotNull null

                GetUserRankings200ResponseItemsInner(
                    rank = index + 1L,
                    value = entry.rankValue,
                    difference = entry.difference,
                    user = createUserDto(user, account),
                )
            }

        call.respond(
            GetUserRankings200Response(
                items = items,
                nextCursor = result.nextCursor,
            ),
        )
    }

    // GET /stats/projects/{projectName} - プロジェクトランキング
    get<Paths.getProjectRankings> { params ->
        val rankingType = RankingType.fromString(params.projectName)
        val term = StatsTerm.fromString(params.term)
        val ascending = params.order == "asc"
        val limit = params.limit ?: 20

        val result = statsService.getProjectRankings(rankingType, term, ascending, limit, params.cursor)

        // プロジェクト情報を取得
        val projectIds = result.items.map { it.projectId }
        val projects = projectService.getProjectsByIds(projectIds)
        val projectMap = projects.associateBy { it.id }

        // ユーザー情報を取得
        val ownerIds = projects.map { it.ownerId }
        val adminIds = projects.flatMap { it.adminIds }
        val allUserIds = (ownerIds + adminIds).distinct()
        val users = userService.getUsersByIds(allUserIds)
        val userMap = users.associateBy { it.id }

        // アカウント情報を取得
        val accountIds = (users.map { it.accountId } + projects.map { it.accountId }).distinct()
        val accounts = accountService.getAccountsByIds(accountIds)
        val accountMap = accounts.associateBy { it.accountId }

        val items =
            result.items.mapIndexedNotNull { index, entry ->
                val project = projectMap[entry.projectId] ?: return@mapIndexedNotNull null
                val projectAccount = accountMap[project.accountId] ?: return@mapIndexedNotNull null

                GetProjectRankings200ResponseItemsInner(
                    rank = index + 1L,
                    value = entry.rankValue,
                    difference = entry.difference,
                    project = createProjectDto(project, projectAccount, userMap, accountMap),
                )
            }

        call.respond(
            GetProjectRankings200Response(
                items = items,
                nextCursor = result.nextCursor,
            ),
        )
    }

    // GET /users/{userId}/stats - 特定ユーザーのランキング順位一覧
    get<Paths.getUserStats> { params ->
        val user = userService.getUser(params.userId)
        val term = StatsTerm.fromString(params.term)

        val stats = statsService.getUserStats(user.id, term)
        val account = accountService.getAccountById(user.accountId)

        val userDto = createUserDto(user, account)

        fun createRankingItem(entry: UserRankingEntry?): GetUserRankings200ResponseItemsInner =
            GetUserRankings200ResponseItemsInner(
                rank = 0L, // 実際のランクは別途計算が必要
                value = entry?.rankValue ?: 0L,
                difference = entry?.difference ?: 0L,
                user = userDto,
            )

        call.respond(
            GetUserStats200Response(
                balance = createRankingItem(stats[RankingType.BALANCE]),
                difference = createRankingItem(stats[RankingType.DIFFERENCE]),
                `in` = createRankingItem(stats[RankingType.IN]),
                `out` = createRankingItem(stats[RankingType.OUT]),
                count = createRankingItem(stats[RankingType.COUNT]),
                total = createRankingItem(stats[RankingType.TOTAL]),
                ratio = createRankingItem(stats[RankingType.RATIO]),
            ),
        )
    }

    // GET /projects/{projectId}/stats - 特定プロジェクトのランキング順位一覧
    get<Paths.getProjectStats> { params ->
        val project = projectService.getProject(params.projectId)
        val term = StatsTerm.fromString(params.term)

        val stats = statsService.getProjectStats(project.id, term)

        // ユーザー情報を取得
        val allUserIds = listOf(project.ownerId) + project.adminIds
        val users = userService.getUsersByIds(allUserIds)
        val userMap = users.associateBy { it.id }

        // アカウント情報を取得
        val accountIds = (users.map { it.accountId } + project.accountId).distinct()
        val accounts = accountService.getAccountsByIds(accountIds)
        val accountMap = accounts.associateBy { it.accountId }

        val projectAccount = accountMap[project.accountId]!!
        val projectDto = createProjectDto(project, projectAccount, userMap, accountMap)

        fun createRankingItem(entry: ProjectRankingEntry?): GetProjectRankings200ResponseItemsInner =
            GetProjectRankings200ResponseItemsInner(
                rank = 0L,
                value = entry?.rankValue ?: 0L,
                difference = entry?.difference ?: 0L,
                project = projectDto,
            )

        call.respond(
            GetProjectStats200Response(
                balance = createRankingItem(stats[RankingType.BALANCE]),
                difference = createRankingItem(stats[RankingType.DIFFERENCE]),
                `in` = createRankingItem(stats[RankingType.IN]),
                `out` = createRankingItem(stats[RankingType.OUT]),
                count = createRankingItem(stats[RankingType.COUNT]),
                total = createRankingItem(stats[RankingType.TOTAL]),
                ratio = createRankingItem(stats[RankingType.RATIO]),
            ),
        )
    }
}

private fun createUserDto(
    user: User,
    account: Account,
): UserDto =
    UserDto(
        id = user.id.value,
        name = user.name.value,
        balance = account.balance,
    )

private fun createProjectDto(
    project: Project,
    projectAccount: Account,
    userMap: Map<UserId, User>,
    accountMap: Map<AccountId, Account>,
): ProjectDto {
    val owner =
        userMap[project.ownerId]
            ?: throw IllegalStateException("Owner not found: ${project.ownerId}")
    val ownerAccount =
        accountMap[owner.accountId]
            ?: throw IllegalStateException("Owner account not found: ${owner.accountId}")

    val adminDtos =
        project.adminIds.mapNotNull { adminId ->
            val admin = userMap[adminId] ?: return@mapNotNull null
            val adminAccount = accountMap[admin.accountId] ?: return@mapNotNull null
            createUserDto(admin, adminAccount)
        }

    return ProjectDto(
        id = project.id.value,
        name = project.name.value,
        owner = createUserDto(owner, ownerAccount),
        admins = adminDtos,
        balance = projectAccount.balance,
        url = project.url?.value,
    )
}
