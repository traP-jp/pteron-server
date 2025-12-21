package jp.trap.plutus.pteron.features.user.infrastructure

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.domain.model.Username
import jp.trap.plutus.pteron.features.user.domain.repository.UserRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.koin.core.annotation.Single
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Single(binds = [UserRepository::class])
class DatabaseUserRepository : UserRepository {
    private val idCache = java.util.concurrent.ConcurrentHashMap<UserId, User>()
    private val usernameCache = java.util.concurrent.ConcurrentHashMap<Username, User>()

    @Volatile
    private var allUsersCache: List<User>? = null

    override suspend fun findAll(): List<User> {
        val cached = allUsersCache
        if (cached != null) return cached

        return UserTable
            .selectAll()
            .map { it.toUser() }
            .also { users ->
                users.forEach { user ->
                    idCache[user.id] = user
                    usernameCache[user.name] = user
                }
                allUsersCache = users
            }
    }

    override suspend fun findById(id: UserId): User? {
        idCache[id]?.let { return it }

        return UserTable
            .selectAll()
            .where { UserTable.id eq id.value.toJavaUuid() }
            .map { it.toUser() }
            .singleOrNull()
            ?.also { user ->
                idCache[user.id] = user
                usernameCache[user.name] = user
            }
    }

    override suspend fun findByIds(ids: List<UserId>): List<User> {
        if (ids.isEmpty()) return emptyList()

        val cachedUsers = ids.mapNotNull { idCache[it] }
        val missingIds = ids.filter { !idCache.containsKey(it) }

        if (missingIds.isEmpty()) {
            return cachedUsers
        }

        val dbUsers =
            UserTable
                .selectAll()
                .where { UserTable.id inList missingIds.map { it.value.toJavaUuid() } }
                .map { it.toUser() }
                .onEach { user ->
                    idCache[user.id] = user
                    usernameCache[user.name] = user
                }

        return cachedUsers + dbUsers
    }

    override suspend fun findByUsername(username: Username): User? {
        usernameCache[username]?.let { return it }

        return UserTable
            .selectAll()
            .where { UserTable.name eq username.value }
            .map { it.toUser() }
            .singleOrNull()
            ?.also { user ->
                idCache[user.id] = user
                usernameCache[user.name] = user
            }
    }

    override suspend fun save(user: User) {
        UserTable.upsert {
            it[id] = user.id.value.toJavaUuid()
            it[name] = user.name.value
            it[accountId] = user.accountId.value.toJavaUuid()
        }
        idCache[user.id] = user
        usernameCache[user.name] = user
        allUsersCache = null
    }

    private fun ResultRow.toUser(): User =
        User(
            id = UserId(this[UserTable.id].value.toKotlinUuid()),
            name = Username(this[UserTable.name]),
            accountId = AccountId(this[UserTable.accountId].toKotlinUuid()),
        )
}
