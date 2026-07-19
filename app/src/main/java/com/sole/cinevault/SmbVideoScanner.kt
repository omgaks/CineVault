package com.sole.cinevault

import android.util.Log
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

private val SMB_VIDEO_EXTENSIONS = setOf(
    "mkv", "mp4", "avi", "mov", "m4v", "ts", "webm", "wmv", "flv", "m2ts", "3gp"
)

// No MediaStore duration is available for network files, so file size is the
// only practical filter for "this is a real movie, not a thumbnail/sample
// clip" — same 50MB floor the device scanner uses for its own DURATION+SIZE check.
private const val MIN_SMB_VIDEO_SIZE_BYTES = 50L * 1024 * 1024

// Guards against pathological folder layouts (very deep nesting, or the
// symlink-loop structures some NAS software creates) turning into a
// runaway recursive scan.
private const val MAX_SMB_SCAN_DEPTH = 6

sealed class SmbScanResult {
    data class Success(val videos: List<VideoWithMetadata>) : SmbScanResult()
    data class Failure(val share: SmbShare, val reason: String) : SmbScanResult()
}

// No longer private — reused by SmbDataSource.kt during playback so the
// exact same dialect range / credential-building logic used to SCAN a share
// is also used to PLAY a file from it, instead of duplicating (and risking
// drifting out of sync with) this logic in two places.
fun buildCifsContext(share: SmbShare): CIFSContext {
    val props = Properties().apply {
        // jcifs-ng negotiates the best dialect within this range
        // automatically — this just rules out ancient SMB1 rather than
        // forcing a single specific version.
        setProperty("jcifs.smb.client.minVersion", "SMB202")
        setProperty("jcifs.smb.client.maxVersion", "SMB311")
    }
    val baseContext: CIFSContext = BaseContext(PropertyConfiguration(props))
    return if (share.username.isBlank()) {
        baseContext.withAnonymousCredentials()
    } else {
        baseContext.withCredentials(NtlmPasswordAuthenticator(share.domain, share.username, share.password))
    }
}

suspend fun scanSmbShare(share: SmbShare): SmbScanResult = withContext(Dispatchers.IO) {
    try {
        val context = buildCifsContext(share)
        val rootUrl = share.rootUrl()
        val root = SmbFile(rootUrl, context)

        if (!root.exists()) {
            return@withContext SmbScanResult.Failure(
                share,
                "Could not reach \"${share.displayName}\" — check the host, share name, and that the device is on the same network."
            )
        }

        val results = mutableListOf<VideoWithMetadata>()
        walkSmbDirectory(rootUrl, context, depth = 0, out = results)
        SmbScanResult.Success(results)

    } catch (e: Exception) {
        Log.e("CineVault", "SMB scan failed for ${share.displayName}: ${e.message}", e)
        val reason = when {
            e.message?.contains("Access is denied", ignoreCase = true) == true ->
                "Access denied — check the username and password for \"${share.displayName}\"."
            e.message?.contains("logon failure", ignoreCase = true) == true ->
                "Login failed — check the username and password for \"${share.displayName}\"."
            e.message?.contains("UnknownHost", ignoreCase = true) == true ->
                "Couldn't find \"${share.host}\" on the network — check the address for \"${share.displayName}\"."
            else -> "Couldn't connect to \"${share.displayName}\": ${e.message ?: e.javaClass.simpleName}"
        }
        SmbScanResult.Failure(share, reason)
    }
}

// Tracks the URL string manually as it recurses rather than reading it back
// off each SmbFile — sidesteps a Kotlin/Java interop ambiguity around
// jcifs-ng's getURL()/getPath() getters (all-caps "URL" doesn't map to a
// Kotlin property the same predictable way getName() does), which isn't
// worth risking a build failure over when plain string concatenation does
// the same job just as reliably.
private fun walkSmbDirectory(dirUrl: String, context: CIFSContext, depth: Int, out: MutableList<VideoWithMetadata>) {
    if (depth > MAX_SMB_SCAN_DEPTH) return

    val dir = SmbFile(dirUrl, context)
    val children = try {
        dir.listFiles()
    } catch (e: Exception) {
        // One inaccessible/flaky subfolder shouldn't abort the whole scan.
        Log.w("CineVault", "SMB: couldn't list $dirUrl: ${e.message}")
        return
    } ?: return

    for (child in children) {
        try {
            val childName = child.name.trimEnd('/')
            val childUrl = dirUrl.trimEnd('/') + "/" + childName

            when {
                child.isDirectory -> walkSmbDirectory(childUrl, context, depth + 1, out)
                child.isFile -> {
                    val ext = childName.substringAfterLast('.', "").lowercase()
                    if (ext !in SMB_VIDEO_EXTENSIONS) continue
                    if (isPersonalVideo(childName)) continue

                    val size = try { child.length() } catch (_: Exception) { 0L }
                    if (size < MIN_SMB_VIDEO_SIZE_BYTES) continue

                    val videoFile = VideoFile(path = childUrl, name = childName)
                    out.add(
                        VideoWithMetadata(
                            video = videoFile,
                            title = cleanScannedTitle(childName),
                            subtitle = "",
                            posterUrl = null,
                            backdropUrl = null,
                            overview = null,
                            rating = null,
                            type = guessMediaType(childName)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("CineVault", "SMB: skipped an entry in $dirUrl: ${e.message}")
        }
    }
}
