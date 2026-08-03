package com.sole.cinevault.subtitles
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

fun launchSubtitleCustomTab(context: Context, query: String) {
    val initialHeight = (context.resources.displayMetrics.heightPixels * 0.82f).toInt()
    val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF161622.toInt()).build()
    // FIX: real crash confirmed via the in-app crash log —
    // setToolbarCornerRadiusDp(20) threw IllegalArgumentException on-
    // device. The stack trace includes com.xiaomi.mirror.MiuiMirrorImpl,
    // suggesting this device's MIUI browser implementation validates the
    // corner-radius bound more strictly (or differently) than stock
    // Android — no documented valid range could be confirmed either way.
    // This was purely a cosmetic touch; removed entirely rather than
    // guessing at another number that might also fail on this device.
    CustomTabsIntent.Builder().setShowTitle(true).setDefaultColorSchemeParams(colors).setInitialActivityHeightPx(initialHeight, CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build().launchUrl(context, SubtitleWebPolicy.searchUri(query))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleFallbackSheet(searchQuery: String, statusText: String, onSecureBrowser: () -> Unit, onEmbeddedBrowser: () -> Unit, onImportFile: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF161622)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Need another subtitle?", style = MaterialTheme.typography.titleLarge, color = Color.White)
            if (statusText.isNotBlank()) {
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8B8C8))
            }
            Text(searchQuery, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFD166), maxLines = 2)
            Button(onClick = onSecureBrowser, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.OpenInBrowser, null)
                Spacer(Modifier.size(8.dp))
                Text("Search website securely")
            }
            Text("Recommended · Opens a protected browser panel with your search prepared.", color = Color(0xFF9D9DAC), style = MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = onImportFile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.size(8.dp))
                Text("Import downloaded subtitle")
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            OutlinedButton(onClick = onEmbeddedBrowser, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Language, null)
                Spacer(Modifier.size(8.dp))
                Text("Embedded browser — experimental")
            }
            Text("May stop working if the website changes its login or download process.", color = Color(0xFF9D9DAC), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedSubtitleBrowser(query: String, preferredLanguage: String, onImported: (SubtitleImportResult.Success) -> Unit, onMessage: (String) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var minimized by remember {
        mutableStateOf(false)
    }
    var busy by remember {
        mutableStateOf(false)
    }
    var currentPage by remember {
        mutableStateOf(SubtitleWebPolicy.searchUri(query).toString())
    }
    var webView: WebView? by remember {
        mutableStateOf(null)
    }

    BackHandler {
        when {
            webView?.canGoBack() == true -> webView?.goBack()
            else -> onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                setDownloadListener(null)
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(12.dp).widthIn(max = 720.dp).fillMaxWidth().heightIn(min = 58.dp, max = 620.dp).background(Color(0xFF161622), RoundedCornerShape(20.dp))) {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, null, tint = Color(0xFFFFD166))
                Text("  OpenSubtitles · English preferred", color = Color.White, modifier = Modifier.weight(1f))
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                IconButton(onClick = {
                    minimized = !minimized
                }) {
                    Icon(if (minimized) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess, "Minimise", tint = Color.White)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                }
            }

            AndroidView(modifier = Modifier.fillMaxWidth().height(if (minimized) 1.dp else 520.dp).alpha(if (minimized) 0f else 1f), factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.setSupportMultipleWindows(false)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !SubtitleWebPolicy.isAllowed(request.url)

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            currentPage = url
                        }
                    }
                    setDownloadListener(DownloadListener { url, userAgent, disposition, _, _ ->
                        if (url.startsWith("blob:", ignoreCase = true)) {
                            onMessage("This website download needs the secure-browser import option.")
                            return@DownloadListener
                        }
                        if (busy) return@DownloadListener
                        busy = true
                        scope.launch {
                            when(val result = SubtitleWebDownloadClient.downloadAndImport(context = context, initialUrl = url, userAgent = userAgent, contentDisposition = disposition, pageUrl = currentPage, releaseHint = query, preferredLanguage = preferredLanguage)) {
                                is WebSubtitleDownloadResult.Imported -> when(val imported = result.result) {
                                    is SubtitleImportResult.Success -> onImported(imported)
                                    is SubtitleImportResult.Failure -> onMessage(imported.userMessage)
                                }
                                is WebSubtitleDownloadResult.Failed -> onMessage(result.message)
                            }
                            busy = false
                        }
                    })
                    loadUrl(SubtitleWebPolicy.searchUri(query).toString())
                }
            }, update = {
                webView = it
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleCandidateSheet(primary: ImportedSubtitle, alternatives: List<ImportedSubtitle>, onSelected: (ImportedSubtitle) -> Unit, onDismiss: () -> Unit) {
    val candidates = remember(primary, alternatives) {
        listOf(primary) + alternatives
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF161622)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose subtitle", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("Best English/release match is shown first.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9D9DAC))
            candidates.forEachIndexed { index, candidate ->
                Row(Modifier.fillMaxWidth().background(Color(0xFF242434), RoundedCornerShape(12.dp)).clickable {
                    onSelected(candidate)
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(candidate.displayName, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(buildString {
                            append(candidate.format.label)
                            candidate.language?.let {
                                append(" · ${it.uppercase()}")
                            }
                            if (candidate.hearingImpaired) append(" · SDH")
                        }, color = Color(0xFF9D9DAC), style = MaterialTheme.typography.labelSmall)
                    }
                    if (index == 0) {
                        Text("BEST", color = Color(0xFFFFD166), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
