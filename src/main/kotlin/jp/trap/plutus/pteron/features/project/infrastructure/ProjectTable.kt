package jp.trap.plutus.pteron.features.project.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object ProjectTable : UUIDTable("projects", "id") {
    val name = varchar("name", 32).uniqueIndex()
    val ownerId = uuid("owner_id")
    val accountId = uuid("account_id").uniqueIndex()
    val url = varchar("url", 2048).nullable()
}
