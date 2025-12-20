package jp.trap.plutus.pteron.features.project.domain.repository

import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.features.project.domain.model.Project
import jp.trap.plutus.pteron.features.project.domain.model.ProjectName
import kotlin.uuid.Uuid

interface ProjectRepository {
    suspend fun findById(projectId: ProjectId): Project?

    suspend fun findByName(name: ProjectName): Project?

    suspend fun findByApiClientId(clientId: Uuid): Project?

    suspend fun findAll(): List<Project>

    suspend fun save(project: Project): Project

    suspend fun delete(projectId: ProjectId)
}
