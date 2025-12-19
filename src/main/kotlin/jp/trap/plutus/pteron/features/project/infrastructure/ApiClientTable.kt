package jp.trap.plutus.pteron.features.project.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object ApiClientTable : UUIDTable("api_clients", "client_id") {
    val projectId = uuid("project_id")
    val clientSecret = varchar("client_secret", 255)
    val createdAt = timestamp("created_at")
}
