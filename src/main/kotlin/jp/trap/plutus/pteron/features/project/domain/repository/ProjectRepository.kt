package jp.trap.plutus.pteron.features.project.domain.repository

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.features.project.domain.model.Project

interface ProjectRepository {
    suspend fun findById(projectId: ProjectId): Project?

    suspend fun findAll(): List<Project>

    suspend fun save(project: Project): Project
}
