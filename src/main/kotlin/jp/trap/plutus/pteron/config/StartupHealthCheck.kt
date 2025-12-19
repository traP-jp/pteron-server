package jp.trap.plutus.pteron.config

import io.grpc.Status
import jp.trap.plutus.api.CornucopiaServiceGrpcKt.CornucopiaServiceCoroutineStub
import jp.trap.plutus.api.getAccountRequest
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

    fun verifyGrpc(stub: CornucopiaServiceCoroutineStub) {
        logger.info("gRPC接続を確認中...")
        try {
            runBlocking {
                stub.getAccount(getAccountRequest { accountId = "health_check_probe" })
            }
            logger.info("gRPC接続確認: OK")
        } catch (e: Exception) {
            val status = Status.fromThrowable(e)
            val code = status.code
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                logger.error("gRPC接続に失敗しました", e)
                throw IllegalStateException("gRPCサーバーに接続できません: ${status.description}", e)
            }
            // その他のエラー(NOT_FOUNDなど)は接続成功とみなす
            logger.info("gRPC接続確認: OK (Response: ${code})")
        }
    }
}
