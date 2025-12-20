package jp.trap.plutus.pteron.features.user.service

import com.github.f4b6a3.uuid.UuidCreator
import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.exception.NotFoundException
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.domain.model.Username
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@Single
class UserService(
    private val userRepository: UserRepository,
    private val economicGateway: EconomicGateway,
    private val unitOfWork: UnitOfWork,
) {
    suspend fun ensureUser(name: Username) {
        val existingUser = unitOfWork.runInTransaction { userRepository.findByUsername(name) }
        if (existingUser != null) {
            return
        }

        val account = economicGateway.createAccount(true)

        unitOfWork.runInTransaction {
            val userCheck = userRepository.findByUsername(name)
            if (userCheck == null) {
                val newUser =
                    User(
                        id = UserId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                        name = name,
                        accountId = account.accountId,
                    )
                try {
                    userRepository.save(newUser)
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to create user: $name", e)
                }
            }
        }
    }

    suspend fun getUser(idOrName: String): User {
        val uuid = runCatching { Uuid.parse(idOrName) }.getOrNull()
        return if (uuid != null) {
            getUserById(UserId(uuid))
        } else {
            getUserByName(Username(idOrName))
        }
    }

    suspend fun getUserByName(name: Username): User =
        unitOfWork.runInTransaction { userRepository.findByUsername(name) }
            ?: throw NotFoundException("User not found: $name")

    suspend fun getUserById(id: UserId): User =
        unitOfWork.runInTransaction { userRepository.findById(id) }
            ?: throw NotFoundException("User not found: $id")

    suspend fun getUsersByIds(ids: List<UserId>): List<User> = unitOfWork.runInTransaction { userRepository.findByIds(ids) }

    suspend fun getAllUsers(): List<User> = unitOfWork.runInTransaction { userRepository.findAll() }
}
