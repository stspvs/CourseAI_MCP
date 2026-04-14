plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("runMcp") {
    group = "application"
    description = "Запуск JVM MCP-сервера (модуль :mcp-server). Не Android/Compose."
    dependsOn(":mcp-server:run")
}

tasks.register("runChartMcp") {
    group = "application"
    description = "Запуск JVM MCP-сервера графиков QuickChart (модуль :mcp-chart-server)."
    dependsOn(":mcp-chart-server:run")
}
