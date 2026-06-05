package jp.trap.plutus.pteron.features.project.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object ProjectAdminTable : Table("project_admins") {
    val projectId = javaUUID("project_id")
    val userId = javaUUID("user_id")

    override val primaryKey = PrimaryKey(projectId, userId)
}
