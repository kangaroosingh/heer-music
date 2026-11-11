package io.github.aedev.flow.ui.screens.listening

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.listening.ListeningStore
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicPersonalization
import io.github.aedev.flow.data.recommendation.music.MusicRecommendationCatalog
import io.github.aedev.flow.data.recommendation.music.isHiddenArtist
import io.github.aedev.flow.ui.components.layout.flowBottomContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ListeningMix(
    val title: Int,
    val explanation: Int,
    val tracks: List<MusicTrack>,
)

@HiltViewModel
internal class ListeningMixesViewModel
    @Inject
    constructor(
        private val brain: MusicBrainEngine,
        private val catalog: MusicRecommendationCatalog,
        private val personal: MusicPersonalization,
        private val artists: FavouriteArtistsStore,
        private val listening: ListeningStore,
        private val downloads: DownloadManager,
    ) : ViewModel() {
        private val _mixes = MutableStateFlow<List<ListeningMix>>(emptyList())
        val mixes =
            combine(
                _mixes,
                personal.profile,
                brain.hiddenArtists,
                listening.state,
                downloads.downloadedTracks,
            ) { mixes, profile, hidden, settings, downloaded ->
                val ids = downloaded.map { it.track.videoId }.toSet()
                mixes.map { mix ->
                    val eligible =
                        mix.tracks.filter { track ->
                            track.videoId !in profile.disliked &&
                                !track.isHiddenArtist(hidden) &&
                                (!settings.downloadedOnly || track.videoId in ids)
                        }
                    mix.copy(tracks = eligible)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        private val _busy = MutableStateFlow(false)
        val busy = _busy.asStateFlow()
        private var loadedAt = 0L

        fun load() {
            if (_busy.value || System.currentTimeMillis() - loadedAt < 600_000) return
            _busy.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val offline = listening.state.first().downloadedOnly
                    val available = downloads.downloadedTracks.first().map { it.track }
                    val ids = available.map { it.videoId }.toSet()

                    fun eligible(tracks: List<MusicTrack>) = if (offline) tracks.filter { it.videoId in ids } else tracks
                    val favourite =
                        artists.artists
                            .first()
                            .map { it.name }
                            .take(3)
                    val candidates = if (offline) available else catalog.candidates(personal.profile.first(), favourite)
                    val discoveries = brain.rankTracks(candidates, "discover").take(30)
                    val releases = if (offline) emptyList() else YouTubeMusicService.fetchNewReleases(20)
                    _mixes.value =
                        listOf(
                            ListeningMix(
                                R.string.hm_daily_mix,
                                R.string.hm_daily_reason,
                                eligible(
                                    brain.rankTracks(brain.heavyRotationTracks(30) + discoveries, "daily_mix").take(30),
                                ),
                            ),
                            ListeningMix(R.string.hm_discover_mix, R.string.hm_discover_reason, discoveries),
                            ListeningMix(R.string.section_rediscover, R.string.hm_rediscover_reason, eligible(brain.rediscoverTracks(30))),
                            ListeningMix(R.string.hm_new_releases, R.string.hm_new_reason, releases),
                        )
                    loadedAt = System.currentTimeMillis()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    loadedAt = 0L
                } finally {
                    _busy.value = false
                }
            }
        }
    }

@Composable
internal fun ListeningMixes(
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    vm: ListeningMixesViewModel = hiltViewModel(),
) {
    val mixes by vm.mixes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.load() }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = flowBottomContentPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) item { Text(stringResource(R.string.hm_loading_sources), style = MaterialTheme.typography.bodyMedium) }
        if (!busy && mixes.isEmpty()) item { Text(stringResource(R.string.hm_mix_empty)) }
        mixes.forEach { mix ->
            item(key = mix.title) {
                Text(stringResource(mix.title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(mix.explanation), style = MaterialTheme.typography.bodySmall)
                if (mix.tracks.isEmpty()) Text(stringResource(R.string.hm_mix_empty), style = MaterialTheme.typography.bodyMedium)
            }
            items(mix.tracks, key = { "${mix.title}:${it.videoId}" }) { track ->
                ListItem(headlineContent = {
                    Text(track.title)
                }, supportingContent = { Text(track.artist) }, modifier = Modifier.clickable { onPlay(track, mix.tracks) })
            }
        }
    }
}
