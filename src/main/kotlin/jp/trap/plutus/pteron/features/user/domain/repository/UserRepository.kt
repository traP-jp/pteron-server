package jp.trap.plutus.pteron.features.user.domain.repository

import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.domain.model.Username

interface UserRepository {
    suspend fun findById(id: UserId): User?

    suspend fun findByIds(ids: List<UserId>): List<User>

    suspend fun findByUsername(username: Username): User?

    suspend fun save(user: User)
}
