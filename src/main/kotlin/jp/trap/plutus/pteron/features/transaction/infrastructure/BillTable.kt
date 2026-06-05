package jp.trap.plutus.pteron.features.transaction.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

object BillTable : UUIDTable("bills", "id") {
    val amount = long("amount")
    val userId = javaUUID("user_id")
    val projectId = javaUUID("project_id")
    val description = varchar("description", 1024).nullable()
    val status = varchar("status", 32)
    val successUrl = varchar("success_url", 2048).nullable()
    val cancelUrl = varchar("cancel_url", 2048).nullable()
    val createdAt = timestamp("created_at")
}
