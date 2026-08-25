package com.sole.cinevault

internal fun shouldLoadSmartSegments(
    metadata: VideoWithMetadata,
    duration: Long,
): Boolean {
    return duration > 60_000L && metadata.type != "secret"
}
