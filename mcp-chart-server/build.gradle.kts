plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("ru.courseai.currencywatch.chartmcp.MainKt")
}

group = "ru.courseai.currencywatch"
version = "0.1.0"

val mcpSdkVersion = libs.versions.mcp.get()

configurations.configureEach {
    resolutionStrategy {
        force(
            "io.modelcontextprotocol:kotlin-sdk-server:$mcpSdkVersion",
            "io.modelcontextprotocol:kotlin-sdk-server-jvm:$mcpSdkVersion",
            "io.modelcontextprotocol:kotlin-sdk-core:$mcpSdkVersion",
            "io.modelcontextprotocol:kotlin-sdk-core-jvm:$mcpSdkVersion",
        )
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.mcp.kotlin.server)
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
}

kotlin {
    jvmToolchain(21)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
