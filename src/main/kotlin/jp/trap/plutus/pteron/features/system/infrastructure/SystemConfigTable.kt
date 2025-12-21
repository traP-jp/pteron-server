package jp.trap.plutus.pteron.features.system.infrastructure

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object SystemConfigTable : IdTable<String>("system_configs") {
    override val id: Column<EntityID<String>> = varchar("key", 64).entityId()
    val value = varchar("value", 255)

    override val primaryKey = PrimaryKey(id)
}
