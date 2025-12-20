package jp.trap.plutus.pteron.features.transaction.service

import com.github.f4b6a3.uuid.UuidCreator
import io.grpc.Status
import io.grpc.StatusException
import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.exception.BadRequestException
import jp.trap.plutus.pteron.common.exception.ConflictException
import jp.trap.plutus.pteron.common.exception.NotFoundException
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import jp.trap.plutus.pteron.features.transaction.domain.model.*
import jp.trap.plutus.pteron.features.transaction.domain.repository.BillQueryOptions
import jp.trap.plutus.pteron.features.transaction.domain.repository.BillQueryResult
import jp.trap.plutus.pteron.features.transaction.domain.repository.BillRepository
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionRepository
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.toKotlinUuid

/**
 * 請求承認結果（Transactionも含めて返却）
 */
data class BillApprovalSuccess(
    val bill: Bill,
    val transaction: Transaction,
)

@Single
class BillService(
    private val billRepository: BillRepository,
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val economicGateway: EconomicGateway,
    private val unitOfWork: UnitOfWork,
) {
    /**
     * 請求を作成
     *
     * @throws NotFoundException プロジェクトまたはユーザーが見つからない場合
     */
    suspend fun createBill(
        projectId: ProjectId,
        targetUserId: UserId,
        amount: Long,
        description: String? = null,
    ): Bill {
        unitOfWork.runInTransaction { projectRepository.findById(projectId) }
            ?: throw NotFoundException("Project not found: $projectId")

        unitOfWork.runInTransaction { userRepository.findById(targetUserId) }
            ?: throw NotFoundException("User not found: $targetUserId")

        val bill =
            Bill(
                id = BillId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                amount = amount,
                userId = targetUserId,
                projectId = projectId,
                description = description,
                status = BillStatus.PENDING,
                createdAt = Clock.System.now(),
            )

        return unitOfWork.runInTransaction {
            billRepository.save(bill)
            bill
        }
    }

    /**
     * 請求IDで請求を取得
     *
     * @throws NotFoundException 請求が見つからない場合
     */
    suspend fun getBill(billId: BillId): Bill =
        unitOfWork.runInTransaction { billRepository.findById(billId) }
            ?: throw NotFoundException("Bill not found: $billId")

    /**
     * プロジェクトの請求一覧を取得
     */
    suspend fun getProjectBills(
        projectId: ProjectId,
        options: BillQueryOptions = BillQueryOptions(),
    ): BillQueryResult =
        unitOfWork.runInTransaction {
            billRepository.findByProjectId(projectId, options)
        }

    /**
     * ユーザーへの請求一覧を取得
     */
    suspend fun getUserBills(
        userId: UserId,
        options: BillQueryOptions = BillQueryOptions(),
    ): BillQueryResult =
        unitOfWork.runInTransaction {
            billRepository.findByUserId(userId, options)
        }

    /**
     * 請求を承認して支払いを実行する（ユーザーが呼び出す）
     *
     * @throws NotFoundException 請求が見つからない場合
     * @throws ConflictException 既に処理済みの場合
     * @throws BadRequestException 残高不足の場合
     */
    suspend fun approveBill(
        billId: BillId,
        actorUserId: UserId,
    ): BillApprovalSuccess {
        val bill =
            unitOfWork.runInTransaction { billRepository.findById(billId) }
                ?: throw NotFoundException("Bill not found: $billId")

        // 請求対象のユーザーのみが承認可能
        if (bill.userId != actorUserId) {
            throw NotFoundException("Bill not found: $billId")
        }

        // 承認処理（PENDING → PROCESSING）
        val processingBill =
            when (val result = bill.approve()) {
                is BillApprovalResult.Success -> {
                    result.bill
                }

                is BillApprovalResult.Failure.AlreadyProcessed -> {
                    throw ConflictException("Bill has already been processed: $billId")
                }

                is BillApprovalResult.Failure.InsufficientBalance -> {
                    throw BadRequestException("Insufficient balance for user: $actorUserId")
                }
            }

        // 処理中状態を保存
        unitOfWork.runInTransaction { billRepository.save(processingBill) }

        // ユーザーとプロジェクトの情報を取得
        val user =
            unitOfWork.runInTransaction { userRepository.findById(bill.userId) }
                ?: throw NotFoundException("User not found: ${bill.userId}")
        val project =
            unitOfWork.runInTransaction { projectRepository.findById(bill.projectId) }
                ?: throw NotFoundException("Project not found: ${bill.projectId}")

        // cornucopiaを通じて送金を実行
        try {
            economicGateway.transfer(
                from = user.accountId,
                to = project.accountId,
                amount = bill.amount,
            )
        } catch (e: StatusException) {
            when (val failResult = processingBill.markAsFailed()) {
                is BillMarkAsFailedResult.Success -> {
                    unitOfWork.runInTransaction { billRepository.save(failResult.bill) }
                }

                is BillMarkAsFailedResult.Failure.NotProcessing -> {
                }
            }
            when (e.status.code) {
                Status.Code.FAILED_PRECONDITION -> throw BadRequestException("Insufficient balance for user: $actorUserId")
                Status.Code.NOT_FOUND -> throw NotFoundException("Account not found for user or project")
                else -> throw RuntimeException("Transfer failed due to gRPC error: ${e.status.description}", e)
            }
        } catch (e: Exception) {
            when (val failResult = processingBill.markAsFailed()) {
                is BillMarkAsFailedResult.Success -> {
                    unitOfWork.runInTransaction { billRepository.save(failResult.bill) }
                }

                is BillMarkAsFailedResult.Failure.NotProcessing -> {
                }
            }
            throw RuntimeException("Transfer failed due to unexpected error", e)
        }

        // 完了状態に変更
        val completedBill =
            when (val result = processingBill.complete()) {
                is BillCompleteResult.Success -> {
                    result.bill
                }

                is BillCompleteResult.Failure.NotProcessing -> {
                    throw ConflictException("Bill state changed unexpectedly: $billId")
                }
            }

        // 取引レコードを作成
        val transaction =
            Transaction(
                id = TransactionId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                type = TransactionType.BILL_PAYMENT,
                amount = bill.amount,
                projectId = bill.projectId,
                userId = bill.userId,
                description = bill.description,
                createdAt = Clock.System.now(),
            )

        return unitOfWork.runInTransaction {
            billRepository.save(completedBill)
            transactionRepository.save(transaction)
            BillApprovalSuccess(completedBill, transaction)
        }
    }

    /**
     * 請求を拒否する（ユーザーが呼び出す）
     *
     * @throws NotFoundException 請求が見つからない場合
     * @throws ConflictException 既に処理済みの場合
     */
    suspend fun declineBill(
        billId: BillId,
        actorUserId: UserId,
    ): Bill {
        val bill =
            unitOfWork.runInTransaction { billRepository.findById(billId) }
                ?: throw NotFoundException("Bill not found: $billId")

        // 請求対象のユーザーのみが拒否可能
        if (bill.userId != actorUserId) {
            throw NotFoundException("Bill not found: $billId")
        }

        return when (val result = bill.decline()) {
            is BillDeclineResult.Success -> {
                unitOfWork.runInTransaction { billRepository.save(result.bill) }
                result.bill
            }

            is BillDeclineResult.Failure.AlreadyProcessed -> {
                throw ConflictException("Bill has already been processed: $billId")
            }
        }
    }

    /**
     * 請求をキャンセルする（プロジェクトが呼び出す）
     *
     * @throws NotFoundException 請求が見つからない場合
     * @throws ConflictException 既に処理済みの場合
     */
    suspend fun cancelBill(
        billId: BillId,
        actorProjectId: ProjectId,
    ): Bill {
        val bill =
            unitOfWork.runInTransaction { billRepository.findById(billId) }
                ?: throw NotFoundException("Bill not found: $billId")

        // 請求を作成したプロジェクトのみがキャンセル可能
        if (bill.projectId != actorProjectId) {
            throw NotFoundException("Bill not found: $billId")
        }

        // decline()を使ってキャンセル（REJECTEDと同じ扱い）
        return when (val result = bill.decline()) {
            is BillDeclineResult.Success -> {
                unitOfWork.runInTransaction { billRepository.save(result.bill) }
                result.bill
            }

            is BillDeclineResult.Failure.AlreadyProcessed -> {
                throw ConflictException("Bill has already been processed: $billId")
            }
        }
    }
}
