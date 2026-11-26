package io.github.aedev.flow.ui.screens.listening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.listening.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject

@HiltViewModel
internal class SoundCloudViewModel
    @Inject
    constructor(
        private val store: ListeningStore,
    ) : ViewModel() {
        val mixes = store.state.map { it.soundCloudMixes }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        val busy = MutableStateFlow(false)
        val invalid = MutableStateFlow(false)
        val offline = store.state.map { it.downloadedOnly }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

        fun add(raw: String) {
            if (busy.value) return
            val url = SoundCloudLinks.canonical(raw)
            invalid.value = url == null
            if (url == null) return
            busy.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val fallback = url.substringAfterLast('/').replace('-', ' ')
                    // Reuse NewPipe's maintained public-service parser; no copied app or private API credentials.
                    val title =
                        if (store.state.first().downloadedOnly) {
                            fallback
                        } else {
                            try {
                                withTimeoutOrNull(12_000) {
                                    if ("/sets/" in url) {
                                        ServiceList.SoundCloud
                                            .getPlaylistExtractor(url)
                                            .apply { fetchPage() }
                                            .name
                                    } else {
                                        ServiceList.SoundCloud
                                            .getStreamExtractor(url)
                                            .apply { fetchPage() }
                                            .name
                                    }
                                }?.takeIf(String::isNotBlank) ?: fallback
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                fallback
                            }
                        }
                    store.update { state ->
                        state.copy(
                            soundCloudMixes = (state.soundCloudMixes.filterNot { it.url == url } + SoundCloudMix(url, title)).takeLast(100),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    invalid.value = true
                } finally {
                    busy.value = false
                }
            }
        }

        fun remove(url: String) {
            viewModelScope.launch { store.update { it.copy(soundCloudMixes = it.soundCloudMixes.filterNot { mix -> mix.url == url }) } }
        }
    }
