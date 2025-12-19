package jp.trap.plutus.pteron.features.user.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable

object UserTable : UUIDTable("users", "id") {
    val name = varchar("name", 32)
    val accountId = uuid("account_id")
}
