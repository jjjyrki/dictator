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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 50
        versionName = "0.9.35"
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
    implementation("com.google.android.material:material:1.13.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
