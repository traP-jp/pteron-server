@file:Suppress("ktlint:standard:no-wildcard-imports")

package jp.trap.plutus.pteron.features.project.route

import io.ktor.http.*
import io.ktor.server.plugins.* // BadRequestException用
import io.ktor.server.request.*
import io.ktor.server.resources.delete // ...
import io.ktor.server.resources.get // 明示的にResource用のgetをインポート
import io.ktor.server.resources.post // 明示的にResource用のpostをインポート
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.pteron.features.project.controller.ProjectController
import jp.trap.plutus.pteron.openapi.internal.Paths
import jp.trap.plutus.pteron.openapi.internal.models.Project
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

@Serializable
data class CreateProjectRequest(
    val name: String,
)

@Serializable
data class AddProjectAdminRequest(
    @SerialName("user_id")
    val userId: String,
)

@Serializable
data class ProjectsResponse(
    val items: List<Project>,
    @SerialName("next_cursor")
    val nextCursor: String? = null,
)

private fun String.toUuidOrBadRequest(fieldName: String): Uuid =
    try {
        Uuid.parse(this)
    } catch (e: IllegalArgumentException) {
        throw BadRequestException("Invalid format for $fieldName: $this")
    }

fun Route.projectRoutes() {
    val projectController by inject<ProjectController>()

    get<Paths.projectsGet> { params ->
        val limit = params.limit ?: 20
        val cursor = params.cursor

        val (items, nextCursor) = projectController.getProjects(limit, cursor)

        call.respond(
            HttpStatusCode.OK,
            ProjectsResponse(
                items = items,
                nextCursor = nextCursor,
            ),
        )
    }

    post<Paths.projectsPost> {
        val ownerIdString =
            call.request.headers["X-Forwarded-User"]
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Missing X-Forwarded-User header"),
                )

        val ownerId =
            try {
                Uuid.parse(ownerIdString)
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid X-Forwarded-User format"))
            }

        val request =
            try {
                call.receive<CreateProjectRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            }

        val createdProject = projectController.createProject(request.name, ownerId)
        call.respond(HttpStatusCode.Created, createdProject)
    }

    get<Paths.projectsProjectIdGet> { params ->
        val projectId = params.projectId.toUuidOrBadRequest("project_id")

        val response = projectController.getProjectDetails(projectId)
        call.respond(HttpStatusCode.OK, response)
    }
    get<Paths.projectsProjectIdAdminsGet> { params ->
        val projectId = params.projectId.toUuidOrBadRequest("project_id")

        val admins = projectController.getProjectAdmins(projectId)
        call.respond(HttpStatusCode.OK, admins)
    }

    post<Paths.projectsProjectIdAdminsPost> { params ->
        val projectId = params.projectId.toUuidOrBadRequest("project_id")

        val request =
            try {
                call.receive<AddProjectAdminRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
            }

        val userId =
            try {
                Uuid.parse(request.userId)
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user_id format"))
            }

        projectController.addProjectAdmin(projectId, userId)
        call.respond(HttpStatusCode.NoContent)
    }

    delete<Paths.projectsProjectIdAdminsUserIdDelete> { params ->
        val projectId = params.projectId.toUuidOrBadRequest("project_id")

        // Paths定義で userId は既に Uuid 型になっているので変換不要
        val userId = params.userId

        projectController.deleteProjectAdmin(projectId, userId)
        call.respond(HttpStatusCode.NoContent)
    }

    get<Paths.projectsProjectIdClientsGet> { params ->
        val projectId = params.projectId.toUuidOrBadRequest("project_id")

        val clients = projectController.getProjectClients(projectId)
        call.respond(HttpStatusCode.OK, clients)
    }

    post<Paths.projectsProjectIdClientsPost> { params ->
        val projectId = params.projectId.toUuidOrBadRequest("project_id")

        val newClient = projectController.createProjectClient(projectId)
        call.respond(HttpStatusCode.Created, newClient)
    }

    delete<Paths.projectsProjectIdClientsClientIdDelete> { params ->
        val projectId = params.projectId.toUuidOrBadRequest("project_id")
        val clientId = params.clientId

        projectController.deleteProjectClient(projectId, clientId)
        call.respond(HttpStatusCode.NoContent)
    }
}
