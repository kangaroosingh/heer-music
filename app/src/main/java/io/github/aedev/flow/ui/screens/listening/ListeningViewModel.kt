package io.github.aedev.flow.ui.screens.listening

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.listening.ListeningEntry
import io.github.aedev.flow.data.listening.ListeningIds
import io.github.aedev.flow.data.listening.ListeningState
import io.github.aedev.flow.data.listening.ListeningStore
import io.github.aedev.flow.data.listening.PodcastFeeds
import io.github.aedev.flow.data.newmusic.InnertubeMusicService
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
internal class ListeningViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: ListeningStore,
        private val feeds: PodcastFeeds,
        private val downloads: io.github.aedev.flow.data.music.DownloadManager,
        private val youtube: io.github.aedev.flow.data.repository.YouTubeRepository,
    ) : ViewModel() {
        val library =
            store.state.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ListeningState(),
            )
        private val _results = MutableStateFlow<List<ListeningEntry>>(emptyList())
        val results = _results.asStateFlow()
        private val _busy = MutableStateFlow(false)
        val busy = _busy.asStateFlow()
        private val _error = MutableStateFlow(false)
        val error = _error.asStateFlow()

        fun offline(value: Boolean) {
            viewModelScope.launch {
                store.update { it.copy(downloadedOnly = value) }
                val track = EnhancedMusicPlayerManager.currentTrack.value
                if (value && track != null && ListeningIds.audioUri(track.videoId)?.scheme != "content" &&
                    downloads.getDownloadedTrackPath(track.videoId) == null
                ) {
                    EnhancedMusicPlayerManager.pause()
                }
            }
        }

        fun favourite(id: String) {
            viewModelScope.launch {
                store.update {
                    it.copy(favourites = if (id in it.favourites) it.favourites - id else it.favourites + id)
                }
            }
        }

        fun remove(id: String) {
            viewModelScope.launch {
                store.update {
                    it.copy(
                        entries =
                            it.entries.filterNot { e ->
                                e.id == id
                            },
                    )
                }
            }
        }

        fun bookmark() {
            val id = EnhancedMusicPlayerManager.currentTrack.value?.videoId ?: return
            if (ListeningIds.kind(id) == null || ListeningIds.isRadio(id)) return
            val position = EnhancedMusicPlayerManager.currentPosition.value
            viewModelScope.launch {
                store.update {
                    it.copy(bookmarks = it.bookmarks + (id to position))
                }
            }
        }

        fun importAudio(uris: List<Uri>) =
            perform {
                val entries =
                    uris.take(100).map { uri ->
                        val name =
                            context.contentResolver
                                .query(
                                    uri,
                                    arrayOf(OpenableColumns.DISPLAY_NAME),
                                    null,
                                    null,
                                    null,
                                )?.use {
                                    if (it.moveToFirst()) it.getString(0) else null
                                }
                                ?: uri.lastPathSegment.orEmpty()
                        ListeningEntry(
                            ListeningIds.of(
                                "book",
                                uri.toString(),
                            ),
                            name,
                        )
                    }

                store.add(entries)
            }

        fun addLink(
            raw: String,
            books: Boolean,
        ) = perform {
            val uri = Uri.parse(raw.trim())
            require(uri.scheme == "https")
            if (uri.host in
                setOf(
                    "youtube.com",
                    "www.youtube.com",
                    "music.youtube.com",
                    "youtu.be",
                )
            ) {
                val list = uri.getQueryParameter("list")
                val tracks =
                    if (list != null) {
                        InnertubeMusicService
                            .fetchWatchPlaylist(
                                list,
                                null,
                            ).take(100)
                    } else {
                        val id = if (uri.host == "youtu.be") uri.lastPathSegment else uri.getQueryParameter("v")
                        require(id?.matches(Regex("[A-Za-z0-9_-]{11}")) == true)
                        InnertubeMusicService.fetchQueue(videoIds = listOf(checkNotNull(id)))
                    }

                require(tracks.isNotEmpty())
                store.add(
                    tracks.map {
                        ListeningEntry(
                            ListeningIds.of(
                                if (books) "youtube_book" else "youtube_podcast",
                                "https://www.youtube.com/watch?v=${it.videoId}",
                            ),
                            it.title,
                            it.artist,
                            it.thumbnailUrl,
                            it.duration,
                        )
                    },
                )
            } else {
                require(!books)
                val entries = feeds.load(raw.trim())
                require(entries.isNotEmpty())
                store.add(entries)
                store.update {
                    it.copy(feeds = (it.feeds + raw.trim()).distinct().take(12))
                }
            }
        }

        fun search(
            query: String,
            books: Boolean,
        ) = perform {
            require(query.isNotBlank())
            val videos =
                youtube
                    .searchVideoPage(query.trim() + if (books) " audiobook" else " podcast")
                    .getOrThrow()
                    .first
                    .take(20)
            _results.value =
                videos.map {
                    ListeningEntry(
                        ListeningIds.of(
                            if (books) "youtube_book" else "youtube_podcast",
                            "https://www.youtube.com/watch?v=${it.id}",
                        ),
                        it.title,
                        it.channelName,
                        it.thumbnailUrl,
                        it.duration,
                    )
                }
        }

        fun saveResult(entry: ListeningEntry) {
            viewModelScope.launch {
                store.add(listOf(entry))
            }
        }

        fun refresh() =
            perform {
                store.state.first().feeds.take(12).forEach {
                    store.add(feeds.load(it))
                }
            }

        private fun perform(block: suspend () -> Unit) {
            if (_busy.value) return
            _busy.value = true
            _error.value = false
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        block()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _error.value = true
                } finally {
                    _busy.value = false
                }
            }
        }
    }
