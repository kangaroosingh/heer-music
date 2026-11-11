package io.github.aedev.flow.data.music.smart

import io.github.aedev.flow.data.music.model.MusicTrack

object SmartDownloadPolicy {
    const val BATCH_SIZE = 5
    const val MIN_FREE_BYTES = 1_073_741_824L
    const val RESERVE_PER_SONG = 67_108_864L
    const val CHECK_INTERVAL_MS = 21_600_000L
    const val READDITION_DELAY_MS = 604_800_000L
    val songLimits = listOf(10, 20, 50)
    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    fun select(
        ranked: List<MusicTrack>,
        settings: SmartDownloadSettings,
        existingIds: Set<String>,
        automaticCount: Int,
        pendingCount: Int,
        freeBytes: Long,
        dislikedIds: Set<String>,
        earlySkips: Map<String, Int>,
        now: Long,
        reservedBytes: Long = 0,
        bitsPerSecond: Int = 160_000,
    ): List<MusicTrack> {
        if (freeBytes <= MIN_FREE_BYTES) return emptyList()
        val batch = if (settings.maxStorageBytes == null) BATCH_SIZE else SmartDownloadStorage.STORAGE_BATCH_SIZE
        val capacity =
            if (settings.maxStorageBytes ==
                null
            ) {
                (settings.maxSongs.coerceIn(10, 50) - automaticCount.coerceAtLeast(0)).coerceAtLeast(0)
            } else {
                batch
            }
        val space =
            (
                (freeBytes - MIN_FREE_BYTES) / RESERVE_PER_SONG -
                    pendingCount.coerceAtLeast(
                        0,
                    )
            ).coerceIn(0L, batch.toLong()).toInt()
        if (!settings.enabled || !settings.networkConfigured || capacity == 0 || space == 0) return emptyList()
        var remaining = (settings.maxStorageBytes ?: Long.MAX_VALUE) - reservedBytes
        return ranked
            .asSequence()
            .distinctBy { it.videoId }
            .filter { track ->
                videoId.matches(track.videoId) && track.duration in 1..600 &&
                    track.videoId !in existingIds && track.videoId !in dislikedIds &&
                    (earlySkips[track.videoId] ?: 0) < 2 &&
                    settings.recentAdditions[track.videoId]?.let { now - it < READDITION_DELAY_MS } != true
            }.filter { track ->
                if (settings.maxStorageBytes == null) {
                    true
                } else {
                    val bytes = SmartDownloadStorage.estimateBytes(track.duration, bitsPerSecond)
                    if (bytes > remaining) {
                        false
                    } else {
                        remaining -= bytes
                        true
                    }
                }
            }.take(minOf(capacity, space, batch))
            .toList()
    }
}
