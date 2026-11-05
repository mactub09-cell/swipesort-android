plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.swipesort.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.swipesort.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // Bundled model: available offline, does not download images or model at runtime.
    implementation("com.google.mlkit:image-labeling:17.0.9")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
