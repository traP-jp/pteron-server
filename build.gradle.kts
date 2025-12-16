import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.google.protobuf.gradle.id

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

    // gRPC Client
    implementation(libs.grpc.netty)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.kotlin)
    implementation(libs.protobuf.java.util)
}

val openApiSpecFile = rootProject.file("../../../specs/openapi/pteron.yaml")
val protoSpecDir = rootProject.file("../../../specs/protobuf")
val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")

protobuf {
    protoc {
        artifact = libs.protobuf.kotlin.get().let {
            "${it.group}:protoc:${it.version}"
        }
    }
    plugins {
        id("grpckt") {
            artifact = libs.grpc.kotlin.stub.get().let {
                "${it.group}:protoc-gen-grpc-kotlin:${it.version}:jdk8@jar"
            }
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}

openApiGenerate {
    generatorName.set("kotlin-server")
    library.set("ktor")
    inputSpec.set(openApiSpecFile.toURI().toString())
    outputDir.set(generatedOpenApiDir.map { it.asFile.absolutePath })
    packageName.set("jp.trap.plutus.pteron.openapi")
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
        ),
    )
}

sourceSets {
    main {
        proto {
            srcDir(protoSpecDir)
        }
        kotlin.srcDir(generatedOpenApiDir.map { it.dir("src/main/kotlin") })
        resources {
            srcDir(openApiSpecFile.parentFile)
        }
    }
}

tasks {
    val generateCode by registering {
        group = "build"
        description = "Generate all code (OpenAPI and Protobuf)"
        dependsOn(openApiGenerate, "generateProto")
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
