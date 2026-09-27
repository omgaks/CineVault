package com.sole.cinevault.network

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

internal fun parseContentDirectoryService(deviceXml: String, location: String): DlnaService? {
    val parser = parser(deviceXml)
    var serviceType: String? = null
    var controlUrl: String? = null
    var inService = false

    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                "service" -> {
                    inService = true
                    serviceType = null
                    controlUrl = null
                }
                "serviceType" -> if (inService) serviceType = parser.nextText().trim()
                "controlURL" -> if (inService) controlUrl = parser.nextText().trim()
            }
            XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "service") {
                if (serviceType?.contains(":ContentDirectory:", ignoreCase = true) == true &&
                    !controlUrl.isNullOrBlank()
                ) {
                    return DlnaService(
                        serviceType = serviceType!!,
                        controlUrl = resolveDlnaUrl(location, controlUrl!!),
                    )
                }
                inService = false
            }
        }
        parser.next()
    }
    return null
}

internal fun parseDidlItems(didl: String): List<DlnaMediaItem> {
    val parser = parser(didl)
    val items = mutableListOf<DlnaMediaItem>()

    var id: String? = null
    var parentId: String? = null
    var title: String? = null
    var resource: String? = null
    var protocolInfo: String? = null
    var size = 0L
    var duration: String? = null
    var art: String? = null
    var inItem = false

    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                "item" -> {
                    inItem = true
                    id = parser.getAttributeValue(null, "id")
                    parentId = parser.getAttributeValue(null, "parentID")
                    title = null; resource = null; protocolInfo = null
                    size = 0L; duration = null; art = null
                }
                "title" -> if (inItem) title = parser.nextText().trim()
                "albumArtURI" -> if (inItem) art = parser.nextText().trim()
                "res" -> if (inItem && resource == null) {
                    protocolInfo = parser.getAttributeValue(null, "protocolInfo")
                    size = parser.getAttributeValue(null, "size")?.toLongOrNull() ?: 0L
                    duration = parser.getAttributeValue(null, "duration")
                    resource = parser.nextText().trim()
                }
            }
            XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "item" && inItem) {
                val mime = protocolInfo?.split(':')?.getOrNull(2)?.takeIf(String::isNotBlank)
                if (!id.isNullOrBlank() && !title.isNullOrBlank() &&
                    !resource.isNullOrBlank() && isSafeHttpUrl(resource!!) && isDlnaMediaMime(mime)
                ) {
                    items += DlnaMediaItem(
                        id = id!!,
                        parentId = parentId,
                        title = title!!,
                        resourceUrl = resource!!,
                        mimeType = mime,
                        size = size,
                        duration = duration,
                        albumArtUrl = art?.takeIf(::isSafeHttpUrl),
                    )
                }
                inItem = false
            }
        }
        parser.next()
    }
    return items
}

private fun parser(xml: String): XmlPullParser =
    XmlPullParserFactory.newInstance().newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(xml))
    }
