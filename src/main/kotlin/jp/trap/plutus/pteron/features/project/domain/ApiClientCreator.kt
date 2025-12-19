package jp.trap.plutus.pteron.features.project.domain

import com.github.f4b6a3.uuid.UuidCreator
import jp.trap.plutus.pteron.features.project.domain.model.ApiClient
import org.mindrot.jbcrypt.BCrypt
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.uuid.toKotlinUuid

data class ApiClientCreationResult(
    val plainSecret: String,
    val apiClient: ApiClient,
)

object ApiClientCreator {
    private const val SECRET_BYTE_LENGTH = 32
    private const val BCRYPT_LOG_ROUNDS = 12
    private val secureRandom = SecureRandom()

    fun createApiClient(): ApiClientCreationResult {
        val plainSecret = generateSecret()
        val hashedSecret = BCrypt.hashpw(plainSecret, BCrypt.gensalt(BCRYPT_LOG_ROUNDS))

        val apiClient = ApiClient(
            clientId = UuidCreator.getTimeOrderedEpoch().toKotlinUuid(),
            clientSecretHashed = hashedSecret,
            createdAt = Clock.System.now(),
        )

        return ApiClientCreationResult(
            plainSecret = plainSecret,
            apiClient = apiClient,
        )
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

