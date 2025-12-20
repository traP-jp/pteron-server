package jp.trap.plutus.pteron.features.project.service

import com.github.f4b6a3.uuid.UuidCreator
import jp.trap.plutus.pteron.common.domain.UnitOfWork
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.common.exception.BadRequestException
import jp.trap.plutus.pteron.common.exception.ConflictException
import jp.trap.plutus.pteron.common.exception.ForbiddenException
import jp.trap.plutus.pteron.common.exception.NotFoundException
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.project.domain.ApiClientCreationResult
import jp.trap.plutus.pteron.features.project.domain.ApiClientCreator
import jp.trap.plutus.pteron.features.project.domain.model.*
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@Single
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val economicGateway: EconomicGateway,
    private val unitOfWork: UnitOfWork,
) {
    suspend fun getProjects(): List<Project> =
        unitOfWork.runInTransaction {
            projectRepository.findAll()
        }

    suspend fun getProjectsByIds(projectIds: List<ProjectId>): List<Project> =
        unitOfWork.runInTransaction {
            projectIds.mapNotNull { projectRepository.findById(it) }
        }

    suspend fun getProject(idOrName: String): Project {
        val uuid = runCatching { Uuid.parse(idOrName) }.getOrNull()
        return if (uuid != null) {
            getProjectDetails(ProjectId(uuid))
        } else {
            getProjectByName(ProjectName(idOrName))
        }
    }

    suspend fun getProjectByName(name: ProjectName): Project =
        unitOfWork.runInTransaction { projectRepository.findByName(name) }
            ?: throw NotFoundException("Project not found: ${name.value}")

    suspend fun createProject(
        name: ProjectName,
        ownerId: UserId,
        url: ProjectUrl? = null,
    ): Project {
        unitOfWork.runInTransaction {
            projectRepository.findByName(name)?.let {
                throw ConflictException("Project with name '${name.value}' already exists")
            }
        }

        val account = economicGateway.createAccount(canOverdraft = false)

        return unitOfWork.runInTransaction {
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
        unitOfWork.runInTransaction { projectRepository.findById(projectId) }
            ?: throw NotFoundException("Project $projectId not found")

    suspend fun updateProject(
        projectId: ProjectId,
        url: ProjectUrl,
        actorId: UserId,
    ): Project =
        unitOfWork.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.isAdmin(actorId)) {
                throw ForbiddenException("Only admins can update project settings")
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
        unitOfWork.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageAdmins(actorId)) {
                throw ForbiddenException("Only the owner can manage admins")
            }

            when (val result = project.addAdmin(userId)) {
                is AdminAdditionResult.Success -> {
                    projectRepository.save(result.project)
                }

                is AdminAdditionResult.Failure.AlreadyExists -> {
                    throw BadRequestException("User is already an admin")
                }
            }
        }
    }

    suspend fun deleteProjectAdmin(
        projectId: ProjectId,
        userId: UserId,
        actorId: UserId,
    ) {
        unitOfWork.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageAdmins(actorId)) {
                throw ForbiddenException("Only the owner can manage admins")
            }

            when (val result = project.removeAdmin(userId)) {
                is AdminRemoveResult.Success -> {
                    projectRepository.save(result.project)
                }

                is AdminRemoveResult.Failure.AdminNotFound -> {
                    throw BadRequestException("User is not an admin")
                }

                is AdminRemoveResult.Failure.CannotRemoveOwner -> {
                    throw BadRequestException("Owner cannot be removed from admins")
                }
            }
        }
    }

    suspend fun createApiClient(
        projectId: ProjectId,
        actorId: UserId,
    ): ApiClientCreationResult =
        unitOfWork.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageApiClients(actorId)) {
                throw ForbiddenException("Only admins can manage API clients")
            }

            val result = ApiClientCreator.createApiClient()
            projectRepository.save(project.addApiClient(result.apiClient))
            result
        }

    suspend fun getProjectApiClients(
        projectId: ProjectId,
        actorId: UserId,
    ): List<ApiClient> =
        unitOfWork.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageApiClients(actorId)) {
                throw ForbiddenException("Only admins can view API clients")
            }

            project.apiClients
        }

    suspend fun deleteApiClient(
        projectId: ProjectId,
        clientId: Uuid,
        actorId: UserId,
    ) {
        unitOfWork.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canManageApiClients(actorId)) {
                throw ForbiddenException("Only admins can manage API clients")
            }

            val clientToRemove =
                project.apiClients.find { it.clientId == clientId }
                    ?: throw BadRequestException("Client not found: $clientId")
            when (val result = project.removeApiClient(clientToRemove)) {
                is ApiClientRemoveResult.Success -> {
                    projectRepository.save(result.project)
                }

                is ApiClientRemoveResult.Failure.ClientNotFound -> {
                    throw BadRequestException("Client not found: $clientId")
                }
            }
        }
    }

    suspend fun deleteProject(
        projectId: ProjectId,
        actorId: UserId,
    ) {
        unitOfWork.runInTransaction {
            val project =
                projectRepository.findById(projectId) ?: throw NotFoundException("Project not found: $projectId")

            if (!project.canDelete(actorId)) {
                throw ForbiddenException("Only the owner can delete this project")
            }

            projectRepository.delete(projectId)
        }
    }

    suspend fun authenticateApiClient(
        clientId: Uuid,
        plainSecret: String,
    ): Project? =
        unitOfWork.runInTransaction {
            val project = projectRepository.findByApiClientId(clientId) ?: return@runInTransaction null
            val apiClient = project.apiClients.find { it.clientId == clientId } ?: return@runInTransaction null

            if (!apiClient.verifySecret(plainSecret)) {
                return@runInTransaction null
            }

            project
        }
}
