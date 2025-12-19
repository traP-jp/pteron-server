package jp.trap.plutus.pteron.features.project.domain

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import kotlin.uuid.Uuid

interface ProjectRepository {
    suspend fun findById(projectId: ProjectId): Project?

    suspend fun batchFind(
        limit: Int,
        cursor: String?,
    ): Pair<List<Project>, String?>

    suspend fun save(project: Project): Project
}
