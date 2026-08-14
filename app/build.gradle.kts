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
        // real test devices (Xiaomi Pad 7, Vivo X300 Pro).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // Signing config — reads from environment variables set by GitHub Actions,
    // falling back gracefully when building locally without a keystore.
    signingConfigs {
        create("release") {
            val ksFile = rootProject.file("cinevault-release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: localProperties.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: localProperties.getProperty("KEY_ALIAS", "")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: localProperties.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
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

    // The VAD model must remain uncompressed because ONNX Runtime accesses it
    // directly through memory mapping.
    androidResources {
        noCompress += "onnx"
    }
}

dependencies {
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    // Keep all Media3 components on the same version for binary compatibility
    // with Jellyfin's FFmpeg decoder.
    val media3Version = "1.9.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.browser:browser:1.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // GPL-3.0-compatible FFmpeg audio decoder.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    testImplementation("junit:junit:4.13.2")
}

// Test Foundation Batch 1: create HTML and XML coverage reports whenever the
// debug unit-test suite runs. Coverage is reported but is not yet enforced as
// a percentage gate.
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// AGP registers variant test tasks lazily. This live task collection discovers
// the debug tasks when AGP creates them instead of looking them up too early.
val debugUnitTestTasks = tasks.withType<Test>().matching {
    name.contains("debug", ignoreCase = true)
}

val jacocoTestReport by tasks.registering(JacocoReport::class) {
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

    sourceDirectories.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
        ),
    )

    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/" +
                    "testDebugUnitTest.exec"
            )
        },
    )
}

debugUnitTestTasks.configureEach {
    finalizedBy(jacocoTestReport)
}
