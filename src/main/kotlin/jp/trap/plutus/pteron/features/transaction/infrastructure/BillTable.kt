package jp.trap.plutus.pteron.features.transaction.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object BillTable : UUIDTable("bills", "id") {
    val amount = long("amount")
    val userId = uuid("user_id")
    val projectId = uuid("project_id")
    val description = varchar("description", 1024).nullable()
    val status = varchar("status", 32)
    val createdAt = timestamp("created_at")
}
