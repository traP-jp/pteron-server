import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.protobuf.gradle.id
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.ksp)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.shadow)
    alias(libs.plugins.protobuf)
    application
}

group = "jp.trap"
version = "1.0.0"

application {
    mainClass.set("jp.trap.plutus.pteron.PteronKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.hsts)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.metrics)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.apache)

    // Koin
    implementation(libs.koin.ktor)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)

    // Exposed
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.kotlin.datetime)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Logging
    implementation(libs.logback.classic)
    implementation(libs.koin.logger.slf4j)

    // Database
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)
    runtimeOnly(libs.mariadb)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit5)

    // Utils
    implementation(libs.uuid.creator)
    implementation(libs.jbcrypt)

    // gRPC Client
    implementation(libs.grpc.netty)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.kotlin)
    implementation(libs.protobuf.java.util)
    implementation(libs.grpc.stub)
}

val internalApiUrl = "https://raw.githubusercontent.com/traP-jp/plutus/main/specs/openapi/internal.yaml"
val publicApiUrl = "https://raw.githubusercontent.com/traP-jp/plutus/main/specs/openapi/pteron.yaml"
val cornucopiaProtoUrl = "https://raw.githubusercontent.com/traP-jp/plutus/main/specs/protobuf/cornucopia.proto"

val specsDir = layout.buildDirectory.dir("specs")
val generatedOpenApiInternalDir = layout.buildDirectory.dir("generated/openapi/internal")
val generatedOpenApiPublicDir = layout.buildDirectory.dir("generated/openapi/public")

protobuf {
    protoc {
        artifact =
            libs.protobuf.kotlin.get().let {
                "${it.group}:protoc:${it.version}"
            }
    }
    plugins {
        id("grpckt") {
            artifact =
                libs.grpc.kotlin.stub.get().let {
                    "${it.group}:protoc-gen-grpc-kotlin:${it.version}:jdk8@jar"
                }
        }
        id("grpc") {
            artifact =
                libs.grpc.netty.get().let {
                    "io.grpc:protoc-gen-grpc-java:${it.version}"
                }
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.dependsOn("downloadSpecs")
            task.plugins {
                id("grpckt")
                id("grpc")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir(specsDir.map { it.dir("protobuf") })
        }
        kotlin.srcDir(generatedOpenApiInternalDir.map { it.dir("src/main/kotlin") })
        kotlin.srcDir(generatedOpenApiPublicDir.map { it.dir("src/main/kotlin") })
        resources {
            srcDir(specsDir)
        }
    }
}

tasks {
    val downloadSpecs by registering {
        group = "build setup"
        description = "Download specs from remote"
        outputs.dir(specsDir)

        doLast {
            val openApiDir = specsDir.get().dir("openapi").asFile
            val protoDir = specsDir.get().dir("protobuf").asFile
            openApiDir.mkdirs()
            protoDir.mkdirs()

            fun download(
                url: String,
                dest: File,
            ) {
                val connection = URI(url).toURL().openConnection()
                connection.getInputStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            download(internalApiUrl, File(openApiDir, "internal.yaml"))
            download(publicApiUrl, File(openApiDir, "pteron.yaml"))
            download(cornucopiaProtoUrl, File(protoDir, "cornucopia.proto"))
        }
    }

    val generateInternalApi by registering(GenerateTask::class) {
        dependsOn(downloadSpecs)
        generatorName.set("kotlin-server")
        library.set("ktor")
        inputSpec.set(
            specsDir.map {
                it
                    .file("openapi/internal.yaml")
                    .asFile
                    .toURI()
                    .toString()
            },
        )
        outputDir.set(generatedOpenApiInternalDir.map { it.asFile.path })
        packageName.set("jp.trap.plutus.pteron.openapi.internal")
        configOptions.set(
            mapOf(
                "serializationLibrary" to "kotlinx-serialization",
                "serializableModel" to "true",
                "enumPropertyNaming" to "UPPERCASE",
            ),
        )
        typeMappings.set(
            mapOf(
                "UUID" to "kotlin.uuid.Uuid",
                "uuid" to "kotlin.uuid.Uuid",
                "string+date-time" to "kotlin.time.Instant",
                "URI" to "kotlin.String",
            ),
        )
    }

    val generatePublicApi by registering(GenerateTask::class) {
        dependsOn(downloadSpecs)
        generatorName.set("kotlin-server")
        library.set("ktor")
        inputSpec.set(
            specsDir.map {
                it
                    .file("openapi/pteron.yaml")
                    .asFile
                    .toURI()
                    .toString()
            },
        )
        outputDir.set(generatedOpenApiPublicDir.map { it.asFile.path })
        packageName.set("jp.trap.plutus.pteron.openapi.public")
        configOptions.set(
            mapOf(
                "serializationLibrary" to "kotlinx-serialization",
                "serializableModel" to "true",
                "enumPropertyNaming" to "UPPERCASE",
            ),
        )
        typeMappings.set(
            mapOf(
                "UUID" to "kotlin.uuid.Uuid",
                "uuid" to "kotlin.uuid.Uuid",
                "string+date-time" to "kotlin.time.Instant",
                "URI" to "kotlin.String",
            ),
        )
    }

    val generateCode by registering {
        group = "build"
        description = "Generate all code (OpenAPI and Protobuf)"
        dependsOn(generateInternalApi, generatePublicApi, "generateProto")
    }

    build {
        dependsOn(shadowJar)
    }

    compileKotlin {
        dependsOn(generateCode)
    }

    matching { it.name == "kspKotlin" }.configureEach {
        dependsOn(generateCode)
    }

    test {
        useJUnitPlatform()
    }

    withType<ShadowJar> {
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-opt-in=io.ktor.utils.io.InternalAPI")
    }
    jvmToolchain(24)
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    filter {
        exclude { it.file.path.contains("generated") }
    }
}
