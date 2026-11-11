package io.github.aedev.flow.ui.screens.music

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.PlaylistRepository
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.scrobble.Scrobbler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MusicPlayerFeedbackTest {
    private val song = MusicTrack("abcdefghijk", "Song", "Artist", "", 240)
    private val library = mockk<PlaylistRepository>(relaxed = true)
    private val likes = mockk<LikedVideosRepository>(relaxed = true)
    private val downloads = mockk<DownloadManager>(relaxed = true)
    private val brain = mockk<MusicBrainEngine>(relaxed = true)

    @Test fun `like queues the canonical song even when listening to its video`() =
        runTest {
            val state = MutableStateFlow(MusicPlayerUiState(currentTrack = song.copy(videoId = "video000001")))
            val actions =
                MusicPlayerTrackActions(
                    mockk<Context>(),
                    this,
                    state,
                    library,
                    likes,
                    downloads,
                    brain,
                    mockk<PlayerPreferences>(relaxed = true),
                    mockk<Scrobbler>(relaxed = true),
                ) {
                    song
                }
            actions.toggleLike()
            testScheduler.advanceUntilIdle()
            coVerify(timeout = 5000) { downloads.downloadTrack(song) }
            coVerify(timeout = 5000) { brain.onExplicitLike(song) }
            assertThat(state.value.isLiked).isTrue()
        }

    @Test fun `dislike excludes the recording removes its like and deletes its local download`() =
        runTest {
            val state = MutableStateFlow(MusicPlayerUiState(currentTrack = song, isLiked = true))
            val actions =
                MusicPlayerTrackActions(
                    mockk<Context>(),
                    this,
                    state,
                    library,
                    likes,
                    downloads,
                    brain,
                    mockk<PlayerPreferences>(relaxed = true),
                    mockk<Scrobbler>(relaxed = true),
                )
            actions.notInterested(song)
            testScheduler.advanceUntilIdle()
            coVerify(timeout = 5000) { brain.dislikeSong(song) }
            coVerify(timeout = 5000) { likes.removeLikeState(song.videoId) }
            coVerify(timeout = 5000) { library.removeFromFavorites(song.videoId) }
            coVerify(timeout = 5000) { downloads.deleteDownload(song.videoId) }
            coVerify(exactly = 0) { brain.blockArtist(any(), any()) }
        }
}
