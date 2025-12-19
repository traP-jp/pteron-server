package jp.trap.plutus.pteron.auth

import io.ktor.server.auth.*

class ForwardAuthConfig(
    name: String?,
) : AuthenticationProvider.Config(name) {
    internal var authenticationFunction: suspend (AuthenticationContext) -> Unit = {}

    fun verify(block: suspend (AuthenticationContext) -> Unit) {
        authenticationFunction = block
    }
}

fun AuthenticationConfig.forwardAuth(
    name: String? = null,
    configure: ForwardAuthConfig.() -> Unit,
) {
    val config = ForwardAuthConfig(name).apply(configure)
    val provider =
        object : AuthenticationProvider(config) {
            override suspend fun onAuthenticate(context: AuthenticationContext) {
                config.authenticationFunction(context)
            }
        }
    register(provider)
}
