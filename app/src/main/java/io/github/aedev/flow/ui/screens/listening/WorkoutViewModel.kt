package io.github.aedev.flow.ui.screens.listening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.listening.*
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.toVideo
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

internal data class WorkoutState(
    val request: WorkoutRequest = WorkoutRequest(),
    val tracks: List<MusicTrack> = emptyList(),
    val busy: Boolean = false,
    val generated: Boolean = false,
    val saved: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
internal class WorkoutViewModel
    @Inject
    constructor(
        private val brain: MusicBrainEngine,
        private val artists: FavouriteArtistsStore,
        private val listening: ListeningStore,
        private val downloads: DownloadManager,
        private val playlists: PlaylistRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(WorkoutState())
        val state = mutable.asStateFlow()
        private val cache = linkedMapOf<String, Pair<Long, List<MusicTrack>>>()

        fun request(value: WorkoutRequest) {
            if (!mutable.value.busy) mutable.value = WorkoutState(request = value)
        }

        fun generate() {
            if (mutable.value.busy) return
            val request = mutable.value.request
            mutable.update { it.copy(busy = true, error = false, saved = false) }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val offline = listening.state.first().downloadedOnly
                    val candidates =
                        if (offline) {
                            downloads.downloadedTracks.first().map { it.track }
                        } else {
                            val queries = WorkoutPolicy.queries(request, artists.artists.first().map { it.name })
                            val key = queries.joinToString("|")
                            cache[key]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.second ?: coroutineScope {
                                queries
                                    .map { query ->
                                        async {
                                            try {
                                                withTimeoutOrNull(12_000) { YouTubeMusicService.searchMusic(query, 20) }.orEmpty()
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (_: Exception) {
                                                emptyList()
                                            }
                                        }
                                    }.awaitAll()
                                    .flatten()
                                    .distinctBy { it.videoId }
                                    .also {
                                        cache[key] = System.currentTimeMillis() to it
                                        while (cache.size > 4) cache.remove(cache.keys.first())
                                    }
                            }
                        }
                    val ordered = brain.rankTracks(candidates, "workout")
                    val tracks = WorkoutPolicy.build(ordered, request)
                    mutable.update { it.copy(tracks = tracks, generated = true) }
                } catch (
                    e: CancellationException,
                ) {
                    throw e
                } catch (
                    _: Exception,
                ) {
                    mutable.update { it.copy(error = true) }
                } finally {
                    mutable.update { it.copy(busy = false) }
                }
            }
        }

        fun save(
            name: String,
            description: String,
        ) {
            val snapshot = mutable.value
            if (snapshot.busy || snapshot.saved || snapshot.tracks.isEmpty()) return
            mutable.update { it.copy(busy = true, error = false) }
            viewModelScope.launch {
                try {
                    playlists.importPlaylist(name, description, snapshot.tracks.map { it.toVideo().copy(isMusic = true) }, isMusic = true)
                    mutable.update { it.copy(saved = true) }
                } catch (
                    e: CancellationException,
                ) {
                    throw e
                } catch (
                    _: Exception,
                ) {
                    mutable.update { it.copy(error = true) }
                } finally {
                    mutable.update { it.copy(busy = false) }
                }
            }
        }
    }
