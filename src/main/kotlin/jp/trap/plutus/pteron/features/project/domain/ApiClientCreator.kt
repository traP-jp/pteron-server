package jp.trap.plutus.pteron.features.project.domain

import com.github.f4b6a3.uuid.UuidCreator
import kotlin.time.Clock
import kotlin.uuid.toKotlinUuid

object ApiClientCreator {
    fun createApiClient() =
        ApiClient(
            UuidCreator.getTimeOrderedEpoch().toKotlinUuid(),
            generateSecret(),
            Clock.System.now(),
        )

    private fun generateSecret(): String {
        TODO()
    }
}
