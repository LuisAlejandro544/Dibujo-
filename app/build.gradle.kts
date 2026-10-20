import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // Cobertura: el eslogan de AGP 9 es Kotlin incorporado (built-in),
    // así que no se aplica org.jetbrains.kotlin.android.
    namespace = "com.dibujo.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dibujo.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.3.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            // El motor C++ de :engine se compila también para Android,
            // así el dispositivo usa MotorNativo (JNI) real.
            path = file("../engine/src/main/cpp/CMakeLists.txt")
            version = "3.31.5"
        }
    }
    ndkVersion = "27.3.13750724"

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // LiteRT: corre el modelo chico de limpieza de trazos en el equipo (CPU).
    implementation(libs.litert)

    // Panel de depuración + LeakCanary: SOLO en el variant debug. En release
    // no se incluye nada de esto (ni código, ni permiso, ni icono).
    debugImplementation(libs.leakcanary.android)
}
