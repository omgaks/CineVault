import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.sole.cinevault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sole.cinevault"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        buildConfigField(
            "String",
            "TMDB_TOKEN",
            "\"${localProperties.getProperty("TMDB_TOKEN", "")}\""
        )

        buildConfigField(
            "String",
            "OMDB_API_KEY",
            "\"${localProperties.getProperty("OMDB_API_KEY", "")}\""
        )

        buildConfigField(
            "String",
            "OPENSUB_API_KEY",
            "\"${localProperties.getProperty("OPENSUB_API_KEY", "")}\""
        )
    }

    // Signing config — reads from environment variables set by GitHub Actions,
    // falls back gracefully when building locally without a keystore
    signingConfigs {
        create("release") {
            val ksFile = rootProject.file("cinevault-release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("KEY_ALIAS", "")
                keyPassword = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ksFile = rootProject.file("cinevault-release.jks")
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Icons were removed from newer Compose BOMs — must be pinned explicitly
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // Media3 — 4K / HEVC / AV1 / HDR tone-mapping era
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // SMB network share scanning (NAS/PC shares) — pure Java, no NDK/native
    // build step needed, unlike the FFmpeg audio codec work planned later.
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    // Palette-based dynamic theming on the Detail screen — extracts a
    // dominant color from each title's poster/backdrop artwork. Small,
    // stable, official AndroidX artifact (not a third-party dependency).
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Encrypted storage for SMB credentials (Android Keystore-backed).
    // NOTE: 1.1.0-alpha06 is deliberate, not a mistake — AndroidX Security
    // Crypto has never shipped a stable 1.1 release, and this alpha is the
    // de facto production-standard version (1.0.0 has known Keystore bugs
    // on some devices that this release fixed).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Restricted-folder scanning walks a SAF-picked folder tree via
    // DocumentFile — not transitively included by anything else here.
    implementation("androidx.documentfile:documentfile:1.0.1")

    // FFmpeg audio decoder for Media3 — broad audio codec coverage
    // (DTS/DTS-HD, TrueHD, AC3/E-AC3, FLAC multichannel, etc.) that the
    // device's own built-in hardware/OS decoders often don't support,
    // which is why some files play with no audio in CineVault but work
    // fine in players like MX Player that bundle their own decoders.
    // Prebuilt by the Jellyfin project — no native/NDK build step needed
    // here, unlike building FFmpeg from source ourselves would require.
    // LICENSE NOTE: this artifact is GPL-3.0. A deliberate call — flagged
    // and accepted for this personal build; NOT license-compatible with
    // distributing the app under the existing MIT LICENSE without
    // addressing that conflict first.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    testImplementation("junit:junit:4.13.2")
}
