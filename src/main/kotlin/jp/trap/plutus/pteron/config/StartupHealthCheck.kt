package jp.trap.plutus.pteron.config

import io.grpc.Status
import jp.trap.plutus.api.CornucopiaServiceGrpcKt.CornucopiaServiceCoroutineStub
import jp.trap.plutus.api.listAccountsRequest
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.ResultSet

object StartupHealthCheck {
    private val logger = LoggerFactory.getLogger(StartupHealthCheck::class.java)

    fun verifyDatabase(database: Database) {
        logger.info("データベース接続を確認中...")
        try {
            transaction(database) {
                exec("SELECT 1") { rs: ResultSet ->
                    rs.next()
                }
            }
            logger.info("データベース接続確認: OK")
        } catch (e: Exception) {
            logger.error("データベース接続に失敗しました", e)
            throw IllegalStateException("データベースに接続できません: ${e.message}", e)
        }
    }

    suspend fun verifyGrpc(stub: CornucopiaServiceCoroutineStub) {
        logger.info("gRPC接続を確認中...")
        try {
            val start = System.currentTimeMillis()
            val response = stub.listAccounts(listAccountsRequest { limit = 1 })
            val elapsed = System.currentTimeMillis() - start
            logger.info("gRPC接続確認: OK (Response: ${response.totalCount}, ${elapsed}ms)")
        } catch (e: Exception) {
            val status = Status.fromThrowable(e)
            val code = status.code
            logger.error("gRPC接続に失敗しました", e)
            throw IllegalStateException("gRPCサーバーに接続できません: ${status.description}", e)
        }
    }
}
