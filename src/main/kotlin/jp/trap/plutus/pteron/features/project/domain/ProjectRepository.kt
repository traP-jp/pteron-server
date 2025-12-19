package jp.trap.plutus.pteron.features.project.domain

import jp.trap.plutus.pteron.common.domain.model.ProjectId

interface ProjectRepository {
    suspend fun findById(projectId: ProjectId): Project?

    suspend fun findAll(): List<Project>

    suspend fun save(project: Project): Project
}
