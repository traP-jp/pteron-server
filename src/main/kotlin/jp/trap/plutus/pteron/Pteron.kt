package jp.trap.plutus.pteron

import com.codahale.metrics.Slf4jReporter
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.dropwizard.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.hsts.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.api.CornucopiaServiceGrpcKt.CornucopiaServiceCoroutineStub
import jp.trap.plutus.pteron.auth.bearerAuth
import jp.trap.plutus.pteron.auth.forwardAuth
import jp.trap.plutus.pteron.common.exception.*
import jp.trap.plutus.pteron.config.Environment
import jp.trap.plutus.pteron.config.StartupHealthCheck
import jp.trap.plutus.pteron.di.AppModule
import jp.trap.plutus.pteron.features.project.controller.projectRoutes
import jp.trap.plutus.pteron.features.project.service.ProjectService
import jp.trap.plutus.pteron.features.stats.controller.statsRoutes
import jp.trap.plutus.pteron.features.stats.service.StatsUpdateJob
import jp.trap.plutus.pteron.features.system.service.SystemAccountService
import jp.trap.plutus.pteron.features.transaction.controller.billRoutes
import jp.trap.plutus.pteron.features.transaction.controller.publicApiRoutes
import jp.trap.plutus.pteron.features.transaction.controller.transactionRoutes
import jp.trap.plutus.pteron.features.user.controller.userRoutes
import jp.trap.plutus.pteron.features.user.service.UserService
import jp.trap.plutus.pteron.openapi.public.models.Error
import jp.trap.plutus.pteron.utils.trapId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ksp.generated.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.util.concurrent.TimeUnit

fun main() {
    Environment.validate()

    embeddedServer(
        factory = Netty,
        port = Environment.PORT,
        host = Environment.HOST,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(AppModule.module)
    }

    val database by inject<Database>()
    val stub by inject<CornucopiaServiceCoroutineStub>()
    val systemAccountService by inject<SystemAccountService>()
    val statsUpdateJob by inject<StatsUpdateJob>()

    runBlocking {
        launch(Dispatchers.IO) {
            StartupHealthCheck.verifyDatabase(database)
        }
        launch {
            StartupHealthCheck.verifyGrpc(stub)
            systemAccountService.initialize()
        }
    }

    statsUpdateJob.start(this)


    val userService by inject<UserService>()

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is BadRequestException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Error(cause.message ?: "Bad request"),
                    )
                }

                is UnauthorizedException -> {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        Error(cause.message ?: "Unauthorized"),
                    )
                }

                is ForbiddenException -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        Error(cause.message ?: "Forbidden"),
                    )
                }

                is NotFoundException -> {
                    call.respond(
                        HttpStatusCode.NotFound,
                        Error(cause.message ?: "Not found"),
                    )
                }

                is ConflictException -> {
                    call.respond(
                        HttpStatusCode.Conflict,
                        Error(cause.message ?: "Conflict"),
                    )
                }

                is IllegalArgumentException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Error(cause.message ?: "Bad request"),
                    )
                }

                is NoSuchElementException -> {
                    call.respond(
                        HttpStatusCode.NotFound,
                        Error(cause.message ?: "Not found"),
                    )
                }

                is ContentTransformationException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Error("Invalid request body: ${cause.message}"),
                    )
                }

                else -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        Error(cause.message ?: "Unknown error"),
                    )
                }
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
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }
    install(HSTS) {
        maxAgeInSeconds = TimeUnit.DAYS.toSeconds(365)
        includeSubDomains = true
        preload = false
    }
    install(Resources)

    val projectService by inject<ProjectService>()

    install(Authentication) {
        forwardAuth("ForwardAuth") {
            verify { context ->
                val call = context.call
                try {
                    val userId = call.trapId
                    userService.ensureUser(userId)
                    context.principal(UserIdPrincipal(userId.value))
                } catch (_: Exception) {
                    context.challenge("ForwardAuth", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                        call.response.status(HttpStatusCode.Unauthorized)
                        challenge.complete()
                    }
                }
            }
        }

        bearerAuth("BearerAuth") {
            validate { clientId, clientSecret ->
                projectService.authenticateApiClient(clientId, clientSecret)
            }
        }
    }

    routing {
        route("health") {
            get {
                call.respond(HttpStatusCode.OK)
            }
        }

        authenticate("BearerAuth") {
            route("api/v1") {
                // Public API (外部プロジェクト開発者向け)
                publicApiRoutes()
            }
        }

        authenticate("ForwardAuth") {
            route("api/internal") {
                // Internal API (ダッシュボード向け)
                userRoutes()
                projectRoutes()
                transactionRoutes()
                billRoutes()
                statsRoutes()
            }
        }
    }
}
