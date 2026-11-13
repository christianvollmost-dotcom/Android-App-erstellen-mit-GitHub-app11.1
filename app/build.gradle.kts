plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun quotedBuildConfig(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.example.tradejournal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tradejournal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "BACKEND_BASE_URL", quotedBuildConfig(providers.gradleProperty("backendUrl").orElse("").get()))
        buildConfigField("String", "BACKEND_PUBLIC_KEY_PEM", quotedBuildConfig(providers.gradleProperty("backendPublicKey").orElse("").get()))
        buildConfigField("String", "BACKEND_KEY_ID", quotedBuildConfig(providers.gradleProperty("backendKeyId").orElse("").get()))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
