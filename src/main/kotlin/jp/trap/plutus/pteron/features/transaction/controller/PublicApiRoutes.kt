package jp.trap.plutus.pteron.features.transaction.controller

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.pteron.auth.projectPrincipal
import jp.trap.plutus.pteron.config.Environment
import jp.trap.plutus.pteron.features.account.service.AccountService
import jp.trap.plutus.pteron.features.transaction.domain.model.Bill
import jp.trap.plutus.pteron.features.transaction.domain.model.BillId
import jp.trap.plutus.pteron.features.transaction.domain.model.BillStatus
import jp.trap.plutus.pteron.features.transaction.domain.model.Transaction
import jp.trap.plutus.pteron.features.transaction.domain.repository.BillQueryOptions
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionQueryOptions
import jp.trap.plutus.pteron.features.transaction.service.BillService
import jp.trap.plutus.pteron.features.transaction.service.TransactionService
import jp.trap.plutus.pteron.features.user.domain.model.Username
import jp.trap.plutus.pteron.features.user.service.UserService
import jp.trap.plutus.pteron.openapi.public.models.*
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import jp.trap.plutus.pteron.openapi.public.Paths as PublicPaths
import jp.trap.plutus.pteron.openapi.public.models.Bill as BillDto
import jp.trap.plutus.pteron.openapi.public.models.Project as ProjectDto
import jp.trap.plutus.pteron.openapi.public.models.Transaction as TransactionDto

/**
 * Public API Routes (外部プロジェクト開発者向け)
 * Bearer認証で保護されたエンドポイント
 */
fun Route.publicApiRoutes() {
    val transactionService by inject<TransactionService>()
    val billService by inject<BillService>()
    val userService by inject<UserService>()
    val accountService by inject<AccountService>()

    // GET /me - 自プロジェクト情報取得
    get<PublicPaths.getMe> {
        val project = call.projectPrincipal?.project ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val account = accountService.getAccountById(project.accountId)

        call.respond(
            ProjectDto(
                id = project.id.value,
                name = project.name.value,
                balance = account.balance,
            ),
        )
    }

    // GET /me/transactions - 自プロジェクトの取引履歴
    get<PublicPaths.getMyTransactions> { params ->
        val project = call.projectPrincipal?.project ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val options =
            TransactionQueryOptions(
                limit = params.limit ?: 20,
                cursor = params.cursor,
            )
        val result = transactionService.getProjectTransactions(project.id, options)
        val transactionDtos = result.items.map { createPublicTransactionDto(it, userService) }

        call.respond(GetMyTransactions200Response(items = transactionDtos, nextCursor = result.nextCursor))
    }

    // GET /me/bills - 自プロジェクトの請求一覧
    get<PublicPaths.getMyBills> { params ->
        val project = call.projectPrincipal?.project ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val status = params.status?.let { BillStatus.valueOf(it) }
        val options =
            BillQueryOptions(
                limit = params.limit ?: 20,
                cursor = params.cursor,
                status = status,
            )
        val result = billService.getProjectBills(project.id, options)
        val billDtos = result.items.map { createPublicBillDto(it, userService) }

        call.respond(GetMyBills200Response(items = billDtos, nextCursor = result.nextCursor))
    }

    // POST /transactions - ユーザーへ送金
    post<PublicPaths.createTransaction> {
        val project = call.projectPrincipal?.project ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val request = call.receive<CreateTransactionRequest>()

        if (request.amount <= 0) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "Amount must be greater than zero",
            )
        }

        // traP ID (ユーザー名) からUserを検索
        val targetUser =
            userService.getUserByName(
                Username(request.toUser),
            )

        val transaction =
            transactionService.transfer(
                projectId = project.id,
                toUserId = targetUser.id,
                amount = request.amount,
                description = request.description,
            )

        call.respond(createPublicTransactionDto(transaction, userService))
    }

    // POST /bills - 請求作成
    post<PublicPaths.createBill> {
        val project = call.projectPrincipal?.project ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val request = call.receive<CreateBillRequest>()

        // traP ID (ユーザー名) からUserを検索
        val targetUser =
            userService.getUserByName(
                Username(request.targetUser),
            )

        val bill =
            billService.createBill(
                projectId = project.id,
                targetUserId = targetUser.id,
                amount = request.amount,
                description = request.description,
                successUrl = request.successUrl,
                cancelUrl = request.cancelUrl,
            )

        // 決済確認ページのURLを生成
        val paymentUrl = "${Environment.PUBLIC_URL}/checkout?id=${bill.id.value}"
        val expiresAt = Clock.System.now() + 24.hours

        call.respond(
            HttpStatusCode.Created,
            CreateBillResponse(
                billId = bill.id.value,
                paymentUrl = paymentUrl,
                expiresAt = expiresAt,
            ),
        )
    }

    // GET /bills/{bill_id} - 請求ステータス確認
    get<PublicPaths.getBill> { params ->
        val project = call.projectPrincipal?.project ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val billId = BillId(params.billId)
        val bill = billService.getBill(billId)

        // 自プロジェクトの請求のみ参照可能
        if (bill.projectId != project.id) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        call.respond(createPublicBillDto(bill, userService))
    }

    // POST /bills/{bill_id}/cancel - 請求キャンセル
    post<PublicPaths.cancelBill> { params ->
        val project = call.projectPrincipal?.project ?: return@post call.respond(HttpStatusCode.Unauthorized)

        val billId = BillId(params.billId)
        billService.cancelBill(billId, project.id)

        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun createPublicTransactionDto(
    transaction: Transaction,
    userService: UserService,
): TransactionDto {
    val user = transaction.userId?.let { userId -> userService.getUser(userId.value.toString()) }

    return TransactionDto(
        id = transaction.id.value,
        type = TransactionDto.Type.valueOf(transaction.type.name),
        amount = transaction.amount,
        userId = transaction.userId?.value,
        userName = user?.name?.value,
        projectId = transaction.projectId?.value,
        description = transaction.description,
        createdAt = transaction.createdAt,
    )
}

private suspend fun createPublicBillDto(
    bill: Bill,
    userService: UserService,
): BillDto {
    val user = userService.getUser(bill.userId.value.toString())

    return BillDto(
        id = bill.id.value,
        amount = bill.amount,
        userId = bill.userId.value,
        userName = user.name.value,
        status = BillDto.Status.valueOf(bill.status.name),
        description = bill.description,
        createdAt = bill.createdAt,
    )
}
