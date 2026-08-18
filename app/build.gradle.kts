import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    jacoco
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
            "FANART_API_KEY",
            "\"${localProperties.getProperty("FANART_API_KEY", "")}\""
        )

        buildConfigField(
            "String",
            "OPENSUB_API_KEY",
            "\"${localProperties.getProperty("OPENSUB_API_KEY", "")}\""
        )

        buildConfigField(
            "String",
            "SUBDL_API_KEY",
            "\"${localProperties.getProperty("SUBDL_API_KEY", "")}\""
        )

        // sherpa-onnx (VAD, for Auto-Sync Phase 1) only ships jniLibs for
        // arm64-v8a in this project — that's the only ABI actually
        // committed under app/src/main/jniLibs/, matching both of Ash's
        // real test devices (Xiaomi Pad 7, Vivo X300 Pro). Restricting the
        // ABI filter here keeps the APK from claiming support for
        // architectures it has no native libs for, and keeps the build
        // from needing armeabi-v7a/x86/x86_64 .so files we never committed.
        ndk {
            abiFilters += "arm64-v8a"
        }
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
            // FIX: was false — proguard-rules.pro was the untouched
            // default template with nothing kept, which was fine ONLY
            // because minification was off. Now genuinely populated with
            // rules covering every reflection/JNI-dependent library this
            // app actually uses (see that file for the full reasoning
            // behind each one) before turning this on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // sherpa-onnx's VAD model (assets/silero_vad.onnx) is mmap'd directly
    // by ONNX Runtime at load time, which requires the file to be stored
    // UNCOMPRESSED inside the APK — Android compresses assets/ by default,
    // and reading a compressed file via mmap corrupts the byte alignment
    // ONNX Runtime expects, causing a native SIGBUS crash on some devices
    // (reported specifically on some MediaTek chips, but not guaranteed
    // safe on any device). This is a real, documented failure mode for
    // this exact library, not a hypothetical — skipping this would likely
    // not even fail the build, just crash at runtime the first time Auto-
    // Sync tries to load the model.
    androidResources {
        noCompress += "onnx"
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

    // Keep the complete Media3 runtime on 1.9.0. Jellyfin's
    // media3-ffmpeg-decoder:1.9.0+1 is compiled against Media3 1.9.0;
    // mixing it with 1.10.x can produce binary-incompatible decoder calls at
    // runtime even when Gradle resolves the project successfully.
    val media3Version = "1.9.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Media3 session — MediaSession/MediaSessionService for
    // CineVaultPlaybackService.kt (lock-screen playback survival, media
    // notification, system media controls). Pinned to the SAME version as
    // the other media3-* artifacts above — mixing Media3 artifact versions
    // is a common source of runtime crashes, so this must always be bumped
    // together with media3-exoplayer/media3-ui, never independently.
    implementation("androidx.media3:media3-session:$media3Version")

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

    // Encrypted storage for SMB credentials AND (as of this fix) Secret
    // Folder's hidden path lists (Android Keystore-backed). NOTE:
    // 1.1.0-alpha06 is deliberate, not a mistake — AndroidX Security
    // Crypto has never shipped a stable 1.1 release, and this alpha is the
    // de facto production-standard version (1.0.0 has known Keystore bugs
    // on some devices that this release fixed).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // BiometricPrompt for Secret Folder's unlock — replaces the deprecated
    // (since API 30) KeyguardManager.createConfirmDeviceCredentialIntent.
    // 1.1.0 is the current STABLE release (verified — the -ktx and
    // -compose variants are still alpha-only as of this writing, so the
    // plain Java-interop artifact is the correct choice here, same as
    // it's fine to call from Kotlin).
    implementation("androidx.biometric:biometric:1.1.0")

    // FragmentActivity (MainActivity's new base class, needed for
    // BiometricPrompt above) lives in this artifact. Would likely also
    // arrive transitively via androidx.biometric itself, but declared
    // explicitly here rather than leave that to chance — 1.8.9 is current
    // stable (verified).
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // Room — Phase 1 of migrating SharedPreferences-as-database usage to
    // a real database (see CachedVideoMetadataDatabase.kt), starting with
    // cinevault_metadata_cache specifically: one SharedPreferences KEY
    // per video, unbounded growth as the library grows, the one store of
    // several with a genuine ANR/TransactionTooLargeException risk rather
    // than just being an awkward fit. Room 2.8.4 — current stable 2.x
    // (verified). Room 3.0 exists but is a brand-new major rewrite still
    // in early alpha as of this writing, under different package
    // coordinates (androidx.room3) — not appropriate to build on yet.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Custom Tabs — used by the subtitle website fallback (SubtitleWebFallback.kt)
    // to open OpenSubtitles as the recommended/default route: a real browser
    // tab sharing the user's own login/cookie state, not a WebView CineVault
    // has to maintain and secure itself. Partial-height presentation
    // (setInitialActivityHeightPx) requires 1.6.0+; 1.10.0 is current stable.
    implementation("androidx.browser:browser:1.10.0")

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
    // LICENSE NOTE: this artifact is GPL-3.0. CineVault itself is licensed
    // GPL-3.0-only (see LICENSE) specifically so this dependency and the
    // rest of the app are license-compatible — no conflict to flag.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    testImplementation("junit:junit:4.13.2")
}

// Test Foundation Batch 1: produce an HTML report for people and an XML
// report for CI/auditing every time the debug unit-test suite runs. Coverage
// is reported, but deliberately not used as a percentage gate yet: the first
// goal is a trustworthy baseline that can grow alongside each future slice.
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val debugUnitTestTasks = tasks.withType<Test>().matching {
    name.contains("debug", ignoreCase = true)
}

val jacocoTestReport by tasks.registering(JacocoReport::class) {
    // AGP creates variant test tasks after this script is evaluated. Using
    // the live task collection keeps this compatible with that lazy task
    // registration instead of looking up testDebugUnitTest too early.
    dependsOn(debugUnitTestTasks)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val coverageExclusions = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/*_Factory.*",
        "**/*_Impl.*",
        "**/*Dao_Impl.*",
        "**/*Database_Impl.*",
    )

    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
                exclude(coverageExclusions)
            },
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) {
                exclude(coverageExclusions)
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
}

debugUnitTestTasks.configureEach {
    finalizedBy(jacocoTestReport)
}
