package jp.trap.plutus.pteron.features.transaction.service

import com.github.f4b6a3.uuid.UuidCreator
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.exception.BadRequestException
import jp.trap.plutus.pteron.common.exception.NotFoundException
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import jp.trap.plutus.pteron.features.transaction.domain.model.Transaction
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionId
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionType
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionQueryOptions
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionQueryResult
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionRepository
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import io.grpc.Status
import io.grpc.StatusException
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.toKotlinUuid

@Single
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val economicGateway: EconomicGateway,
    private val unitOfWork: UnitOfWork,
) {
    /**
     * 全取引履歴を取得
     */
    suspend fun getTransactions(options: TransactionQueryOptions = TransactionQueryOptions()): TransactionQueryResult =
        unitOfWork.runInTransaction {
            transactionRepository.findAll(options)
        }

    /**
     * 特定ユーザーの取引履歴を取得
     */
    suspend fun getUserTransactions(
        userId: UserId,
        options: TransactionQueryOptions = TransactionQueryOptions(),
    ): TransactionQueryResult =
        unitOfWork.runInTransaction {
            transactionRepository.findByUserId(userId, options)
        }

    /**
     * 特定プロジェクトの取引履歴を取得
     */
    suspend fun getProjectTransactions(
        projectId: ProjectId,
        options: TransactionQueryOptions = TransactionQueryOptions(),
    ): TransactionQueryResult =
        unitOfWork.runInTransaction {
            transactionRepository.findByProjectId(projectId, options)
        }

    /**
     * 取引IDで取引を取得
     */
    suspend fun getTransaction(transactionId: TransactionId): Transaction =
        unitOfWork.runInTransaction {
            transactionRepository.findById(transactionId)
        } ?: throw NotFoundException("Transaction not found: $transactionId")

    /**
     * プロジェクトからユーザーへ送金（TRANSFER）
     *
     * @throws NotFoundException プロジェクトまたはユーザーが見つからない場合
     * @throws BadRequestException 残高不足の場合
     */
    suspend fun transfer(
        projectId: ProjectId,
        toUserId: UserId,
        amount: Long,
        description: String? = null,
    ): Transaction {
        val project =
            unitOfWork.runInTransaction { projectRepository.findById(projectId) }
                ?: throw NotFoundException("Project not found: $projectId")

        val user =
            unitOfWork.runInTransaction { userRepository.findById(toUserId) }
                ?: throw NotFoundException("User not found: $toUserId")

        try {
            economicGateway.transfer(
                from = project.accountId,
                to = user.accountId,
                amount = amount,
            )
        } catch (e: StatusException) {
            when (e.status.code) {
                Status.Code.FAILED_PRECONDITION -> throw BadRequestException("Insufficient balance for project: $projectId")
                Status.Code.NOT_FOUND -> throw NotFoundException("Account not found for project or user")
                else -> throw RuntimeException("Transfer failed due to gRPC error: ${e.status.description}", e)
            }
        } catch (e: Exception) {
            throw RuntimeException("Transfer failed due to unexpected error", e)
        }

        // 取引レコードを作成
        val transaction =
            Transaction(
                id = TransactionId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                type = TransactionType.TRANSFER,
                amount = amount,
                projectId = projectId,
                userId = toUserId,
                description = description,
                createdAt = Clock.System.now(),
            )

        return unitOfWork.runInTransaction {
            transactionRepository.save(transaction)
            transaction
        }
    }
}
