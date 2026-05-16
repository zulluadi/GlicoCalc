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

fun Project.secretOrProperty(name: String): String? {
    return providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
}

val releaseStoreFilePath = project.secretOrProperty("ANDROID_RELEASE_STORE_FILE")
val releaseStorePassword = project.secretOrProperty("ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = project.secretOrProperty("ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = project.secretOrProperty("ANDROID_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    val iosX64Target = iosX64()
    val iosArm64Target = iosArm64()
    val iosSimulatorArm64Target = iosSimulatorArm64()
    val iosTargets = listOf(iosX64Target, iosArm64Target, iosSimulatorArm64Target)

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.glicocalc.composeapp")
        }
    }

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
                implementation("io.github.g0dkar:qrcode-kotlin:4.0.7")
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
                implementation("com.journeyapps:zxing-android-embedded:4.3.0")
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("com.squareup.sqldelight:native-driver:1.5.5")
            }
        }
        val iosX64Main by getting {
            dependsOn(iosMain)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
    }
}

android {
    namespace = "com.glicocalc"
    compileSdk = 34

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.glicocalc"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "0.4"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("Release keystore is not configured. Falling back to the debug signing key.")
                signingConfigs.getByName("debug")
            }
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
