plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("ru.courseai.currencywatch.mcp.MainKt")
}

group = "ru.courseai.currencywatch"
version = "0.1.0"

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.mcp.kotlin.server)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
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
