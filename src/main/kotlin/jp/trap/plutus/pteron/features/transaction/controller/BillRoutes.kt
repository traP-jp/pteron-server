package jp.trap.plutus.pteron.features.transaction.controller

import io.ktor.http.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.pteron.features.account.service.AccountService
import jp.trap.plutus.pteron.features.project.service.ProjectService
import jp.trap.plutus.pteron.features.transaction.domain.model.BillId
import jp.trap.plutus.pteron.features.transaction.service.BillService
import jp.trap.plutus.pteron.features.user.service.UserService
import jp.trap.plutus.pteron.openapi.internal.Paths
import jp.trap.plutus.pteron.openapi.internal.models.ApproveBill200Response
import jp.trap.plutus.pteron.utils.trapId
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid
import jp.trap.plutus.pteron.openapi.internal.models.Bill as BillDto
import jp.trap.plutus.pteron.openapi.internal.models.Project as ProjectDto
import jp.trap.plutus.pteron.openapi.internal.models.User as UserDto

fun Route.billRoutes() {
    val billService by inject<BillService>()
    val userService by inject<UserService>()
    val projectService by inject<ProjectService>()
    val accountService by inject<AccountService>()

    // GET /me/bills/{bill_id}
    get<Paths.getBill> { params ->
        val billId = BillId(Uuid.parse(params.billId))
        val currentUser = userService.getUserByName(call.trapId)
        val bill = billService.getBill(billId)

        // 自分宛の請求のみ参照可能
        if (bill.userId != currentUser.id) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val billDto = createBillDto(bill, userService, projectService, accountService)
        call.respond(billDto)
    }

    // POST /me/bills/{bill_id}/approve
    post<Paths.approveBill> { params ->
        val billId = BillId(Uuid.parse(params.billId))
        val currentUser = userService.getUserByName(call.trapId)

        val result = billService.approveBill(billId, currentUser.id)

        // 成功時はプロジェクトのURLをリダイレクト先として返す
        val project = projectService.getProjectDetails(result.bill.projectId)
        val redirectUrl = project.url?.value ?: "/"

        call.respond(ApproveBill200Response(redirectUrl = redirectUrl))
    }

    // POST /me/bills/{bill_id}/decline
    post<Paths.declineBill> { params ->
        val billId = BillId(Uuid.parse(params.billId))
        val currentUser = userService.getUserByName(call.trapId)

        billService.declineBill(billId, currentUser.id)

        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun createBillDto(
    bill: jp.trap.plutus.pteron.features.transaction.domain.model.Bill,
    userService: UserService,
    projectService: ProjectService,
    accountService: AccountService,
): BillDto {
    val user = userService.getUser(bill.userId.value.toString())
    val project = projectService.getProjectDetails(bill.projectId)

    val userAccount = accountService.getAccountById(user.accountId)
    val projectAccount = accountService.getAccountById(project.accountId)

    // プロジェクトのadmin情報を取得
    val adminUsers = userService.getUsersByIds(project.adminIds)
    val adminAccountIds = adminUsers.map { it.accountId }
    val adminAccounts = accountService.getAccountsByIds(adminAccountIds)
    val adminAccountMap = adminAccounts.associateBy { it.accountId }

    val ownerUser =
        adminUsers.find { it.id == project.ownerId }
            ?: throw IllegalStateException("Owner not found")
    val ownerAccount =
        adminAccountMap[ownerUser.accountId]
            ?: throw IllegalStateException("Owner account not found")

    val adminDtos =
        adminUsers.map { admin ->
            val adminAccount =
                adminAccountMap[admin.accountId]
                    ?: throw IllegalStateException("Admin account not found")
            UserDto(
                id = admin.id.value,
                name = admin.name.value,
                balance = adminAccount.balance,
            )
        }

    val projectDto =
        ProjectDto(
            id = project.id.value,
            name = project.name.value,
            owner =
                UserDto(
                    id = ownerUser.id.value,
                    name = ownerUser.name.value,
                    balance = ownerAccount.balance,
                ),
            admins = adminDtos,
            balance = projectAccount.balance,
            url = project.url?.value,
        )

    val userDto =
        UserDto(
            id = user.id.value,
            name = user.name.value,
            balance = userAccount.balance,
        )

    return BillDto(
        id = bill.id.value,
        amount = bill.amount,
        user = userDto,
        project = projectDto,
        status = BillDto.Status.valueOf(bill.status.name),
        description = bill.description,
        createdAt = bill.createdAt,
    )
}
