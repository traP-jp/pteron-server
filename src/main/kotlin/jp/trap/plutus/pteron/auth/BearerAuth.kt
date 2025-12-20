package jp.trap.plutus.pteron.auth

import io.ktor.http.*
import io.ktor.server.auth.*
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.features.project.domain.model.Project
import java.util.*
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Public API用Bearer認証のPrincipal
 * 認証されたプロジェクト情報を保持
 */
data class ProjectPrincipal(
    val project: Project,
)

/**
 * Bearer認証設定
 */
class BearerAuthConfig(
    name: String?,
) : AuthenticationProvider.Config(name) {
    internal var validateFunction: suspend (clientId: Uuid, clientSecret: String) -> Project? = { _, _ -> null }

    /**
     * APIクライアントの検証関数を設定
     * client_idとclient_secretからProjectを返す（認証失敗時はnull）
     */
    fun validate(block: suspend (clientId: Uuid, clientSecret: String) -> Project?) {
        validateFunction = block
    }
}

/**
 * Public API用Bearer認証プロバイダー
 *
 * Authorization: Bearer <base64(client_id:client_secret)> 形式のトークンを検証
 */
fun AuthenticationConfig.bearerAuth(
    name: String? = null,
    configure: BearerAuthConfig.() -> Unit,
) {
    val config = BearerAuthConfig(name).apply(configure)
    val provider =
        object : AuthenticationProvider(config) {
            override suspend fun onAuthenticate(context: AuthenticationContext) {
                val call = context.call

                // Authorization ヘッダーを取得
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader == null || !authHeader.startsWith("Bearer ", ignoreCase = true)) {
                    context.challenge("BearerAuth", AuthenticationFailedCause.NoCredentials) { challenge, _ ->
                        challenge.complete()
                    }
                    return
                }

                // トークンをデコード
                val token = authHeader.removePrefix("Bearer ").removePrefix("bearer ").trim()
                val credentials = decodeCredentials(token)

                if (credentials == null) {
                    context.challenge("BearerAuth", AuthenticationFailedCause.InvalidCredentials) { challenge, _ ->
                        challenge.complete()
                    }
                    return
                }

                val (clientId, clientSecret) = credentials

                // プロジェクトを検証
                val project = config.validateFunction(clientId, clientSecret)

                if (project == null) {
                    context.challenge("BearerAuth", AuthenticationFailedCause.InvalidCredentials) { challenge, _ ->
                        challenge.complete()
                    }
                    return
                }

                context.principal(ProjectPrincipal(project))
            }
        }
    register(provider)
}

/**
 * Base64エンコードされた "client_id:client_secret" をデコード
 */
private fun decodeCredentials(token: String): Pair<Uuid, String>? {
    return try {
        val decoded = String(Base64.getDecoder().decode(token), Charsets.UTF_8)
        val parts = decoded.split(":", limit = 2)
        if (parts.size != 2) return null

        val clientId = UUID.fromString(parts[0]).toKotlinUuid()
        val clientSecret = parts[1]
        Pair(clientId, clientSecret)
    } catch (_: Exception) {
        null
    }
}

/**
 * ApplicationCallから認証済みProjectPrincipalを取得する拡張関数
 */
val io.ktor.server.application.ApplicationCall.projectPrincipal: ProjectPrincipal?
    get() = principal<ProjectPrincipal>()

/**
 * 認証済みProjectIdを取得する拡張関数
 */
val io.ktor.server.application.ApplicationCall.projectId: ProjectId
    get() =
        projectPrincipal?.project?.id
            ?: throw IllegalStateException("Project principal not found. Ensure this route is authenticated with BearerAuth.")
