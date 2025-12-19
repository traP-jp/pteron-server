package jp.trap.plutus.pteron.features.user.infrastructure

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.domain.model.Username
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.koin.core.annotation.Single
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Single(binds = [UserRepository::class])
class DatabaseUserRepository : UserRepository {
    override suspend fun findById(id: UserId): User? =
        UserTable
            .selectAll()
            .where { UserTable.id eq id.value.toJavaUuid() }
            .map { it.toUser() }
            .singleOrNull()

    override suspend fun findByUsername(username: Username): User? =
        UserTable
            .selectAll()
            .where { UserTable.name eq username.value }
            .map { it.toUser() }
            .singleOrNull()

    override suspend fun save(user: User) {
        UserTable.upsert {
            it[id] = user.id.value.toJavaUuid()
            it[name] = user.name.value
            it[accountId] = user.accountId.value.toJavaUuid()
        }
    }

    private fun ResultRow.toUser(): User =
        User(
            id = UserId(this[UserTable.id].value.toKotlinUuid()),
            name = Username(this[UserTable.name]),
            accountId = AccountId(this[UserTable.accountId].toKotlinUuid()),
        )
}
