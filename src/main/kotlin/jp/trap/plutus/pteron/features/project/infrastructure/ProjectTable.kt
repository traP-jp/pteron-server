package jp.trap.plutus.pteron.features.project.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID

object ProjectTable : UUIDTable("projects", "id") {
    val name = varchar("name", 32).uniqueIndex()
    val ownerId = javaUUID("owner_id")
    val accountId = javaUUID("account_id").uniqueIndex()
    val url = varchar("url", 2048).nullable()
}
