package jp.trap.plutus.pteron.features.system.domain.repository

import jp.trap.plutus.pteron.features.system.domain.model.SystemConfig

interface SystemConfigRepository {
    suspend fun findByKey(key: String): SystemConfig?
    suspend fun save(config: SystemConfig): SystemConfig
}
