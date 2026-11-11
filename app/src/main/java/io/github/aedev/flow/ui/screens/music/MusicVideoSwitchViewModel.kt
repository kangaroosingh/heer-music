package io.github.aedev.flow.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.musicvideo.MusicVideoSwitch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The music player's Song/Video switch, shown only when it is turned on in Settings. */
@HiltViewModel
class MusicVideoSwitchViewModel
    @Inject
    constructor(
        private val videoSwitch: MusicVideoSwitch,
        private val preferences: PlayerPreferences,
    ) : ViewModel() {
        val isEnabled: StateFlow<Boolean> =
            preferences.musicVideoSwitch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        val videoMode: StateFlow<Boolean> = videoSwitch.videoMode

        val isLoading: StateFlow<Boolean> = videoSwitch.isLoading

        fun select(video: Boolean) {
            viewModelScope.launch {
                preferences.setMusicVideoSwitch(true)
                videoSwitch.select(video)
            }
        }

        fun listenId(videoId: String): String = videoSwitch.listenId(videoId)

        fun songFor(videoId: String) = videoSwitch.songFor(videoId)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
