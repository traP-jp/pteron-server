package jp.trap.plutus.pteron.features.user.infrastructure

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID

object UserTable : UUIDTable("users", "id") {
    val name = varchar("name", 32)
    val accountId = javaUUID("account_id")
}
