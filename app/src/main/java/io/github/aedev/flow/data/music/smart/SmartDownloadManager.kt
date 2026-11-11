package io.github.aedev.flow.data.music.smart

import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.model.toMusicTrack
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.PlaylistRepository
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicPersonalization
import io.github.aedev.flow.data.recommendation.music.MusicRecommendationCatalog
import io.github.aedev.flow.data.recommendation.music.isHiddenArtist
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.downloader.request.toDownloadRequest
import io.github.aedev.flow.data.video.downloader.work.DownloadController
import io.github.aedev.flow.data.video.downloader.work.EnqueueOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

internal fun DownloadWithItems.isSmartDownload(): Boolean = DownloadRequest.decode(download.requestJson)?.smartDownload == true

@Singleton
class SmartDownloadManager
    @Inject
    constructor(
        private val environment: SmartDownloadEnvironment,
        private val store: SmartDownloadStore,
        private val brain: MusicBrainEngine,
        private val personalization: MusicPersonalization,
        private val catalog: MusicRecommendationCatalog,
        private val artists: FavouriteArtistsStore,
        private val library: PlaylistRepository,
        private val likes: LikedVideosRepository,
        private val saved: io.github.aedev.flow.data.local.PlaylistRepository,
        private val downloads: DownloadManager,
        private val dao: DownloadDao,
        private val controller: DownloadController,
        private val preferences: PlayerPreferences,
    ) {
        private val mutex = Mutex()

        suspend fun fill(manual: Boolean = false) {
            mutex.withLock { selectAndQueue(manual) }
        }

        private suspend fun selectAndQueue(manual: Boolean) {
            val settings = store.state.first()
            if (!settings.enabled || !settings.networkConfigured) return
            if (preferences.isDeepFlowCurrentlyActive()) return result(SmartDownloadResult.PRIVATE)
            if (!networkAllowed()) return result(SmartDownloadResult.WAITING)
            var rows = dao.getAllDownloadsWithItemsOnce()
            if (settings.rotationEnabled &&
                (manual || System.currentTimeMillis() - settings.lastRunAt >= SmartDownloadPolicy.CHECK_INTERVAL_MS)
            ) {
                rotateWhenFull(settings, rows)
                rows = dao.getAllDownloadsWithItemsOnce()
            }
            val used = rows.count { it.isSmartDownload() }
            if (settings.maxStorageBytes?.let { SmartDownloadStorage.reservedBytes(rows) >= it }
                ?: (used >= settings.maxSongs.coerceIn(10, 50))
            ) {
                return result(SmartDownloadResult.FULL)
            }
            val pending = rows.count { it.overallStatus in setOf(DownloadItemStatus.PENDING, DownloadItemStatus.DOWNLOADING) }
            val free = environment.availableBytes()
            val reserved = SmartDownloadPolicy.MIN_FREE_BYTES + (pending + 1L) * SmartDownloadPolicy.RESERVE_PER_SONG
            if (free < reserved) return result(SmartDownloadResult.LOW_STORAGE)
            val bitsPerSecond = SmartDownloadStorage.bitrate(preferences.musicDownloadQuality.first())
            val now = System.currentTimeMillis()
            if (!manual && settings.lastRunAt > 0 && now - settings.lastRunAt < SmartDownloadPolicy.CHECK_INTERVAL_MS) {
                return result(SmartDownloadResult.COOLDOWN)
            }
            store.update { it.copy(lastRunAt = now) }
            val ranked = brain.rankTracks(candidates(), "smart_downloads")
            val taste = personalization.profile.first()
            val storedIds = rows.mapTo(mutableSetOf()) { it.download.videoId }
            val offlineIds = downloads.downloadedTracks.first().map { it.track.videoId }
            val selected =
                SmartDownloadPolicy.select(
                    ranked,
                    store.state.first(),
                    storedIds + offlineIds,
                    used,
                    pending,
                    free,
                    taste.disliked.keys,
                    taste.earlySkips,
                    now,
                    SmartDownloadStorage.reservedBytes(rows),
                    bitsPerSecond,
                )
            var queued = 0
            var stopReason = SmartDownloadResult.EMPTY
            for (track in selected) {
                currentCoroutineContext().ensureActive()
                val current = store.state.first()
                if (!current.enabled || !current.networkConfigured) break
                if (!networkAllowed()) {
                    stopReason = SmartDownloadResult.WAITING
                    break
                }
                if (preferences.isDeepFlowCurrentlyActive()) {
                    stopReason = SmartDownloadResult.PRIVATE
                    break
                }
                val latestTaste = personalization.profile.first()
                if (track.isHiddenArtist(brain.hiddenArtists.value) || track.videoId in latestTaste.disliked) continue
                if ((latestTaste.earlySkips[track.videoId] ?: 0) >= 2) continue
                val currentRows = dao.getAllDownloadsWithItemsOnce()
                val estimatedBytes = SmartDownloadStorage.estimateBytes(track.duration, bitsPerSecond)
                val reservedBytes = SmartDownloadStorage.reservedBytes(currentRows)
                if (current.maxStorageBytes?.let { reservedBytes + estimatedBytes > it }
                    ?: (currentRows.count { it.isSmartDownload() } >= current.maxSongs.coerceIn(10, 50))
                ) {
                    stopReason = SmartDownloadResult.FULL
                    break
                }
                val currentPending =
                    currentRows.count {
                        it.overallStatus in setOf(DownloadItemStatus.PENDING, DownloadItemStatus.DOWNLOADING)
                    }
                val required = SmartDownloadPolicy.MIN_FREE_BYTES + (currentPending + 1L) * SmartDownloadPolicy.RESERVE_PER_SONG
                if (environment.availableBytes() < required) {
                    stopReason = SmartDownloadResult.LOW_STORAGE
                    break
                }
                val outcome =
                    withContext(NonCancellable) {
                        if (store.reserveAddition(
                                track.videoId,
                                now,
                                currentRows.count { it.isSmartDownload() },
                                reservedBytes,
                                estimatedBytes,
                            )
                        ) {
                            controller.enqueue(
                                track.toDownloadRequest().copy(smartDownload = true, threads = 1, smartEstimatedBytes = estimatedBytes),
                            )
                        } else {
                            null
                        }
                    }
                if (outcome == null) break
                if (outcome == EnqueueOutcome.QUEUED) queued++
            }
            result(if (queued == 0) stopReason else SmartDownloadResult.QUEUED)
        }

        private suspend fun rotateWhenFull(
            settings: SmartDownloadSettings,
            rows: List<DownloadWithItems>,
        ) {
            val full =
                settings.maxStorageBytes?.let { SmartDownloadStorage.reservedBytes(rows) >= it }
                    ?: (rows.count { it.isSmartDownload() } >= settings.maxSongs.coerceIn(10, 50))
            if (!full || environment.availableBytes() < SmartDownloadPolicy.MIN_FREE_BYTES + SmartDownloadPolicy.RESERVE_PER_SONG) return
            val stored = rows.map { it.download.videoId }.toSet() + downloads.downloadedTracks.first().map { it.track.videoId }
            val taste = personalization.profile.first()
            val replacements =
                brain
                    .rankTracks(candidates(), "smart_rotation")
                    .filter {
                        it.videoId !in stored && it.videoId !in taste.disliked &&
                            it.duration in 1..600 && !it.isHiddenArtist(brain.hiddenArtists.value) &&
                            it.videoId.matches(Regex("[A-Za-z0-9_-]{11}")) && (taste.earlySkips[it.videoId] ?: 0) < 2 &&
                            settings.recentAdditions[it.videoId]?.let { at ->
                                System.currentTimeMillis() - at <
                                    SmartDownloadPolicy.READDITION_DELAY_MS
                            } !=
                            true
                    }.take(5)
            if (replacements.isEmpty()) return
            val protected =
                library.favorites
                    .first()
                    .map { it.videoId }
                    .toSet() +
                    likes.getLikedMusicFlow().first().map { it.videoId } +
                    library.playlists
                        .first()
                        .flatMap { it.tracks }
                        .map { it.videoId } +
                    saved.smartDownloadSeeds(Int.MAX_VALUE).map { it.videoId } +
                    library.history
                        .first()
                        .take(50)
                        .map { it.videoId } +
                    listOfNotNull(
                        io.github.aedev.flow.player.EnhancedMusicPlayerManager.currentTrack.value
                            ?.videoId,
                    )
            val now = System.currentTimeMillis()
            val ids =
                SmartDownloadRotation.select(
                    rows.map { row ->
                        SmartDownloadRotation.Entry(
                            row.download.videoId,
                            row.download.createdAt,
                            row.isSmartDownload(),
                            row.overallStatus == DownloadItemStatus.COMPLETED,
                        )
                    },
                    protected,
                    now,
                    replacements.size,
                )
            val freed = SmartDownloadStorage.reservedBytes(rows.filter { it.download.videoId in ids })
            val smallestReplacement =
                replacements.minOf {
                    SmartDownloadStorage.estimateBytes(it.duration, SmartDownloadStorage.bitrate(preferences.musicDownloadQuality.first()))
                }
            if (settings.maxStorageBytes?.let { it - SmartDownloadStorage.reservedBytes(rows) + freed < smallestReplacement } ==
                true
            ) {
                return
            }
            for (id in ids) {
                if (!networkAllowed() || !store.state.first().rotationEnabled) break
                if (io.github.aedev.flow.player.EnhancedMusicPlayerManager.currentTrack.value
                        ?.videoId == id
                ) {
                    continue
                }
                downloads.deleteDownload(id)
                store.update { current ->
                    val recent = ((current.recentAdditions - id) + (id to now)).entries.toList().takeLast(200)
                    current.copy(recentAdditions = recent.associate { it.toPair() })
                }
            }
        }

        private suspend fun candidates(): List<MusicTrack> {
            val history = library.history.first().associateBy { it.videoId }
            val rotation = brain.heavyRotationTracks(40) + brain.timeOfDayTracks(20) + brain.rediscoverTracks(20)
            val seeds = rotation.map { history[it.videoId] ?: it }
            val pickedArtists = artists.artists.first().map { it.name }
            val learnedArtists = brain.tasteProfile().topArtists.map { it.name }
            val preferred = (learnedArtists + pickedArtists).distinct().take(3)
            val liked =
                likes
                    .getLikedMusicFlow()
                    .first()
                    .take(80)
                    .map { it.toMusicTrack() }
            val local = library.favorites.first().take(80) + liked + seeds + saved.smartDownloadSeeds(80)
            val pool = local + catalog.candidates(personalization.profile.first(), preferred)
            return pool.groupBy { it.videoId }.values.map { group ->
                group.firstOrNull { it.duration > 0 } ?: group.first()
            }
        }

        private suspend fun networkAllowed(): Boolean = environment.networkAllowed(preferences.downloadOverWifiOnly.first())

        private suspend fun result(value: SmartDownloadResult) {
            store.update { it.copy(lastResult = if (it.enabled) value else SmartDownloadResult.DISABLED) }
        }
    }
