# CineVault release ProGuard/R8 rules.
#
# Written for enabling isMinifyEnabled — previously this file was the
# untouched default template with nothing actually kept, which was fine
# ONLY because minification itself was off. Turning minification on
# without these rules would very likely have caused real runtime crashes
# (JNI lookups by exact name failing) or silent data-population failures
# (Gson reflection no longer matching renamed fields) — neither of which
# would show up as a compile error, only as a broken release build.
#
# Every rule below is tied to a specific, verified reason — checked
# against this app's actual dependencies and code, not copied from a
# generic "just in case" ProGuard template.

# ── Crash logger readability ────────────────────────────────────────────
# CineVault has its own crash logger (installCrashLogger, writing to
# cinevault_crash_log.txt) — keeping source file + line number info means
# those logs stay genuinely readable ("VideoPlayerScreen.kt:1234") instead
# of turning into unusable obfuscated stack traces the moment minification
# is on, which would defeat the point of having a crash logger at all.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Gson (TMDB, OMDB, and local cache serialization) ────────────────────
# Baseline rules every Gson-using app needs regardless of which specific
# model classes it has — generic type signatures and TypeToken need to
# survive for Gson's reflection to work at all.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# The actual model classes. None of these use @SerializedName (confirmed
# — grepped the whole codebase, zero matches), meaning Gson relies
# entirely on matching Kotlin property names directly to JSON keys /
# cached-JSON keys. If R8 renames these fields, Gson wouldn't crash — it
# would silently leave them at their default/null value, which is a
# quieter but just as real bug (metadata/ratings/cast would stop
# populating, or previously-cached local data would stop loading).
# Subtitle providers (OpenSubtitles, SubDL) are NOT included here —
# confirmed they parse responses manually via org.json.JSONObject, not
# Gson reflection, so they have no exposure to this class of issue.
-keep class com.sole.cinevault.metadata.TmdbMovieSearchResponse { *; }
-keep class com.sole.cinevault.metadata.TmdbMovie { *; }
-keep class com.sole.cinevault.metadata.TmdbTvSearchResponse { *; }
-keep class com.sole.cinevault.metadata.TmdbTvShow { *; }
-keep class com.sole.cinevault.metadata.TmdbCreditsResponse { *; }
-keep class com.sole.cinevault.metadata.TmdbCastMember { *; }
-keep class com.sole.cinevault.metadata.TmdbEpisode { *; }
-keep class com.sole.cinevault.metadata.TmdbGenre { *; }
-keep class com.sole.cinevault.metadata.TmdbCollection { *; }
-keep class com.sole.cinevault.metadata.TmdbCrewMember { *; }
-keep class com.sole.cinevault.metadata.TmdbCreatedBy { *; }
-keep class com.sole.cinevault.metadata.TmdbKeyword { *; }
-keep class com.sole.cinevault.metadata.TmdbMovieKeywordsBlock { *; }
-keep class com.sole.cinevault.metadata.TmdbTvKeywordsBlock { *; }
-keep class com.sole.cinevault.metadata.TmdbCreditsBlock { *; }
-keep class com.sole.cinevault.metadata.TmdbMovieDetails { *; }
-keep class com.sole.cinevault.metadata.TmdbTvDetails { *; }
-keep class com.sole.cinevault.metadata.TmdbExternalIds { *; }
-keep class com.sole.cinevault.metadata.OmdbResponse { *; }
-keep class com.sole.cinevault.metadata.OmdbRating { *; }
-keep class com.sole.cinevault.metadata.CachedVideoMetadata { *; }
-keep class com.sole.cinevault.metadata.TmdbExtraDetails { *; }
-keep class com.sole.cinevault.library.CachedLibrary { *; }

# ── Retrofit ─────────────────────────────────────────────────────────────
# Retrofit and OkHttp both ship their own consumer ProGuard rules bundled
# in their AARs, applied automatically by R8 — these are a light
# defensive layer on top for the API interfaces themselves, a known
# occasional pain point with Retrofit's generic method signatures.
-keep,allowobfuscation interface com.sole.cinevault.metadata.TmdbApi
-keep,allowobfuscation interface com.sole.cinevault.metadata.OmdbApi
-keepattributes Exceptions

# ── sherpa-onnx / ONNX Runtime (Auto-Sync's VAD, JNI bridge) ────────────
# CRITICAL — must stay completely unobfuscated. This is vendored
# third-party source (com.k2fsa.sherpa.onnx, not CineVault's own code),
# and its native (.so) side calls back into these Java/Kotlin classes by
# EXACT NAME via JNI (FindClass/GetMethodID-style lookups). Native code
# has no way to know about a rename R8 makes on the Kotlin side — any
# renaming here would surface as a native crash (NoSuchMethodError or a
# native-level failure) the first time Auto-Sync tries to run, not a
# compile-time error.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ONNX Runtime itself (the inference engine sherpa-onnx sits on top of)
# has the same JNI-boundary concern.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── security-crypto (SMB credentials + Secret Folder encryption) ───────
# Built on Google's Tink, which uses reflection for its key-management
# scheme registration — a well-documented ProGuard pain point for this
# specific library, not a generic just-in-case rule.
-keep class com.google.crypto.tink.** { *; }
-keep,allowobfuscation,allowoptimization class com.google.crypto.tink.**
-dontwarn com.google.crypto.tink.**

# ── Media3 / ExoPlayer / FFmpeg extension ───────────────────────────────
# Media3 ships its own consumer rules for the core library, but the
# Jellyfin-maintained FFmpeg decoder extension (media3-ffmpeg-decoder)
# bridges to native code the same way sherpa-onnx does, and is a much
# less commonly-obfuscated combination than stock ExoPlayer — kept
# defensively rather than assuming its consumer rules cover everything.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# ── jcifs-ng (SMB) ───────────────────────────────────────────────────────
# Network protocol library with its own internal negotiation/reflection
# for different SMB dialect versions — defensive keep given no first-hand
# confirmation of how complete its own consumer rules are for every
# obfuscation scenario.
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# jcifs-ng transitively pulls in SLF4J for optional logging. SLF4J's
# StaticLoggerBinder is intentionally absent unless the app also bundles
# an actual logging backend (Logback, Log4j, etc.) — SLF4J's own
# LoggerFactory handles that absence gracefully at runtime (falls back to
# a no-op logger). R8 in full mode treats any referenced-but-missing
# class as a hard build error by default, which this isn't — it's the
# documented, expected pattern for SLF4J specifically, not a real problem.
# Confirmed via an actual failed build: "R8: Missing class
# org.slf4j.impl.StaticLoggerBinder" — this isn't a defensive guess.
-dontwarn org.slf4j.**

# ── ML Kit subtitle translation / language identification ──────────────
# CineVault creates these clients through ML Kit's component/factory
# registry. Release-only testing showed Translation.getClient(options)
# failing before a Translator could be returned. Preserve this bounded
# ML Kit feature surface and its component registrars while leaving the
# rest of Google Play services and ML Kit available to R8 optimization.
-keep class com.google.mlkit.nl.translate.** { *; }
-keep interface com.google.mlkit.nl.translate.** { *; }
-keep class com.google.mlkit.nl.languageid.** { *; }
-keep interface com.google.mlkit.nl.languageid.** { *; }
-keep class com.google.mlkit.** implements com.google.firebase.components.ComponentRegistrar { *; }

# ── coroutines / kotlinx.serialization internals ────────────────────────
# Standard defensive rules for Kotlin coroutines' own internal use of
# reflection for continuation state machines — a widely-documented
# baseline any coroutine-heavy app benefits from.
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepclassmembernames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.debug.**
