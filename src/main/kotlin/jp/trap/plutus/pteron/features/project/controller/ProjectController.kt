package jp.trap.plutus.pteron.features.project.controller

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.project.service.ProjectService
import jp.trap.plutus.pteron.openapi.internal.models.APIClient
import jp.trap.plutus.pteron.openapi.internal.models.Project
import jp.trap.plutus.pteron.openapi.internal.models.User
import kotlin.uuid.Uuid
import jp.trap.plutus.pteron.features.project.domain.ApiClient as DomainAPIClient
import jp.trap.plutus.pteron.features.project.domain.Project as DomainProject

class ProjectController(
    private val projectService: ProjectService,
) {
    private fun DomainProject.toResponse(): Project =
        Project(
            id = this.id.value,
            name = this.name,
            ownerId = this.ownerId.value,
            adminIds = this.adminIds.map { it.value },
            accountId = this.accountId.value,
        )

    private fun UserId.toResponse(): User =
        User(
            // TODO
        )

    private fun DomainAPIClient.toResponse(): APIClient =
        APIClient(
            clientId = this.clientId.toString(),
            clientSecret = this.clientSecret,
            createdAt = this.createdAt,
        )

    suspend fun getProjects(
        limit: Int,
        cursor: String?,
    ): Pair<List<Project>, String?> {
        val (domainProjects, nextCursor) = projectService.getProjects(limit, cursor)
        return Pair(
            domainProjects.map { it.toResponse() },
            nextCursor,
        )
    }

    suspend fun createProject(
        name: String,
        ownerId: Uuid,
    ): Project {
        val createProject = projectService.createProject(name, UserId(ownerId))
        return createProject.toResponse()
    }

    suspend fun getProjectDetails(projectId: Uuid): Project {
        val projectDetails =
            projectService.getProjectDetails(ProjectId(projectId))
        return projectDetails.toResponse()
    }

    suspend fun getProjectAdmins(projectId: Uuid): List<User> {
        val project =
            projectService.getProjectDetails(ProjectId(projectId))
        return project.adminIds.map { it.toResponse() }
    }

    suspend fun addProjectAdmin(
        projectId: Uuid,
        userId: Uuid,
    ) {
        projectService.addProjectAdmin(ProjectId(projectId), UserId(userId))
    }

    suspend fun deleteProjectAdmin(
        projectId: Uuid,
        userId: Uuid,
    ) {
        projectService.deleteProjectAdmin(ProjectId(projectId), UserId(userId))
    }

    suspend fun getProjectClients(projectId: Uuid): List<APIClient> {
        val project =
            projectService.getProjectDetails(ProjectId(projectId))
        return project.apiClients.toList().map { it.toResponse() }
    }

    suspend fun createProjectClient(projectId: Uuid): APIClient {
        val client = projectService.createAPIClient(ProjectId(projectId))
        return client.toResponse()
    }

    suspend fun deleteProjectClient(
        projectId: Uuid,
        clientId: String,
    ) {
        val clientUuid =
            try {
                Uuid.parse(clientId)
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("Invalid client_id format")
            }
        projectService.deleteAPIClient(ProjectId(projectId), clientUuid)
    }
}
