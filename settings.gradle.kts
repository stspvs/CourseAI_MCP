pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "currency-watch-mcp"

include(":shared")
include(":mcp-server")
include(":mcp-chart-server")
