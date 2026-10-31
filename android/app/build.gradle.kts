plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ntvslide"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ntvslide"
        minSdk = 31          // Nothing OS 1.x ships Android 12
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Shizuku lets ntvslide run one small helper process with shell privileges (no root needed).
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
