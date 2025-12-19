package jp.trap.plutus.pteron.features.project.service

import com.github.f4b6a3.uuid.UuidCreator
import io.ktor.server.plugins.*
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.project.domain.ApiClientCreationResult
import jp.trap.plutus.pteron.features.project.domain.ApiClientCreator
import jp.trap.plutus.pteron.features.project.domain.model.*
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import jp.trap.plutus.pteron.features.project.domain.transaction.ProjectTransaction
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@Single
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val projectTransaction: ProjectTransaction,
    private val economicGateway: EconomicGateway,
) {
    suspend fun getProjects(): List<Project> = projectRepository.findAll()

    suspend fun createProject(
        name: ProjectName,
        ownerId: UserId,
        url: ProjectUrl? = null,
    ): Project {
        val account = economicGateway.createAccount(canOverdraft = false)

        return projectTransaction.runInTransaction {
            val project =
                Project(
                    id = ProjectId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                    name = name,
                    ownerId = ownerId,
                    adminIds = listOf(ownerId),
                    accountId = account.accountId,
                    apiClients = emptyList(),
                    url = url,
                )
            projectRepository.save(project)
            project
        }
    }

    suspend fun getProjectDetails(projectId: ProjectId): Project =
        projectTransaction.runInTransaction { projectRepository.findById(projectId) }
            ?: throw NotFoundException("Project $projectId not found")

    suspend fun updateProject(
        projectId: ProjectId,
        url: ProjectUrl,
        actorId: UserId,
    ): Project =
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.isAdmin(actorId)) {
                throw ForbiddenOperationException("Only admins can update project settings")
            }

            val updatedProject = project.updateUrl(url)
            projectRepository.save(updatedProject)
            updatedProject
        }

    suspend fun addProjectAdmin(
        projectId: ProjectId,
        userId: UserId,
        actorId: UserId,
    ) {
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageAdmins(actorId)) {
                throw ForbiddenOperationException("Only the owner can manage admins")
            }

            when (val result = project.addAdmin(userId)) {
                is AdminAdditionResult.Success -> {
                    projectRepository.save(result.project)
                }

                is AdminAdditionResult.Failure.AlreadyExists -> {
                    throw IllegalArgumentException("User is already an admin")
                }
            }
        }
    }

    suspend fun deleteProjectAdmin(
        projectId: ProjectId,
        userId: UserId,
        actorId: UserId,
    ) {
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageAdmins(actorId)) {
                throw ForbiddenOperationException("Only the owner can manage admins")
            }

            when (val result = project.removeAdmin(userId)) {
                is AdminRemoveResult.Success -> {
                    projectRepository.save(result.project)
                }

                is AdminRemoveResult.Failure.AdminNotFound -> {
                    throw IllegalArgumentException("User is not an admin")
                }

                is AdminRemoveResult.Failure.CannotRemoveOwner -> {
                    throw IllegalArgumentException("Owner cannot be removed from admins")
                }
            }
        }
    }

    suspend fun createApiClient(
        projectId: ProjectId,
        actorId: UserId,
    ): ApiClientCreationResult =
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageApiClients(actorId)) {
                throw ForbiddenOperationException("Only admins can manage API clients")
            }

            val result = ApiClientCreator.createApiClient()
            projectRepository.save(project.addApiClient(result.apiClient))
            result
        }

    suspend fun getProjectApiClients(
        projectId: ProjectId,
        actorId: UserId,
    ): List<ApiClient> =
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageApiClients(actorId)) {
                throw ForbiddenOperationException("Only admins can view API clients")
            }

            project.apiClients
        }

    suspend fun deleteApiClient(
        projectId: ProjectId,
        clientId: Uuid,
        actorId: UserId,
    ) {
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageApiClients(actorId)) {
                throw ForbiddenOperationException("Only admins can manage API clients")
            }

            val clientToRemove =
                project.apiClients.find { it.clientId == clientId }
                    ?: throw IllegalArgumentException("Client not found: $clientId")
            when (val result = project.removeApiClient(clientToRemove)) {
                is ApiClientRemoveResult.Success -> {
                    projectRepository.save(result.project)
                }

                is ApiClientRemoveResult.Failure.ClientNotFound -> {
                    throw IllegalArgumentException("Client not found: $clientId")
                }
            }
        }
    }

    suspend fun deleteProject(
        projectId: ProjectId,
        actorId: UserId,
    ) {
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canDelete(actorId)) {
                throw ForbiddenOperationException("Only the owner can delete this project")
            }

            projectRepository.delete(projectId)
        }
    }
}

