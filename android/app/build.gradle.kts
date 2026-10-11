plugins {
    id("com.android.application")
}

android {
    namespace = "com.textzixiao.studio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.textzixiao.studio"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
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
