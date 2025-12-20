package jp.trap.plutus.pteron.features.project.controller

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.account.domain.model.Account
import jp.trap.plutus.pteron.features.account.service.AccountService
import jp.trap.plutus.pteron.features.project.domain.model.ApiClient
import jp.trap.plutus.pteron.features.project.domain.model.Project
import jp.trap.plutus.pteron.features.project.domain.model.ProjectName
import jp.trap.plutus.pteron.features.project.domain.model.ProjectUrl
import jp.trap.plutus.pteron.features.project.service.ProjectService
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.service.UserService
import jp.trap.plutus.pteron.openapi.internal.Paths
import jp.trap.plutus.pteron.openapi.internal.models.AddProjectAdminRequest
import jp.trap.plutus.pteron.openapi.internal.models.CreateProjectRequest
import jp.trap.plutus.pteron.openapi.internal.models.GetProjects200Response
import jp.trap.plutus.pteron.openapi.internal.models.UpdateProjectRequest
import jp.trap.plutus.pteron.utils.trapId
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid
import jp.trap.plutus.pteron.openapi.internal.models.APIClient as APIClientDto
import jp.trap.plutus.pteron.openapi.internal.models.Project as ProjectDto
import jp.trap.plutus.pteron.openapi.internal.models.User as UserDto

fun Route.projectRoutes() {
    val projectService by inject<ProjectService>()
    val userService by inject<UserService>()
    val accountService by inject<AccountService>()

    // GET /projects
    get<Paths.getProjects> {
        val projects = projectService.getProjects()
        val projectDtos = createProjectDtos(projects, userService, accountService)
        call.respond(GetProjects200Response(items = projectDtos))
    }

    // POST /projects
    post<Paths.createProject> {
        val request = call.receive<CreateProjectRequest>()
        val currentUser = userService.getUserByName(call.trapId)
        val project =
            projectService.createProject(
                name = ProjectName(request.name),
                ownerId = currentUser.id,
                url = request.url?.let { ProjectUrl(it) },
            )
        val projectDto = createProjectDto(project, userService, accountService)
        call.respond(HttpStatusCode.Created, projectDto)
    }

    // GET /projects/{project_id}
    get<Paths.getProject> { params ->
        val project = projectService.getProject(params.projectId)
        val projectDto = createProjectDto(project, userService, accountService)
        call.respond(projectDto)
    }

    // PUT /projects/{project_id}
    put<Paths.updateProject> { params ->
        val project = projectService.getProject(params.projectId)
        val request = call.receive<UpdateProjectRequest>()
        val currentUser = userService.getUserByName(call.trapId)
        val updatedProject = projectService.updateProject(project.id, ProjectUrl(request.url), currentUser.id)
        val projectDto = createProjectDto(updatedProject, userService, accountService)
        call.respond(HttpStatusCode.OK, projectDto)
    }

    // GET /projects/{project_id}/admins
    get<Paths.getProjectAdmins> { params ->
        val project = projectService.getProject(params.projectId)
        val admins = userService.getUsersByIds(project.adminIds)
        val accounts = accountService.getAccountsByIds(admins.map { it.accountId })
        val accountMap = accounts.associateBy { it.accountId }
        val adminDtos =
            admins.map { admin ->
                val account =
                    accountMap[admin.accountId] ?: throw IllegalStateException("Account not found: ${admin.accountId}")
                createUserDto(admin, account)
            }
        call.respond(adminDtos)
    }

    // POST /projects/{project_id}/admins
    post<Paths.addProjectAdmin> { params ->
        val project = projectService.getProject(params.projectId)
        val request = call.receive<AddProjectAdminRequest>()
        val userId = UserId(Uuid.parse(request.userId))
        val currentUser = userService.getUserByName(call.trapId)
        projectService.addProjectAdmin(project.id, userId, currentUser.id)
        call.respond(HttpStatusCode.NoContent)
    }

    // DELETE /projects/{project_id}/admins
    delete<Paths.removeProjectAdmin> { params ->
        val project = projectService.getProject(params.projectId)
        val request = call.receive<AddProjectAdminRequest>()
        val userId = UserId(Uuid.parse(request.userId))
        val currentUser = userService.getUserByName(call.trapId)
        projectService.deleteProjectAdmin(project.id, userId, currentUser.id)
        call.respond(HttpStatusCode.NoContent)
    }

    // GET /projects/{project_id}/clients
    get<Paths.getProjectApiClients> { params ->
        val project = projectService.getProject(params.projectId)
        val currentUser = userService.getUserByName(call.trapId)
        val clients = projectService.getProjectApiClients(project.id, currentUser.id)
        val clientDtos = clients.map { createApiClientDto(it) }
        call.respond(clientDtos)
    }

    // POST /projects/{project_id}/clients
    post<Paths.createProjectApiClient> { params ->
        val project = projectService.getProject(params.projectId)
        val currentUser = userService.getUserByName(call.trapId)
        val result = projectService.createApiClient(project.id, currentUser.id)
        val clientDto = createApiClientDto(result.apiClient, plainSecret = result.plainSecret)
        call.respond(HttpStatusCode.Created, clientDto)
    }

    // DELETE /projects/{project_id}/clients/{client_id}
    delete<Paths.deleteProjectApiClient> { params ->
        val project = projectService.getProject(params.projectId)
        val clientId = Uuid.parse(params.clientId)
        val currentUser = userService.getUserByName(call.trapId)
        projectService.deleteApiClient(project.id, clientId, currentUser.id)
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun createProjectDtos(
    projects: List<Project>,
    userService: UserService,
    accountService: AccountService,
): List<ProjectDto> {
    if (projects.isEmpty()) return emptyList()

    val allUserIds = projects.flatMap { it.adminIds }.distinct()
    val users = userService.getUsersByIds(allUserIds)
    val userMap = users.associateBy { it.id }

    val allAccountIds = (users.map { it.accountId } + projects.map { it.accountId }).distinct()
    val accounts = accountService.getAccountsByIds(allAccountIds)
    val accountMap = accounts.associateBy { it.accountId }

    return projects.map { project ->
        val owner = userMap[project.ownerId] ?: throw IllegalStateException("Owner not found: ${project.ownerId}")
        val ownerAccount =
            accountMap[owner.accountId] ?: throw IllegalStateException("Owner account not found: ${owner.accountId}")
        val projectAccount =
            accountMap[project.accountId]
                ?: throw IllegalStateException("Project account not found: ${project.accountId}")

        val adminDtos =
            project.adminIds.map { adminId ->
                val admin = userMap[adminId] ?: throw IllegalStateException("Admin not found: $adminId")
                val account =
                    accountMap[admin.accountId]
                        ?: throw IllegalStateException("Admin account not found: ${admin.accountId}")
                createUserDto(admin, account)
            }

        ProjectDto(
            id = project.id.value,
            name = project.name.value,
            owner = createUserDto(owner, ownerAccount),
            admins = adminDtos,
            balance = projectAccount.balance,
            url = project.url?.value,
        )
    }
}

private suspend fun createProjectDto(
    project: Project,
    userService: UserService,
    accountService: AccountService,
): ProjectDto {
    val admins = userService.getUsersByIds(project.adminIds)
    val owner =
        admins.find { it.id == project.ownerId } ?: throw IllegalStateException("Owner not found: ${project.ownerId}")

    val allAccountIds = admins.map { it.accountId } + project.accountId
    val accounts = accountService.getAccountsByIds(allAccountIds)
    val accountMap = accounts.associateBy { it.accountId }

    val ownerAccount =
        accountMap[owner.accountId] ?: throw IllegalStateException("Owner account not found: ${owner.accountId}")
    val projectAccount =
        accountMap[project.accountId] ?: throw IllegalStateException("Project account not found: ${project.accountId}")

    val adminDtos =
        admins.map { admin ->
            val account =
                accountMap[admin.accountId] ?: throw IllegalStateException("Admin account not found: ${admin.accountId}")
            createUserDto(admin, account)
        }

    return ProjectDto(
        id = project.id.value,
        name = project.name.value,
        owner = createUserDto(owner, ownerAccount),
        admins = adminDtos,
        balance = projectAccount.balance,
        url = project.url?.value,
    )
}

private fun createUserDto(
    user: User,
    account: Account,
): UserDto =
    UserDto(
        id = user.id.value,
        name = user.name.value,
        balance = account.balance,
    )

private fun createApiClientDto(
    client: ApiClient,
    plainSecret: String? = null,
): APIClientDto =
    APIClientDto(
        clientId = client.clientId.toString(),
        createdAt = client.createdAt,
        clientSecret = plainSecret,
    )
