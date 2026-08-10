// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // KSP — Room's annotation processor (see app/build.gradle.kts for the
    // actual Room dependencies). Added directly rather than through the
    // version catalog above, matching how every other dependency in this
    // project is declared (plain strings in app/build.gradle.kts) — that
    // catalog's own versions are already stale against what the app
    // module actually uses (e.g. coreKtx 1.10.1 here vs. 1.16.0 in
    // practice), so it isn't the actively-maintained source of truth here.
    // Version 2.2.10-2.0.2 matches this project's exact Kotlin version
    // (2.2.10) — KSP releases are tied to a specific Kotlin version, not
    // just "latest," so this isn't a value that can be picked
    // independently of the kotlin version above.
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
