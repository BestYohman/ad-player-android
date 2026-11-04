plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
// the version people see (VERSION at the top of the project; what changed is in CHANGELOG.md)
val appVersion = rootProject.file("VERSION").readText().trim()

android {
    namespace = "com.bestyohman.adplayer"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.bestyohman.adplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = build
        versionName = appVersion
    }
    signingConfigs {
        // A fixed key, so each new build installs over the last one (settings and sign-in are kept).
        create("release") {
            storeFile = file("adplayer.keystore")
            storePassword = "adplayer"
            keyAlias = "adplayer"
            keyPassword = "adplayer"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug { signingConfig = signingConfigs.getByName("release") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
