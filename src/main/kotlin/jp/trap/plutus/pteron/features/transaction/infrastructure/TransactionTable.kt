package jp.trap.plutus.pteron.features.transaction.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object TransactionTable : UUIDTable("transactions", "id") {
    val type = varchar("type", 32)
    val amount = long("amount")
    val projectId = javaUUID("project_id").nullable()
    val userId = javaUUID("user_id").nullable()
    val description = varchar("description", 1024).nullable()
    val createdAt = timestamp("created_at")
}
