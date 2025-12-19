package jp.trap.plutus.pteron.features.project.domain

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
    val name: String,
    val ownerId: UserId,
    val adminIds: List<UserId>,
    val accountId: AccountId,
    val apiClients: List<ApiClient>,
) {
    fun hasAdmin(userId: UserId): Boolean = adminIds.contains(userId)

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
            ),
        )
    }
}

class ApiClient(
    val clientId: Uuid,
    val clientSecret: String,
    val createdAt: Instant,
)
