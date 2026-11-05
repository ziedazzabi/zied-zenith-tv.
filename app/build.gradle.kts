// Custom edition: servers come from custom/preset.json (users only type username + password)
val presetFile = rootProject.file("custom/preset.json")
@Suppress("UNCHECKED_CAST")
val preset: Map<String, Any?> = if (presetFile.exists()) groovy.json.JsonSlurper().parse(presetFile) as Map<String, Any?> else emptyMap()
val customAppName: String = (preset["appName"] as? String)?.takeIf { it.isNotBlank() } ?: "Z-Vision"
// "Z-Vision Max" -> "Max" (badge next to the logo)
val customSuffix: String = customAppName.replace(Regex("^Z-?\\s?Vision\\s*", RegexOption.IGNORE_CASE), "").trim()
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.zenith.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zenith.iptv"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        resValue("string", "app_name", "Z-Vision")
        resValue("string", "brand_suffix", "")
        vectorDrawables { useSupportLibrary = true }
        // TV boxes and phones are ARM
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        // A fixed debug-style key committed with the project so every build can update the previous one.
        create("shared") {
            storeFile = file("zenith-release.jks")
            storePassword = "zenithtv"
            keyAlias = "zenith"
            keyPassword = "zenithtv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        create("standard") { dimension = "edition" }
        create("custom") {
            dimension = "edition"
            applicationIdSuffix = ".custom"
            resValue("string", "app_name", customAppName)
            resValue("string", "brand_suffix", customSuffix)
        }
    }
    sourceSets {
        getByName("custom") {
            assets.srcDirs(rootProject.file("custom"))
            // its own logo, launcher icon and TV banner
            res.srcDirs(rootProject.file("branding/android-res"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // compress native libraries (VLC) so the APK stays small to download
        jniLibs { useLegacyPackaging = true }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    // VLC: software decoding fallback for channels the hardware can't decode (HEVC, MPEG-2, 10-bit…)
    implementation("org.videolan.android:libvlc-all:3.6.0")
    // FFmpeg audio decoders for ExoPlayer (AC-3, E-AC-3, DTS, MP2… on boxes without them)
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    // QR codes (crash report fallback)
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
}
