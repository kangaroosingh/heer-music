package io.github.aedev.flow.ui.components.musicplayer.full

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.layout.LocalFlowSnackbarHostState
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicTasteViewModel
import kotlinx.coroutines.launch

internal data class MusicPlayerFeedback(
    val disliked: Boolean,
    val onLike: () -> Unit,
    val onDislike: () -> Unit,
)

@Composable
internal fun musicPlayerFeedback(
    track: MusicTrack,
    player: MusicPlayerViewModel,
): MusicPlayerFeedback {
    val taste: MusicTasteViewModel = hiltViewModel()
    val profile by taste.profile.collectAsStateWithLifecycle()
    val disliked = track.videoId in profile?.disliked.orEmpty()
    val snackbar = LocalFlowSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val undo = stringResource(R.string.undo)
    val message = stringResource(R.string.hm_disliked_undo)
    return MusicPlayerFeedback(disliked, onLike = {
        if (disliked) taste.undo(track.videoId)
        player.toggleLike()
    }, onDislike = {
        if (disliked) {
            taste.undo(track.videoId)
        } else {
            val feedback = player.dislikeAndSkip(track)
            scope.launch {
                feedback.join()
                if (snackbar?.showSnackbar(message, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                    taste.undo(track.videoId)
                }
            }
        }
    })
}
