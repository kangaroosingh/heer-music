package io.github.aedev.flow.musicfork

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicVideoSwitchViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeerMusicSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun click(label: String): Boolean {
        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null || !node.isVisibleToUser) return false
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
                var target: AccessibilityNodeInfo? = node
                repeat(5) {
                    if (target?.isClickable == true && target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
                    target = target?.parent
                }
            }
            repeat(node.childCount) { if (visit(node.getChild(it))) return true }
            return false
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun waitFor(
        message: String,
        timeout: Long = 30_000,
        check: () -> Boolean,
    ) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < until) {
            if (check()) return
            Thread.sleep(200)
        }
        fail(message)
    }

    @Test fun songVideoLikeAndDislikeWorkThroughTheRealPlayer() =
        runBlocking {
            val deps = EntryPointAccessors.fromApplication(context, MusicSmokeDependencies::class.java)
            val personal = deps.personalization()
            val original = personal.profile.first()
            val prefs = PlayerPreferences(context)
            val originalVideo = prefs.musicVideoSwitch.first()
            val clip = MusicTrack("jNQXAC9IVRw", "Heer player fixture", "Test clip", "", 19, isVideoSong = true)
            personal.update { it.copy(setupComplete = true, disliked = it.disliked - clip.videoId) }
            val host = FlowMusicSmokeTest()
            val activity = host.activity()
            lateinit var vm: MusicPlayerViewModel
            lateinit var video: MusicVideoSwitchViewModel
            try {
                deps.downloads().deleteDownload(clip.videoId)
                assertTrue(
                    context.applicationInfo
                        .loadLabel(context.packageManager)
                        .toString()
                        .contains("Heer Music"),
                )
                instrumentation.runOnMainSync {
                    vm = ViewModelProvider(activity)[MusicPlayerViewModel::class.java]
                    video = ViewModelProvider(activity)[MusicVideoSwitchViewModel::class.java]
                    vm.setEndlessRadioEnabled(false)
                    vm.loadAndPlayTrack(clip, listOf(clip))
                }
                waitFor("Music must become playable", 90_000) {
                    var ready = false
                    instrumentation.runOnMainSync {
                        ready =
                            EnhancedMusicPlayerManager.player?.playbackState == androidx.media3.common.Player.STATE_READY
                    }
                    ready
                }
                instrumentation.runOnMainSync {
                    vm.pause()
                    vm.seekTo(4_000)
                }
                waitFor("Player must expand") { click("Heer player fixture") }
                waitFor("Song/Video switch must be visible") { click(context.getString(R.string.music_player_mode_video)) }
                waitFor("Video must select a video track", 90_000) {
                    var selected = false
                    instrumentation.runOnMainSync {
                        selected =
                            EnhancedMusicPlayerManager.player?.currentTracks?.isTypeSelected(C.TRACK_TYPE_VIDEO) == true
                    }
                    selected
                }
                host.screenshot("heer-music-video.png")
                waitFor("Song tab must be selectable") { click(context.getString(R.string.music_player_mode_song)) }
                waitFor("Song mode must disable the video choice") { !deps.videoSwitch().videoMode.value }
                waitFor("Like button must be visible") { click(context.getString(R.string.hm_like_download)) }
                withTimeout(180_000) {
                    while (deps.downloads().getDownloadedTrackPath(clip.videoId) == null) delay(300)
                }
                host.screenshot("heer-music-player.png")
                waitFor("Dislike button must be visible") { click(context.getString(R.string.hm_dislike_remove)) }
                withTimeout(30_000) {
                    personal.profile.first { clip.videoId in it.disliked }
                    while (deps.downloads().getDownloadedTrackPath(clip.videoId) != null) delay(200)
                }
                assertFalse(vm.uiState.value.isLiked)
            } finally {
                personal.update { original }
                deps.downloads().deleteDownload(clip.videoId)
                prefs.setMusicVideoSwitch(originalVideo)
                instrumentation.runOnMainSync {
                    deps.videoSwitch().select(false)
                    EnhancedMusicPlayerManager.player?.stop()
                    EnhancedMusicPlayerManager.player?.clearMediaItems()
                    activity.finish()
                }
            }
        }
}
