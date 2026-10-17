pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "dibujo"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":core", ":engine", ":ui", ":app")
