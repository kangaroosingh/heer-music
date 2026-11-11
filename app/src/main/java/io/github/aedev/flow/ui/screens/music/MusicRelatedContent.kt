package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class MusicRelatedContent(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<MusicPlayerUiState>,
) {
    private var fetchedFor: String? = null
    private var request: kotlinx.coroutines.Job? = null

    fun fetch(id: String) {
        if (fetchedFor == id) return
        request?.cancel()
        if (LocalMediaIds.isLocal(id)) {
            fetchedFor = id
            state.update { it.copy(relatedContent = emptyList(), isRelatedLoading = false) }
            return
        }
        fetchedFor = id
        request =
            scope.launch(PerformanceDispatcher.networkIO) {
                state.update { it.copy(isRelatedLoading = true) }
                try {
                    val tracks = withTimeoutOrNull(10_000) { YouTubeMusicService.getRelatedMusic(id, 20) }.orEmpty()
                    if (fetchedFor == id) {
                        if (tracks.isEmpty()) fetchedFor = null
                        state.update { it.copy(relatedContent = tracks, isRelatedLoading = false) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (fetchedFor == id) {
                        fetchedFor = null
                        state.update { it.copy(isRelatedLoading = false) }
                    }
                }
            }
    }
}
