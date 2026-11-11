package io.github.aedev.flow.ui.screens.music

import androidx.media3.common.Player
import io.github.aedev.flow.player.EnhancedMusicPlayerManager

internal object MusicLoadedPlayback {
    fun matches(videoId: String): Boolean {
        val player = EnhancedMusicPlayerManager.player ?: return false
        return EnhancedMusicPlayerManager.currentTrack.value?.videoId == videoId &&
            player.playbackState in setOf(Player.STATE_READY, Player.STATE_BUFFERING)
    }
}
