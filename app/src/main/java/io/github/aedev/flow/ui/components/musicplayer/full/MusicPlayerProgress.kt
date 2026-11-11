package io.github.aedev.flow.ui.components.musicplayer.full

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.listening.ListeningIds
import io.github.aedev.flow.ui.components.musicplayer.controls.PlayerProgressSlider

@Composable
internal fun MusicPlayerProgress(
    id: String,
    position: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    playing: Boolean,
    modifier: Modifier,
) {
    if (ListeningIds.isRadio(id)) {
        Text(stringResource(R.string.hm_live), modifier, style = MaterialTheme.typography.labelLarge)
    } else {
        PlayerProgressSlider(positionProvider = position, duration = duration, onSeekTo = onSeek, isPlaying = playing, modifier = modifier)
    }
}
