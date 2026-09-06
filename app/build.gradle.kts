import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "io.jyri.dictator"
    // Keep the tested API 35 baseline until newer Android behavior is validated.
    //noinspection GradleDependency
    compileSdk = 35

    defaultConfig {
        applicationId = "io.jyri.dictator"
        minSdk = 34
        // Upgrade alongside compileSdk after compatibility testing.
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 48
        versionName = "0.9.33"
    }

    signingConfigs {
        create("release") {
            // Upgrade in place over the debug-signed development installs.
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
