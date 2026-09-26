package com.sole.cinevault

/**
 * JVM-safe decision seam for external subtitle MediaItem construction.
 */
internal fun shouldAttachExternalSubtitle(hasSubtitleUri: Boolean): Boolean = hasSubtitleUri
