package io.github.aedev.flow.data.music.smart

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.MusicAudioQuality
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.*
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.video.downloader.request.toDownloadRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SmartDownloadStorageTest {
    private fun row(
        id: String,
        bytes: Long,
        smart: Boolean = true,
        status: DownloadItemStatus = DownloadItemStatus.COMPLETED,
    ): DownloadWithItems {
        val request =
            MusicTrack(
                id,
                "Track",
                "Artist",
                "",
                240,
            ).toDownloadRequest().copy(smartDownload = smart, smartEstimatedBytes = 5_000_000)
        return DownloadWithItems(
            DownloadEntity(id, "Track", "Artist", requestJson = request.encode()),
            listOf(
                DownloadItemEntity(
                    videoId = id,
                    fileType = DownloadFileType.AUDIO,
                    fileName = "track.m4a",
                    filePath = "/fixture/$id",
                    downloadedBytes = bytes,
                    totalBytes = bytes,
                    status = status,
                ),
            ),
        )
    }

    @Test fun `GB estimate grows with budget and includes hours and track count`() {
        val one = SmartDownloadStorage.estimate(1_000_000_000, emptyList(), MusicAudioQuality.HIGH)
        val two = SmartDownloadStorage.estimate(2_000_000_000, emptyList(), MusicAudioQuality.HIGH)
        assertThat(two.songs).isAtLeast(one.songs * 2)
        assertThat(one.hours).isWithin(0.01).of(one.songs * 240 / 3600.0)
        assertThat(one.averageSeconds).isEqualTo(240)
        assertThat(SmartDownloadStorage.estimate(1_000_000_000, emptyList(), MusicAudioQuality.LOW).songs).isGreaterThan(one.songs)
    }

    @Test fun `saved durations inform the average and manual files do not consume the automatic budget`() {
        val rows = listOf(row("abcdefghij0", 10_000_000), row("abcdefghij1", 30_000_000, smart = false))
        assertThat(SmartDownloadStorage.actualBytes(rows)).isEqualTo(10_000_000)
        assertThat(SmartDownloadStorage.reservedBytes(rows)).isEqualTo(10_000_000)
        assertThat(SmartDownloadStorage.estimate(1_000_000_000, rows, MusicAudioQuality.HIGH).averageSeconds).isEqualTo(240)
    }

    @Test fun `pending files reserve space before their actual byte count is known`() {
        val rows = listOf(row("abcdefghij0", 0, status = DownloadItemStatus.PENDING))
        assertThat(SmartDownloadStorage.actualBytes(rows)).isEqualTo(0)
        assertThat(SmartDownloadStorage.reservedBytes(rows)).isEqualTo(5_000_000)
    }

    @Test fun `GB selection honors remaining reserved bytes instead of the legacy song count`() {
        val songs = (0..40).map { MusicTrack("song%07d".format(it), "Track $it", "Artist", "", 240) }
        val bytes = SmartDownloadStorage.estimateBytes(240, 160_000)
        val selected =
            SmartDownloadPolicy.select(
                songs,
                SmartDownloadSettings(
                    enabled = true,
                    networkConfigured = true,
                    maxSongs = 10,
                    maxStorageBytes =
                        bytes * 3,
                ),
                emptySet(),
                100,
                0,
                10_000_000_000,
                emptySet(),
                emptyMap(),
                1,
                reservedBytes = bytes,
            )
        assertThat(selected).containsExactlyElementsIn(songs.take(2)).inOrder()
    }

    @Test fun `final file budget excludes its own reservation and enforces a lowered budget`() =
        runTest {
            val store = mockk<SmartDownloadStore>()
            val dao = mockk<DownloadDao>()
            every { store.state } returns flowOf(SmartDownloadSettings(maxStorageBytes = 1_000_000_000))
            coEvery { dao.getAllDownloadsWithItemsOnce() } returns
                listOf(row("abcdefghij0", 600_000_000), row("abcdefghij1", 0, status = DownloadItemStatus.PENDING))
            val budget = SmartDownloadBudget(store, dao)
            assertThat(budget.canCommit("abcdefghij1", 400_000_000)).isTrue()
            assertThat(budget.canCommit("abcdefghij1", 400_000_001)).isFalse()
            assertThat(budget.canCommit("other", 400_000_000)).isFalse()
        }
}
