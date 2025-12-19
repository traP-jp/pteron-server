package jp.trap.plutus.pteron.di

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jp.trap.plutus.pteron.config.Environment
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class DatabaseModule {
    @Single
    fun provideDataSource(): HikariDataSource {
        val config =
            HikariConfig().apply {
                jdbcUrl = Environment.DATABASE_URL
                username = Environment.DATABASE_USER
                password = Environment.DATABASE_PASSWORD
                maximumPoolSize = 10
                minimumIdle = 2
                idleTimeout = 30000
                connectionTimeout = 30000
                maxLifetime = 1800000
            }
        return HikariDataSource(config)
    }

    @Single
    fun provideDatabase(dataSource: HikariDataSource): Database {
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        return Database.connect(dataSource)
    }
}
