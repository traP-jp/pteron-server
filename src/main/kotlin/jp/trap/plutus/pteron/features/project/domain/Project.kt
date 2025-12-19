package jp.trap.plutus.pteron.features.project.domain

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import kotlin.time.Instant
import kotlin.uuid.Uuid

class Project(
    val id: ProjectId,
    val name: String,
    val ownerId: UserId,
    val adminIds: List<UserId>,
    val accountId: AccountId,
    val apiClients: List<ApiClient>,
) {
    fun hasAdmin(userId: UserId): Boolean = adminIds.contains(userId)

    fun addAdmin(userId: UserId): Project {
        if (userId in adminIds) {
            throw IllegalArgumentException("User already exists for this project")
        }
        return Project(
            id = id,
            name = name,
            ownerId = ownerId,
            adminIds = adminIds + userId,
            accountId = accountId,
            apiClients = apiClients,
        )
    }

    fun removeAdmin(userId: UserId): Project {
        if (userId !in adminIds) {
            throw IllegalArgumentException("User does not exist for this project")
        }
        if (userId == ownerId) {
            throw IllegalArgumentException("Owner can't be deleted")
        }
        return Project(
            id = id,
            name = name,
            ownerId = ownerId,
            adminIds = adminIds - userId,
            accountId = accountId,
            apiClients = apiClients,
        )
    }

    fun addApiClient(client: ApiClient): Project =
        Project(
            id = id,
            name = name,
            ownerId = ownerId,
            adminIds = adminIds,
            accountId = accountId,
            apiClients = apiClients + client,
        )

    fun removeApiClient(client: ApiClient): Project {
        if (client !in apiClients) {
            throw IllegalArgumentException("Client is not registered")
        }
        return Project(
            id = id,
            name = name,
            ownerId = ownerId,
            adminIds = adminIds,
            accountId = accountId,
            apiClients = apiClients - client,
        )
    }
}

class ApiClient(
    val clientId: Uuid,
    val clientSecret: String,
    val createdAt: Instant,
)
