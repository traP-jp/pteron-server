package jp.trap.plutus.pteron.utils

import io.ktor.server.application.*
import io.ktor.server.request.*
import jp.trap.plutus.pteron.config.Environment
import jp.trap.plutus.pteron.features.user.domain.model.Username

val ApplicationCall.trapId: Username
    get() =
        Username(
            request.header("X-Forwarded-User")
                ?: if (Environment.DEBUG_MODE) {
                    "traP"
                } else {
                    throw IllegalStateException("X-Forwarded-User header is missing")
                },
        )
