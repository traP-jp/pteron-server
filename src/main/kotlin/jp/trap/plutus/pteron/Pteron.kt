package jp.trap.plutus.pteron

import com.codahale.metrics.Slf4jReporter
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.dropwizard.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.pteron.di.AppModule
import org.koin.ksp.generated.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.util.concurrent.TimeUnit

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
