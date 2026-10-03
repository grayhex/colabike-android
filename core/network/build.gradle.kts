import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Generated API v1 client and the one transport around it. Plain Kotlin: no Android here.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get())) } }

lint {
    warningsAsErrors = true
    abortOnError = true
}

// --- API contract (docs/architecture.md, "API v1 client") -----------------------------------
// api/openapi.json is the snapshot of https://colabike.ru/api/v1/openapi.json the build uses;
// api/openapi.json.sha256 pins it, so a changed contract never changes the build silently.

val contractFile = rootProject.layout.projectDirectory.file("api/openapi.json")
val checksumFile = rootProject.layout.projectDirectory.file("api/openapi.json.sha256")
val generatorInput = layout.buildDirectory.file("openapi/openapi.json")
val generatedDir = layout.buildDirectory.dir("generated/openapi")
val liveContractUrl =
    providers.gradleProperty("contractUrl").orElse("https://colabike.ru/api/v1/openapi.json")

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** The stored form: pretty JSON, Cyrillic kept as is, numbers and key order untouched. */
fun canonical(raw: String): String = JsonOutput.prettyPrint(raw, true) + "\n"

abstract class VerifyApiContract : DefaultTask() {
    @get:InputFile abstract val contract: RegularFileProperty
    @get:InputFile abstract val checksum: RegularFileProperty

    @TaskAction
    fun verify() {
        val bytes = contract.get().asFile.readBytes()
        val actual =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
        val expected = checksum.get().asFile.readText().trim()
        check(actual == expected) {
            "api/openapi.json does not match api/openapi.json.sha256 ($actual != $expected). " +
                "Refresh the snapshot with ./gradlew :core:network:updateApiContract and review the diff."
        }
    }
}

val verifyApiContract =
    tasks.register<VerifyApiContract>("verifyApiContract") {
        group = "verification"
        description = "Fails when the contract snapshot and its checksum disagree."
        contract.set(contractFile)
        checksum.set(checksumFile)
    }

tasks.named("check") { dependsOn(verifyApiContract) }

tasks.register("updateApiContract") {
    group = "api"
    description = "Downloads the live contract into api/openapi.json and pins its checksum."
    val url = liveContractUrl
    val target = contractFile.asFile
    val sum = checksumFile.asFile
    doLast {
        val text = canonical(URI(url.get()).toURL().readText())
        target.writeText(text)
        sum.writeText(sha256(text.toByteArray()) + "\n")
        logger.lifecycle(
            "Contract updated from ${url.get()}: review the diff, then commit both files."
        )
    }
}

tasks.register("checkApiContractDrift") {
    group = "api"
    description = "Fails when the live contract differs from the snapshot (scheduled CI job)."
    val url = liveContractUrl
    val snapshot = contractFile.asFile
    doLast {
        val live = canonical(URI(url.get()).toURL().readText())
        check(live == snapshot.readText()) {
            "The live contract at ${url.get()} differs from api/openapi.json: " +
                "run ./gradlew :core:network:updateApiContract and review what changed."
        }
    }
}

// Generator input: the snapshot with boolean constants turned into plain booleans. A `const`
// boolean becomes a one-value enum whose unknown-value fallback does not compile (cola#325);
// the server forbids them now, this keeps older contracts buildable.
val prepareGeneratorInput =
    tasks.register("prepareGeneratorInput") {
        inputs.file(contractFile)
        outputs.file(generatorInput)
        val source = contractFile.asFile
        val target = generatorInput
        doLast {
            fun plain(node: Any?): Any? =
                when (node) {
                    is Map<*, *> -> {
                        val boolean =
                            node["type"] == "boolean" ||
                                (node["type"] as? List<*>)?.contains("boolean") == true
                        node.entries
                            .filterNot { boolean && (it.key == "const" || it.key == "enum") }
                            .associate { it.key to plain(it.value) }
                    }
                    is List<*> -> node.map(::plain)
                    else -> node
                }
            val json = JsonOutput.toJson(plain(JsonSlurper().parse(source)))
            target.get().asFile.apply { parentFile.mkdirs() }.writeText(json)
        }
    }

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-okhttp4")
    inputSpec.set(generatorInput)
    outputDir.set(generatedDir)
    packageName.set("ru.colabike.api")
    typeMappings.set(mapOf("number" to "kotlin.Double"))
    // --additional-properties of the CLI; what cola docs/modules/api-v1.md checked.
    additionalProperties.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "enumUnknownDefaultCase" to "true",
            "omitGradleWrapper" to "true",
        )
    )
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    cleanupOutput.set(true)
}

tasks.named("openApiGenerate") { dependsOn(prepareGeneratorInput) }

sourceSets.main { kotlin.srcDir(generatedDir.map { it.dir("src/main/kotlin") }) }

tasks.named("compileKotlin") { dependsOn("openApiGenerate") }

dependencies {
    api(project(":core:model"))
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
