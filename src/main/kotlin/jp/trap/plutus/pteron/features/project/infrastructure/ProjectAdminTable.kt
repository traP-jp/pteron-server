package jp.trap.plutus.pteron.features.project.infrastructure

import org.jetbrains.exposed.v1.core.Table

object ProjectAdminTable : Table("project_admins") {
    val projectId = uuid("project_id")
    val userId = uuid("user_id")

    override val primaryKey = PrimaryKey(projectId, userId)
}
