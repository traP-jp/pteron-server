package jp.trap.plutus.pteron.features.transaction.controller

import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.account.domain.model.Account
import jp.trap.plutus.pteron.features.account.service.AccountService
import jp.trap.plutus.pteron.features.project.domain.model.Project
import jp.trap.plutus.pteron.features.project.service.ProjectService
import jp.trap.plutus.pteron.features.transaction.domain.model.Transaction
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionQueryOptions
import jp.trap.plutus.pteron.features.transaction.service.TransactionService
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.service.UserService
import jp.trap.plutus.pteron.openapi.internal.Paths
import jp.trap.plutus.pteron.openapi.internal.models.GetTransactions200Response
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import jp.trap.plutus.pteron.openapi.internal.models.Project as ProjectDto
import jp.trap.plutus.pteron.openapi.internal.models.Transaction as TransactionDto
import jp.trap.plutus.pteron.openapi.internal.models.User as UserDto

fun Route.transactionRoutes() {
    val transactionService by inject<TransactionService>()
    val userService by inject<UserService>()
    val projectService by inject<ProjectService>()
    val accountService by inject<AccountService>()

    // GET /transactions
    get<Paths.getTransactions> { params ->
        val options = createQueryOptions(params.term, params.limit, params.cursor)
        val result = transactionService.getTransactions(options)
        val transactionDtos = createTransactionDtos(result.items, userService, projectService, accountService)
        call.respond(GetTransactions200Response(items = transactionDtos, nextCursor = result.nextCursor))
    }

    // GET /transactions/users/{user_id}
    get<Paths.getUserTransactions> { params ->
        val userId = UserId(Uuid.parse(params.userId))
        val options = createQueryOptions(params.term, params.limit, params.cursor)
        val result = transactionService.getUserTransactions(userId, options)
        val transactionDtos = createTransactionDtos(result.items, userService, projectService, accountService)
        call.respond(GetTransactions200Response(items = transactionDtos, nextCursor = result.nextCursor))
    }

    // GET /transactions/projects/{project_id}
    get<Paths.getProjectTransactions> { params ->
        val projectId = ProjectId(Uuid.parse(params.projectId))
        val options = createQueryOptions(params.term, params.limit, params.cursor)
        val result = transactionService.getProjectTransactions(projectId, options)
        val transactionDtos = createTransactionDtos(result.items, userService, projectService, accountService)
        call.respond(GetTransactions200Response(items = transactionDtos, nextCursor = result.nextCursor))
    }
}

/**
 * termパラメータから期間のsinceを計算してQueryOptionsを作成
 */
private fun createQueryOptions(
    term: String?,
    limit: Int?,
    cursor: String?,
): TransactionQueryOptions {
    val since =
        term?.let {
            val duration =
                when (it) {
                    "24hours" -> 24.hours
                    "7days" -> 7.days
                    "30days" -> 30.days
                    "365days" -> 365.days
                    else -> null
                }
            duration?.let { d -> Clock.System.now() - d }
        }

    // termが指定されている場合は全件取得（limitとcursorを無視）
    return if (term != null && since != null) {
        TransactionQueryOptions(limit = null, cursor = null, since = since)
    } else {
        TransactionQueryOptions(limit = limit ?: 20, cursor = cursor, since = null)
    }
}

private suspend fun createTransactionDtos(
    transactions: List<Transaction>,
    userService: UserService,
    projectService: ProjectService,
    accountService: AccountService,
): List<TransactionDto> {
    if (transactions.isEmpty()) return emptyList()

    // ユーザー情報を一括取得
    val userIds = transactions.map { it.userId }.distinct()
    val users = userService.getUsersByIds(userIds)
    val userMap = users.associateBy { it.id }

    // プロジェクト情報を一括取得
    val projectIds = transactions.map { it.projectId }.distinct()
    val projects = projectService.getProjectsByIds(projectIds)
    val projectMap = projects.associateBy { it.id }

    // アカウント情報を一括取得
    val accountIds = (users.map { it.accountId } + projects.map { it.accountId }).distinct()
    val accounts = accountService.getAccountsByIds(accountIds)
    val accountMap = accounts.associateBy { it.accountId }

    return transactions.map { transaction ->
        val user = userMap[transaction.userId]
            ?: throw IllegalStateException("User not found: ${transaction.userId}")
        val project = projectMap[transaction.projectId]
            ?: throw IllegalStateException("Project not found: ${transaction.projectId}")
        val userAccount = accountMap[user.accountId]
            ?: throw IllegalStateException("User account not found: ${user.accountId}")
        val projectAccount = accountMap[project.accountId]
            ?: throw IllegalStateException("Project account not found: ${project.accountId}")

        createTransactionDto(transaction, user, userAccount, project, projectAccount, userMap, accountMap)
    }
}

private fun createTransactionDto(
    transaction: Transaction,
    user: User,
    userAccount: Account,
    project: Project,
    projectAccount: Account,
    userMap: Map<UserId, User>,
    accountMap: Map<jp.trap.plutus.pteron.common.domain.model.AccountId, Account>,
): TransactionDto =
    TransactionDto(
        id = transaction.id.value,
        type = TransactionDto.Type.valueOf(transaction.type.name),
        amount = transaction.amount,
        project = createProjectDto(project, projectAccount, userMap, accountMap),
        user = createUserDto(user, userAccount),
        description = transaction.description,
        createdAt = transaction.createdAt,
    )

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
    accountMap: Map<jp.trap.plutus.pteron.common.domain.model.AccountId, Account>,
): ProjectDto {
    val owner = userMap[project.ownerId]
        ?: throw IllegalStateException("Owner not found: ${project.ownerId}")
    val ownerAccount = accountMap[owner.accountId]
        ?: throw IllegalStateException("Owner account not found: ${owner.accountId}")

    val adminDtos = project.adminIds.mapNotNull { adminId ->
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
