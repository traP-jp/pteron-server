package jp.trap.plutus.pteron.features.system.service

import com.github.f4b6a3.uuid.UuidCreator
import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.config.Environment
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.system.domain.model.SystemConfig
import jp.trap.plutus.pteron.features.system.domain.repository.SystemConfigRepository
import jp.trap.plutus.pteron.features.transaction.domain.model.Transaction
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionId
import jp.trap.plutus.pteron.features.transaction.domain.model.TransactionType
import jp.trap.plutus.pteron.features.transaction.domain.repository.TransactionRepository
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@Single
class SystemAccountService(
    private val economicGateway: EconomicGateway,
    private val transactionRepository: TransactionRepository,
    private val systemConfigRepository: SystemConfigRepository,
    private val unitOfWork: UnitOfWork,
) {
    private val logger = LoggerFactory.getLogger(SystemAccountService::class.java)

    private val systemAccountIdKey = "SYSTEM_ACCOUNT_ID"

    suspend fun initialize() {
        val existing =
            unitOfWork.runInTransaction {
                systemConfigRepository.findByKey(systemAccountIdKey)
            }

        if (existing == null) {
            logger.info("Initializing System Account...")
            val account = economicGateway.createAccount(canOverdraft = true)
            logger.info("System Account created: ${account.accountId.value}")

            unitOfWork.runInTransaction {
                if (systemConfigRepository.findByKey(systemAccountIdKey) == null) {
                    systemConfigRepository.save(
                        SystemConfig(
                            key = systemAccountIdKey,
                            value = account.accountId.value.toString(),
                        ),
                    )
                }
            }
        } else {
            logger.info("System Account already exists: ${existing.value}")
        }
    }

    private suspend fun getSystemAccountId(): AccountId? {
        val value =
            unitOfWork.runInTransaction {
                systemConfigRepository.findByKey(systemAccountIdKey)?.value
            }
        return value?.let { AccountId(Uuid.parse(it)) }
    }

    suspend fun sendWelcomeBonusToUser(
        userId: UserId,
        userAccountId: AccountId,
    ) {
        val bonus = Environment.WELCOME_BONUS_USER
        if (bonus <= 0) return

        val systemAccountId = getSystemAccountId()
        if (systemAccountId == null) {
            logger.error("System Account ID not found. Cannot send welcome bonus to user $userId")
            return
        }

        try {
            economicGateway.transfer(
                from = systemAccountId,
                to = userAccountId,
                amount = bonus,
            )

            unitOfWork.runInTransaction {
                transactionRepository.save(
                    Transaction(
                        id = TransactionId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                        type = TransactionType.SYSTEM,
                        amount = bonus,
                        projectId = null,
                        userId = userId,
                        description = "Welcome Bonus",
                        createdAt = Clock.System.now(),
                    ),
                )
            }
            logger.info("Sent welcome bonus of $bonus to user $userId")
        } catch (e: Exception) {
            logger.error("Failed to send welcome bonus to user $userId", e)
        }
    }

    suspend fun sendWelcomeBonusToProject(
        projectId: ProjectId,
        projectAccountId: AccountId,
    ) {
        val bonus = Environment.WELCOME_BONUS_PROJECT
        if (bonus <= 0) return

        val systemAccountId = getSystemAccountId()
        if (systemAccountId == null) {
            logger.error("System Account ID not found. Cannot send welcome bonus to project $projectId")
            return
        }

        try {
            economicGateway.transfer(
                from = systemAccountId,
                to = projectAccountId,
                amount = bonus,
            )

            unitOfWork.runInTransaction {
                transactionRepository.save(
                    Transaction(
                        id = TransactionId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                        type = TransactionType.SYSTEM,
                        amount = bonus,
                        projectId = projectId,
                        userId = null,
                        description = "Welcome Bonus",
                        createdAt = Clock.System.now(),
                    ),
                )
            }
            logger.info("Sent welcome bonus of $bonus to project $projectId")
        } catch (e: Exception) {
            logger.error("Failed to send welcome bonus to project $projectId", e)
        }
    }
}
