package jp.trap.plutus.pteron.config

object Environment {
    val DEBUG_MODE: Boolean by lazy {
        System.getenv("DEBUG_MODE")?.toBoolean() ?: false
    }
}