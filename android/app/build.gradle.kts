plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.Text.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.Text.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "1.0.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity:1.9.0")
    implementation("androidx.core:core:1.13.1")
}


