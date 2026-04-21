import java.io.BufferedReader

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("com.squareup.sqldelight")
    id("com.google.gms.google-services") apply false
    id("com.google.firebase.appdistribution") apply false
}

val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.appdistribution")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    // Suport iOS
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.components.resources)
                implementation("com.squareup.sqldelight:runtime:1.5.5")
                implementation("com.squareup.sqldelight:coroutines-extensions:1.5.5")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.credentials:credentials:1.3.0")
                implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
                implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
                implementation("com.squareup.sqldelight:android-driver:1.5.5")
                implementation("com.google.firebase:firebase-auth:22.2.0")
                implementation("com.google.firebase:firebase-firestore:24.7.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")
            }
        }
    }
}

android {
    namespace = "com.glicocalc"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.glicocalc"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            if (hasGoogleServices) {
                apply(from = "firebase-config.gradle")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}



sqldelight {
    database("GlicoDatabase") {
        packageName = "com.glicocalc.database"
        sourceFolders = listOf("sqldelight")
    }
}
