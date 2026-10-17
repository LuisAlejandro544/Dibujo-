pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "dibujo"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core", ":engine", ":ui")
