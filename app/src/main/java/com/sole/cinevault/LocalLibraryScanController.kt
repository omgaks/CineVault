package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sole.cinevault.library.*
import com.sole.cinevault.metadata.*
import com.sole.cinevault.smb.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// Runs the actual library scan on a scope that outlives any single
// composable — HomeScreen.kt's big "Scan Library" button calls start()
// and navigates away immediately, so the coroutine driving the scan
// can't live inside LocalVideoLibraryScreen's own rememberCoroutineScope()
// (that scope is cancelled the moment the screen isn't composed, which
// would kill the scan mid-flight the instant Library isn't the visible
// screen). isScanning/status/lastError are plain mutableStateOf so both
// Home and Library recompose live off the exact same in-progress scan,
// however it got started, instead of each screen keeping an independent
// local copy that goes stale the moment the other screen updates it.
object LibraryScanController {
    var isScanning by mutableStateOf(false)
    var status by mutableStateOf("")
    var lastError by mutableStateOf<ErrorBannerState?>(null)
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(context: Context, onVideosLoaded: (List<VideoWithMetadata>) -> Unit) {
        if (isScanning) return
        controllerScope.launch {
            isScanning = true; status = "Scanning device videos..."; lastError = null
            val deviceVideos = try { scanDeviceVideos(context) } catch (e: Exception) {
                e.printStackTrace()
                status = ""; isScanning = false
                lastError = ErrorBannerState("Scan failed: ${e.message ?: "Unknown error"}") { start(context, onVideosLoaded) }
                return@launch
            }

            val smbShares = loadSmbShares(context)
            val smbVideos = mutableListOf<VideoWithMetadata>()
            for (share in smbShares) {
                status = "Scanning network share: ${share.displayName}..."
                when (val result = scanSmbShare(share)) {
                    is SmbScanResult.Success -> smbVideos.addAll(result.videos)
                    is SmbScanResult.Failure -> lastError = ErrorBannerState(result.reason) { start(context, onVideosLoaded) }
                }
            }

            status = "Scanning restricted folders..."
            val restrictedVideos = try { scanAllRestrictedFolders(context) } catch (e: Exception) { emptyList() }

            val scannedVideos = deviceVideos + smbVideos + restrictedVideos
            status = "Found ${scannedVideos.size} videos. Loading cached posters..."
            val instantList = scannedVideos.map { applyCachedMetadataIfAvailable(context, it) }
            onVideosLoaded(instantList); saveLibraryCache(context, instantList)

            val toEnrich = instantList.withIndex().filter { (_, item) ->
                !isRestrictedFolderItem(item) && shouldEnrichOnlineMetadata(item)
            }
            status = "Loaded ${instantList.size} videos. Updating missing posters & ratings..."

            val workingList = instantList.toMutableList()
            var completedCount = 0
            val semaphore = Semaphore(6)
            coroutineScope {
                toEnrich.map { (index, item) ->
                    async {
                        semaphore.withPermit {
                            val enriched = try { enrichVideoWithOnlineMetadata(context, item) } catch (e: Exception) { item }
                            completedCount++
                            status = "Metadata $completedCount/${toEnrich.size}: ${item.video.name.take(28)}"
                            if (enriched != item) {
                                workingList[index] = enriched
                                if (completedCount % 8 == 0) { val p = workingList.toList(); onVideosLoaded(p); saveLibraryCache(context, p) }
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalList = workingList.toList(); onVideosLoaded(finalList); saveLibraryCache(context, finalList)
            status = "Library updated: ${finalList.size} videos"; delay(500); status = ""; isScanning = false
        }
    }
}
