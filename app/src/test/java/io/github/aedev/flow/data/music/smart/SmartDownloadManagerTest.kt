package io.github.aedev.flow.data.music.smart

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.PlaylistRepository
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicPersonalization
import io.github.aedev.flow.data.recommendation.music.MusicRecommendationCatalog
import io.github.aedev.flow.data.recommendation.music.MusicRecommendationProfile
import io.github.aedev.flow.data.recommendation.music.MusicTasteProfile
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.downloader.request.toDownloadRequest
import io.github.aedev.flow.data.video.downloader.work.DownloadController
import io.github.aedev.flow.data.video.downloader.work.EnqueueOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SmartDownloadManagerTest {
    private val environment = mockk<SmartDownloadEnvironment>()
    private val store = mockk<SmartDownloadStore>()
    private val brain = mockk<MusicBrainEngine>()
    private val personal = mockk<MusicPersonalization>()
    private val catalog = mockk<MusicRecommendationCatalog>()
    private val artists = mockk<FavouriteArtistsStore>()
    private val library = mockk<PlaylistRepository>()
    private val likes = mockk<LikedVideosRepository>()
    private val saved = mockk<io.github.aedev.flow.data.local.PlaylistRepository>()
    private val downloads = mockk<DownloadManager>(relaxed = true)
    private val dao = mockk<DownloadDao>()
    private val controller = mockk<DownloadController>(relaxed = true)
    private val preferences = mockk<PlayerPreferences>()
    private val state = MutableStateFlow(SmartDownloadSettings(enabled = true, networkConfigured = true))
    private val rows = mutableListOf<DownloadWithItems>()
    private val queued = mutableListOf<DownloadRequest>()
    private val songs = (1..12).map { MusicTrack("song%07d".format(it), "Song $it", "Artist $it", "", 180) }
    private val manager =
        SmartDownloadManager(
            environment,
            store,
            brain,
            personal,
            catalog,
            artists,
            library,
            likes,
            saved,
            downloads,
            dao,
            controller,
            preferences,
        )

    private fun row(
        request: DownloadRequest,
        status: DownloadItemStatus = DownloadItemStatus.PENDING,
    ) = DownloadWithItems(
        DownloadEntity(videoId = request.videoId, title = request.tags.title, uploader = "Artist", requestJson = request.encode()),
        listOf(
            DownloadItemEntity(
                videoId = request.videoId,
                fileType = DownloadFileType.AUDIO,
                fileName = "x",
                filePath = "/x",
                status = status,
            ),
        ),
    )

    @Before fun setup() {
        every { environment.availableBytes() } returns 10_000_000_000L
        every { environment.networkAllowed(any()) } returns true
        every { store.state } returns state
        coEvery { store.update(any()) } coAnswers
            { state.value = firstArg<(SmartDownloadSettings) -> SmartDownloadSettings>()(state.value) }
        coEvery { preferences.isDeepFlowCurrentlyActive() } returns false
        every { preferences.downloadOverWifiOnly } returns flowOf(false)
        every { preferences.musicDownloadQuality } returns flowOf(io.github.aedev.flow.data.local.MusicAudioQuality.HIGH)
        coEvery { store.reserveAddition(any(), any(), any(), any(), any()) } coAnswers {
            val id = firstArg<String>()
            val time = secondArg<Long>()
            state.value = state.value.copy(recentAdditions = state.value.recentAdditions + (id to time))
            true
        }
        coEvery { dao.getAllDownloadsWithItemsOnce() } answers { rows.toList() }
        every { library.history } returns flowOf(emptyList())
        every { library.favorites } returns flowOf(emptyList())
        every { likes.getLikedMusicFlow() } returns flowOf(emptyList())
        every { artists.artists } returns flowOf(emptyList())
        every { downloads.downloadedTracks } returns flowOf(emptyList())
        every { personal.profile } returns flowOf(MusicRecommendationProfile())
        every { brain.hiddenArtists } returns MutableStateFlow(emptySet())
        coEvery { brain.heavyRotationTracks(any()) } returns emptyList()
        coEvery { brain.timeOfDayTracks(any()) } returns emptyList()
        coEvery { brain.rediscoverTracks(any()) } returns emptyList()
        val taste = mockk<MusicTasteProfile>()
        every { taste.topArtists } returns emptyList()
        coEvery { brain.tasteProfile() } returns taste
        coEvery { saved.smartDownloadSeeds(any()) } returns emptyList()
        coEvery { catalog.candidates(any(), any()) } returns songs
        coEvery { brain.rankTracks(any(), any()) } answers { firstArg() }
        coEvery { controller.enqueue(any(), any()) } coAnswers {
            val request = firstArg<DownloadRequest>()
            queued += request
            rows += row(request)
            EnqueueOutcome.QUEUED
        }
    }

    @Test fun `opt out and missing network consent never fetch or queue`() =
        runTest {
            state.value = state.value.copy(enabled = false)
            manager.fill()
            state.value = state.value.copy(enabled = true, networkConfigured = false)
            manager.fill()
            coVerify(exactly = 0) { catalog.candidates(any(), any()) }
            coVerify(exactly = 0) { controller.enqueue(any(), any()) }
        }

    @Test fun `private listening does not trigger catalogue requests`() =
        runTest {
            coEvery { preferences.isDeepFlowCurrentlyActive() } returns true
            manager.fill()
            assertThat(state.value.lastResult).isEqualTo(SmartDownloadResult.PRIVATE)
            coVerify(exactly = 0) { catalog.candidates(any(), any()) }
        }

    @Test fun `the chosen network rule gates selection`() =
        runTest {
            every { preferences.downloadOverWifiOnly } returns flowOf(true)
            every { environment.networkAllowed(true) } returns false
            manager.fill()
            assertThat(state.value.lastResult).isEqualTo(SmartDownloadResult.WAITING)
            coVerify(exactly = 0) { catalog.candidates(any(), any()) }
        }

    @Test fun `low storage prevents metadata and file downloads`() =
        runTest {
            every { environment.availableBytes() } returns SmartDownloadPolicy.MIN_FREE_BYTES
            manager.fill()
            assertThat(state.value.lastResult).isEqualTo(SmartDownloadResult.LOW_STORAGE)
            coVerify(exactly = 0) { catalog.candidates(any(), any()) }
        }

    @Test fun `automatic checks are rate limited but an explicit check can fill sooner`() =
        runTest {
            state.value = state.value.copy(lastRunAt = System.currentTimeMillis())
            manager.fill()
            assertThat(queued).isEmpty()
            manager.fill(manual = true)
            assertThat(queued).hasSize(5)
        }

    @Test fun `five suggestions use the native queue with persistent automatic ownership`() =
        runTest {
            manager.fill()
            assertThat(queued).hasSize(5)
            assertThat(queued.all { it.smartDownload && it.wantsAudioOnly && it.threads == 1 }).isTrue()
            assertThat(state.value.recentAdditions.keys).containsExactlyElementsIn(queued.map { it.videoId })
            coVerify(exactly = 0) { downloads.deleteDownload(any()) }
        }

    @Test fun `existing manual downloads are not replaced or relabelled`() =
        runTest {
            val manual = songs.first().toDownloadRequest()
            rows += row(manual, DownloadItemStatus.COMPLETED)
            manager.fill()
            assertThat(queued.map { it.videoId }).doesNotContain(manual.videoId)
            assertThat(DownloadRequest.decode(rows.first().download.requestJson)?.smartDownload).isFalse()
        }

    @Test fun `paused and failed automatic requests still reserve their slots`() =
        runTest {
            state.value = state.value.copy(maxSongs = 10)
            repeat(10) { rows += row(songs[it].toDownloadRequest().copy(smartDownload = true), DownloadItemStatus.FAILED) }
            manager.fill()
            assertThat(state.value.lastResult).isEqualTo(SmartDownloadResult.FULL)
            coVerify(exactly = 0) { catalog.candidates(any(), any()) }
        }

    @Test fun `replays use known history metadata without guessing a duration`() =
        runTest {
            val replay = songs.first()
            coEvery { catalog.candidates(any(), any()) } returns emptyList()
            coEvery { brain.heavyRotationTracks(any()) } returns listOf(replay.copy(duration = 0))
            every { library.history } returns flowOf(listOf(replay))
            manager.fill()
            assertThat(queued.single().videoId).isEqualTo(replay.videoId)
            assertThat(queued.single().durationSeconds).isEqualTo(180)
        }

    @Test fun `dislikes and artist blocks are checked again before queuing`() =
        runTest {
            every { personal.profile } returns flowOf(MusicRecommendationProfile(disliked = mapOf(songs.first().videoId to "Disliked")))
            every { brain.hiddenArtists } returns MutableStateFlow(setOf("artist 2"))
            manager.fill()
            assertThat(queued.map { it.videoId }).containsNoneOf(songs[0].videoId, songs[1].videoId)
        }

    @Test fun `space is rechecked after catalogue work before a request is queued`() =
        runTest {
            every { environment.availableBytes() } returnsMany listOf(10_000_000_000L, SmartDownloadPolicy.MIN_FREE_BYTES)
            manager.fill()
            assertThat(queued).isEmpty()
            assertThat(state.value.lastResult).isEqualTo(SmartDownloadResult.LOW_STORAGE)
        }

    @Test fun `a cancelled selector finishes only its current queue bookkeeping commit`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coEvery { store.reserveAddition(any(), any(), any(), any(), any()) } coAnswers {
                entered.complete(Unit)
                release.await()
                val id = firstArg<String>()
                state.value = state.value.copy(recentAdditions = mapOf(id to secondArg<Long>()))
                true
            }
            val job = launch { manager.fill(manual = true) }
            entered.await()
            job.cancel()
            release.complete(Unit)
            job.join()
            assertThat(queued).hasSize(1)
            assertThat(state.value.recentAdditions.keys).containsExactly(queued.single().videoId)
        }
}
