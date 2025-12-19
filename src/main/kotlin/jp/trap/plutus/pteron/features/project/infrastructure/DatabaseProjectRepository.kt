package jp.trap.plutus.pteron.features.project.infrastructure

import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.project.domain.model.ApiClient
import jp.trap.plutus.pteron.features.project.domain.model.Project
import jp.trap.plutus.pteron.features.project.domain.model.ProjectName
import jp.trap.plutus.pteron.features.project.domain.model.ProjectUrl
import jp.trap.plutus.pteron.features.project.domain.repository.ProjectRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.koin.core.annotation.Single
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@Single(binds = [ProjectRepository::class])
class DatabaseProjectRepository : ProjectRepository {
    override suspend fun findById(projectId: ProjectId): Project? {
        val projectRow =
            ProjectTable
                .selectAll()
                .where { ProjectTable.id eq projectId.value.toJavaUuid() }
                .singleOrNull() ?: return null

        val adminIds =
            ProjectAdminTable
                .selectAll()
                .where { ProjectAdminTable.projectId eq projectId.value.toJavaUuid() }
                .map { UserId(it[ProjectAdminTable.userId].toKotlinUuid()) }

        val apiClients =
            ApiClientTable
                .selectAll()
                .where { ApiClientTable.projectId eq projectId.value.toJavaUuid() }
                .map { it.toApiClient() }

        return projectRow.toProject(adminIds, apiClients)
    }

    override suspend fun findByName(name: ProjectName): Project? {
        val projectRow =
            ProjectTable
                .selectAll()
                .where { ProjectTable.name.lowerCase() eq name.normalized }
                .singleOrNull() ?: return null

        val projectId = projectRow[ProjectTable.id].value

        val adminIds =
            ProjectAdminTable
                .selectAll()
                .where { ProjectAdminTable.projectId eq projectId }
                .map { UserId(it[ProjectAdminTable.userId].toKotlinUuid()) }

        val apiClients =
            ApiClientTable
                .selectAll()
                .where { ApiClientTable.projectId eq projectId }
                .map { it.toApiClient() }

        return projectRow.toProject(adminIds, apiClients)
    }

    override suspend fun findAll(): List<Project> {
        val projectRows = ProjectTable.selectAll().toList()
        if (projectRows.isEmpty()) return emptyList()

        val projectIds = projectRows.map { it[ProjectTable.id].value }

        val adminsByProject =
            ProjectAdminTable
                .selectAll()
                .where { ProjectAdminTable.projectId inList projectIds }
                .groupBy(
                    { it[ProjectAdminTable.projectId].toKotlinUuid() },
                    { UserId(it[ProjectAdminTable.userId].toKotlinUuid()) },
                )

        val clientsByProject =
            ApiClientTable
                .selectAll()
                .where { ApiClientTable.projectId inList projectIds }
                .groupBy(
                    { it[ApiClientTable.projectId].toKotlinUuid() },
                    { it.toApiClient() },
                )

        return projectRows.map { row ->
            val id = row[ProjectTable.id].value.toKotlinUuid()
            row.toProject(
                adminIds = adminsByProject[id] ?: emptyList(),
                apiClients = clientsByProject[id] ?: emptyList(),
            )
        }
    }

    override suspend fun save(project: Project): Project {
        ProjectTable.upsert {
            it[id] = project.id.value.toJavaUuid()
            it[name] = project.name.value
            it[ownerId] = project.ownerId.value.toJavaUuid()
            it[accountId] = project.accountId.value.toJavaUuid()
            it[url] = project.url?.value
        }

        // Sync admins
        ProjectAdminTable.deleteWhere {
            projectId eq project.id.value.toJavaUuid()
        }
        project.adminIds.forEach { adminId ->
            ProjectAdminTable.insert {
                it[projectId] = project.id.value.toJavaUuid()
                it[userId] = adminId.value.toJavaUuid()
            }
        }

        // Sync API clients
        ApiClientTable.deleteWhere {
            projectId eq project.id.value.toJavaUuid()
        }
        project.apiClients.forEach { client ->
            ApiClientTable.insert {
                it[id] = client.clientId.toJavaUuid()
                it[projectId] = project.id.value.toJavaUuid()
                it[clientSecret] = client.clientSecretHashed
                it[createdAt] = client.createdAt
            }
        }

        return project
    }

    override suspend fun delete(projectId: ProjectId) {
        ProjectTable.deleteWhere {
            id eq projectId.value.toJavaUuid()
        }
    }

    private fun ResultRow.toProject(
        adminIds: List<UserId>,
        apiClients: List<ApiClient>,
    ): Project =
        Project(
            id = ProjectId(this[ProjectTable.id].value.toKotlinUuid()),
            name = ProjectName(this[ProjectTable.name]),
            ownerId = UserId(this[ProjectTable.ownerId].toKotlinUuid()),
            adminIds = adminIds,
            accountId = AccountId(this[ProjectTable.accountId].toKotlinUuid()),
            apiClients = apiClients,
            url = this[ProjectTable.url]?.let { ProjectUrl(it) },
        )

    private fun ResultRow.toApiClient(): ApiClient =
        ApiClient(
            clientId = this[ApiClientTable.id].value.toKotlinUuid(),
            clientSecretHashed = this[ApiClientTable.clientSecret],
            createdAt = this[ApiClientTable.createdAt],
        )
}
