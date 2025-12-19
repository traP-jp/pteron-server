package jp.trap.plutus.pteron.features.project.domain.model

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import kotlin.time.Instant
import kotlin.uuid.Uuid

sealed interface AdminAdditionResult {
    data class Success(
        val project: Project,
    ) : AdminAdditionResult

    sealed interface Failure : AdminAdditionResult {
        object AlreadyExists : Failure
    }
}

sealed interface AdminRemoveResult {
    data class Success(
        val project: Project,
    ) : AdminRemoveResult

    sealed interface Failure : AdminRemoveResult {
        object AdminNotFound : Failure

        object CannotRemoveOwner : Failure
    }
}

sealed interface ApiClientRemoveResult {
    data class Success(
        val project: Project,
    ) : ApiClientRemoveResult

    sealed interface Failure : ApiClientRemoveResult {
        object ClientNotFound : Failure
    }
}

class Project(
    val id: ProjectId,
    val name: ProjectName,
    val ownerId: UserId,
    val adminIds: List<UserId>,
    val accountId: AccountId,
    val apiClients: List<ApiClient>,
    val url: ProjectUrl? = null,
) {
    init {
        require(ownerId in adminIds) { "Owner must be included in admin IDs." }
    }

    fun isOwner(userId: UserId): Boolean = ownerId == userId

    fun isAdmin(userId: UserId): Boolean = adminIds.contains(userId)

    fun canDelete(userId: UserId): Boolean = isOwner(userId)

    fun canManageApiClients(userId: UserId): Boolean = isAdmin(userId)

    fun canManageAdmins(userId: UserId): Boolean = isOwner(userId)

    fun updateUrl(newUrl: ProjectUrl): Project =
        Project(
            id = id,
            name = name,
            ownerId = ownerId,
            adminIds = adminIds,
            accountId = accountId,
            apiClients = apiClients,
            url = newUrl,
        )

    fun addAdmin(userId: UserId): AdminAdditionResult {
        if (userId in adminIds) {
            return AdminAdditionResult.Failure.AlreadyExists
        }

        return AdminAdditionResult.Success(
            Project(
                id = id,
                name = name,
                ownerId = ownerId,
                adminIds = adminIds + userId,
                accountId = accountId,
                apiClients = apiClients,
                url = url,
            ),
        )
    }

    fun removeAdmin(userId: UserId): AdminRemoveResult {
        if (userId !in adminIds) {
            return AdminRemoveResult.Failure.AdminNotFound
        }
        if (userId == ownerId) {
            return AdminRemoveResult.Failure.CannotRemoveOwner
        }
        return AdminRemoveResult.Success(
            Project(
                id = id,
                name = name,
                ownerId = ownerId,
                adminIds = adminIds - userId,
                accountId = accountId,
                apiClients = apiClients,
                url = url,
            ),
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
            url = url,
        )

    fun removeApiClient(client: ApiClient): ApiClientRemoveResult {
        if (client !in apiClients) {
            return ApiClientRemoveResult.Failure.ClientNotFound
        }
        return ApiClientRemoveResult.Success(
            Project(
                id = id,
                name = name,
                ownerId = ownerId,
                adminIds = adminIds,
                accountId = accountId,
                apiClients = apiClients - client,
                url = url,
            ),
        )
    }
}

class ApiClient(
    val clientId: Uuid,
    val clientSecretHashed: String,
    val createdAt: Instant,
) {
    fun verifySecret(plainSecret: String): Boolean =
        org.mindrot.jbcrypt.BCrypt
            .checkpw(plainSecret, clientSecretHashed)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApiClient) return false
        return clientId == other.clientId
    }

    override fun hashCode(): Int = clientId.hashCode()
}
