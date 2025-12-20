package jp.trap.plutus.pteron.features.transaction.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object TransactionTable : UUIDTable("transactions", "id") {
    val type = varchar("type", 32)
    val amount = long("amount")
    val projectId = uuid("project_id")
    val userId = uuid("user_id")
    val description = varchar("description", 1024).nullable()
    val createdAt = timestamp("created_at")
}
