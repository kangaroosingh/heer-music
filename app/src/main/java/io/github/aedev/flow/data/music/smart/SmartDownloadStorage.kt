package io.github.aedev.flow.data.music.smart

import io.github.aedev.flow.data.local.MusicAudioQuality
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import kotlin.math.max

object SmartDownloadStorage {
    const val GB = 1_000_000_000L
    const val TAG_ALLOWANCE = 262_144L
    const val STORAGE_BATCH_SIZE = 25

    fun bitrate(quality: MusicAudioQuality): Int =
        when (quality) {
            MusicAudioQuality.LOW -> 48_000
            MusicAudioQuality.MEDIUM -> 128_000
            MusicAudioQuality.HIGH, MusicAudioQuality.AUTO -> 160_000
        }

    fun estimateBytes(
        seconds: Int,
        bitsPerSecond: Int,
    ): Long = (seconds.coerceIn(1, 600).toLong() * bitsPerSecond.coerceIn(32_000, 512_000) / 8 * 1.05).toLong() + TAG_ALLOWANCE

    data class Estimate(
        val songs: Int,
        val hours: Double,
        val averageSeconds: Int,
        val bitsPerSecond: Int,
    )

    fun estimate(
        budgetBytes: Long,
        rows: List<DownloadWithItems>,
        quality: MusicAudioQuality,
    ): Estimate {
        val durations =
            rows.mapNotNull {
                DownloadRequest.decode(it.download.requestJson)?.takeIf { request -> request.isMusic }?.durationSeconds?.takeIf { d ->
                    d in
                        1..600
                }
            }
        val average = if (durations.isEmpty()) 240 else durations.average().toInt()
        val rate = bitrate(quality)
        val count = (budgetBytes.coerceAtLeast(0) / estimateBytes(average, rate)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return Estimate(count, count * average / 3600.0, average, rate)
    }

    fun actualBytes(rows: List<DownloadWithItems>): Long =
        rows.filter { it.isSmartDownload() }.sumOf { row ->
            row.items.sumOf { max(it.downloadedBytes, if (it.status == DownloadItemStatus.COMPLETED) it.totalBytes else 0L) }
        }

    fun reservedBytes(
        rows: List<DownloadWithItems>,
        exceptId: String? = null,
    ): Long =
        rows.filter { it.isSmartDownload() && it.download.videoId != exceptId }.sumOf { row ->
            val measured = row.items.sumOf { max(it.totalBytes, it.downloadedBytes) }
            if (row.overallStatus == DownloadItemStatus.COMPLETED) {
                measured
            } else {
                val request = DownloadRequest.decode(row.download.requestJson)
                max(measured, request?.smartEstimatedBytes?.takeIf { it > 0 } ?: SmartDownloadPolicy.RESERVE_PER_SONG)
            }
        }
}
