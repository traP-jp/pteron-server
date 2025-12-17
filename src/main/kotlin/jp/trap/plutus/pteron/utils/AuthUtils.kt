package jp.trap.plutus.pteron.utils

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

val ApplicationCall.trapId: String?
    get() = request.header("X-Forwarded-User")
