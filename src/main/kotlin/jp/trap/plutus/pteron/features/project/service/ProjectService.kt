package jp.trap.plutus.pteron.features.project.service

import com.github.f4b6a3.uuid.UuidCreator
import io.ktor.server.plugins.*
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.project.domain.ApiClientCreator
import jp.trap.plutus.pteron.features.project.domain.model.*
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import jp.trap.plutus.pteron.features.project.domain.transaction.ProjectTransaction
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class ProjectService(
    private val projectRepository: ProjectRepository,
    private val projectTransaction: ProjectTransaction,
    private val economicGateway: EconomicGateway,
) {
    suspend fun getProjects(): List<Project> = projectRepository.findAll()

    suspend fun createProject(
        name: String,
        ownerId: UserId,
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
                )
            projectRepository.save(project)
            project
        }
    }

    suspend fun getProjectDetails(projectId: ProjectId): Project =
        projectTransaction.runInTransaction { projectRepository.findById(projectId) }
            ?: throw NotFoundException("Project $projectId not found")

    suspend fun addProjectAdmin(
        projectId: ProjectId,
        userId: UserId,
    ) {
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")
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
    ) {
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

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

    suspend fun createApiClient(projectId: ProjectId): ApiClient =
        projectTransaction.runInTransaction {
            val apiClient = ApiClientCreator.createApiClient()
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")
            projectRepository.save(project.addApiClient(apiClient))
            apiClient
        }

    suspend fun deleteApiClient(
        projectId: ProjectId,
        clientId: Uuid,
    ) {
        projectTransaction.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")
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
}
