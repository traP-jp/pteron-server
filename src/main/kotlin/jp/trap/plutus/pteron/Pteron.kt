package org.example.jp.trap.plutus.pteron

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(AppModule.module)
    }



    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is IllegalArgumentException ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Error(cause.message ?: "Bad request"),
                    )

                is NoSuchElementException ->
                    call.respond(
                        HttpStatusCode.NotFound,
                        Error(cause.message ?: "Not found"),
                    )

                else ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        Error(cause.message ?: "Unknown error"),
                    )
            }
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, Error("Resource not found"))
        }
    }

    install(DefaultHeaders)
    install(DropwizardMetrics) {
        val reporter =
            Slf4jReporter
                .forRegistry(registry)
                .outputTo(this@module.log)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build()
        reporter.start(10, TimeUnit.SECONDS)
    }
    install(ContentNegotiation) {
        json()
    }
    install(AutoHeadResponse) // see https://ktor.io/docs/autoheadresponse.html
    install(
        Compression,
        jp.trap.plutus.pteron.openapi.public.ApplicationCompressionConfiguration()
    ) // see https://ktor.io/docs/compression.html
    install(
        Compression,
        jp.trap.plutus.pteron.openapi.internal.ApplicationCompressionConfiguration()
    ) // see https://ktor.io/docs/compression.html
    install(
        HSTS,
        jp.trap.plutus.pteron.openapi.public.ApplicationHstsConfiguration()
    ) // see https://ktor.io/docs/hsts.html
    install(
        HSTS,
        jp.trap.plutus.pteron.openapi.internal.ApplicationHstsConfiguration()
    ) // see https://ktor.io/docs/hsts.html
    install(Resources)

    routing {
        route("api/v1") {
            // Public API
        }

        route("api/internal") {
            // Internal API
        }
    }
}
