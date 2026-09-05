import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "io.jyri.dictator"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.jyri.dictator"
        minSdk = 34
        targetSdk = 35
        versionCode = 7
        versionName = "0.5.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.register("prepareKotlinBuildScriptModel") {
    // Dummy task to satisfy old IDE versions during sync with AGP 9.0+
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
