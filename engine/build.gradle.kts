import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Directorios de la build nativa (la librería queda en build/cmake/lib).
val nativeSrcDir = layout.projectDirectory.dir("src/main/cpp")
val nativeBuildDir = layout.buildDirectory.dir("cmake")
val nativeLibDir = layout.buildDirectory.dir("cmake/lib")

// El JDK se usa para las cabeceras jni.h. Requiere cmake + g++/clang.
val jdkHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
?: "/usr/lib/jvm/java-17-openjdk-amd64"
val nativeLib = System.mapLibraryName("dibujo_engine")

val configureNative by tasks.registering(Exec::class) {
    group = "build"
    description = "Configura el motor nativo de dibujo con CMake."
    inputs.dir(nativeSrcDir)
    outputs.file(nativeBuildDir.map { it.file("CMakeCache.txt") })
    commandLine(
        "cmake",
        "-S", nativeSrcDir.asFile.absolutePath,
        "-B", nativeBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${nativeLibDir.get().asFile.absolutePath}",
        "-DJDK_HOME=$jdkHome",
    )
}

val buildNative by tasks.registering(Exec::class) {
    group = "build"
    description = "Compila la librería compartida del motor (C++)."
    dependsOn(configureNative)
    inputs.dir(nativeSrcDir)
    outputs.file(nativeLibDir.map { it.file(nativeLib) })
    commandLine("cmake", "--build", nativeBuildDir.get().asFile.absolutePath, "--parallel")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(buildNative)
}
