package jp.trap.plutus.pteron.features.system.infrastructure

import jp.trap.plutus.pteron.features.system.domain.model.SystemConfig
import jp.trap.plutus.pteron.features.system.domain.repository.SystemConfigRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.koin.core.annotation.Single

@Single
class DatabaseSystemConfigRepository : SystemConfigRepository {
    override suspend fun findByKey(key: String): SystemConfig? =
        SystemConfigTable
            .selectAll()
            .where { SystemConfigTable.id eq key }
            .singleOrNull()
            ?.let {
                SystemConfig(
                    key = it[SystemConfigTable.id].value,
                    value = it[SystemConfigTable.value],
                )
            }

    override suspend fun save(config: SystemConfig): SystemConfig {
        SystemConfigTable.upsert {
            it[id] = config.key
            it[value] = config.value
        }
        return config
    }
}
