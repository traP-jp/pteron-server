package jp.trap.plutus.pteron.features.project.service

import com.github.f4b6a3.uuid.UuidCreator
import io.ktor.server.plugins.NotFoundException
import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.project.domain.ApiClient
import jp.trap.plutus.pteron.features.project.domain.ApiClientCreator
import jp.trap.plutus.pteron.features.project.domain.Project
import jp.trap.plutus.pteron.features.project.domain.ProjectRepository
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class ProjectService(
    private val projectRepository: ProjectRepository,
) {
    suspend fun getProjects(
        limit: Int,
        cursor: String?,
    ): Pair<List<Project>, String?> = projectRepository.batchFind(limit, cursor)

    suspend fun createProject(
        name: String,
        ownerId: UserId,
    ): Project {
        val project =
            Project(
                id = ProjectId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                name = name,
                ownerId = ownerId,
                adminIds = listOf(ownerId),
                accountId = AccountId(UuidCreator.getTimeOrderedEpoch().toKotlinUuid()),
                apiClients = emptyList(),
            )
        projectRepository.save(project)
        return project
    }

    suspend fun getProjectDetails(projectId: ProjectId): Project =
        projectRepository.findById(projectId) ?: throw NotFoundException("Project $projectId not found")

    suspend fun addProjectAdmin(
        projectId: ProjectId,
        userId: UserId,
    ) {
        val project =
            projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")
        if (project.hasAdmin(userId)) {
            throw IllegalArgumentException("User is already an admin")
        }
        projectRepository.save(project.addAdmin(userId))
    }

    suspend fun deleteProjectAdmin(
        projectId: ProjectId,
        userId: UserId,
    ) {
        val project =
            projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")
        if (!project.hasAdmin(userId)) {
            throw IllegalArgumentException("User is not an admin")
        }
        projectRepository.save(project.removeAdmin(userId))
    }

    suspend fun createAPIClient(projectId: ProjectId): ApiClient {
        val apiClient = ApiClientCreator.createApiClient()
        val project =
            projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")
        projectRepository.save(project.addApiClient(apiClient))
        return apiClient
    }

    suspend fun deleteAPIClient(
        projectId: ProjectId,
        clientId: Uuid,
    ) {
        val project =
            projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")
        val clientToRemove =
            project.apiClients.find { it.clientId == clientId }
                ?: throw IllegalArgumentException("Client not found: $clientId")
        projectRepository.save(project.removeApiClient(clientToRemove))
    }
}
