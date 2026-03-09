// Root build.gradle.kts - PrivatAid

plugins {
    alias(libs.plugins.android.application) apply true
    alias(libs.plugins.kotlin.android) apply true
    alias(libs.plugins.kotlin.compose) apply true
}

android {
    // Keep namespace the same to avoid breaking package references
    namespace = "com.pocketnarc.privacyshield"
    compileSdk = 35

    defaultConfig {
        // Change applicationId for full system-level rebranding
        applicationId = "com.privataid.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.accompanist.permissions)
    
    // Play Integrity
    implementation(libs.play.integrity)
    
    // FFT for audio spectrum analysis
    implementation(libs.jtransforms)
    
    // Image Loading
    implementation(libs.coil.compose)

    // CameraX
    implementation(libs.bundles.camerax)

    implementation(libs.bundles.compose.ui)
    debugImplementation(libs.bundles.compose.debug)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
