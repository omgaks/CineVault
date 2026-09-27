package com.sole.cinevault.network

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class DlnaNetworkSource(
    private val device: DlnaDevice,
    override val displayName: String,
    private val client: OkHttpClient = createNetworkHttpClient(),
) : NetworkSource {
    override val type: NetworkType = NetworkType.DLNA
    override val id: String = stableNetworkSourceId(type, device.location)

    override fun createDataSourceFactory(): DataSource.Factory =
        DefaultHttpDataSource.Factory()

    override suspend fun testConnection(): NetworkConnectionResult = withContext(Dispatchers.IO) {
        try {
            val service = loadService()
            if (service != null) NetworkConnectionResult.Connected
            else safeNetworkFailure(displayName)
        } catch (_: Exception) {
            safeNetworkFailure(displayName)
        }
    }

    override suspend fun scan(): List<NetworkVideo> = withContext(Dispatchers.IO) {
        try {
            val service = loadService() ?: return@withContext emptyList()
            browse(service, "0")
                .map {
                    NetworkVideo(
                        path = it.resourceUrl,
                        name = it.title,
                        size = it.size,
                        posterUrl = it.albumArtUrl,
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadService(): DlnaService? {
        val request = Request.Builder().url(device.location).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            parseContentDirectoryService(response.body?.string().orEmpty(), device.location)
        }
    }

    private fun browse(service: DlnaService, objectId: String): List<DlnaMediaItem> {
        val soap = browseEnvelope(objectId)
        val request = Request.Builder()
            .url(service.controlUrl)
            .header("SOAPAction", "\"${service.serviceType}#Browse\"")
            .post(soap.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val result = extractBrowseResult(response.body?.string().orEmpty()) ?: return emptyList()
            parseDidlItems(result)
        }
    }
}

internal fun browseEnvelope(objectId: String): String =
    """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:Browse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
      <ObjectID>${xmlEscape(objectId)}</ObjectID>
      <BrowseFlag>BrowseDirectChildren</BrowseFlag>
      <Filter>*</Filter>
      <StartingIndex>0</StartingIndex>
      <RequestedCount>0</RequestedCount>
      <SortCriteria></SortCriteria>
    </u:Browse>
  </s:Body>
</s:Envelope>"""

internal fun extractBrowseResult(soap: String): String? {
    val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(soap))
    }
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG &&
            parser.name.substringAfter(':') == "Result"
        ) return parser.nextText()
        parser.next()
    }
    return null
}

private fun xmlEscape(value: String): String =
    value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
