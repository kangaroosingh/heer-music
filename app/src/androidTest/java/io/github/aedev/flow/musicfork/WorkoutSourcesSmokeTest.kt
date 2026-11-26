package io.github.aedev.flow.musicfork

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import io.github.aedev.flow.R
import io.github.aedev.flow.data.listening.ListeningIds
import io.github.aedev.flow.data.listening.WorkoutRequest
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.screens.listening.SoundCloudViewModel
import io.github.aedev.flow.ui.screens.listening.WorkoutViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class WorkoutSourcesSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val deps get() = EntryPointAccessors.fromApplication(context, MusicSmokeDependencies::class.java)

    private fun waitFor(
        message: String,
        timeout: Long = 60_000,
        check: () -> Boolean,
    ) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            if (check()) return
            Thread.sleep(200)
        }
        FlowMusicSmokeTest().screenshot("heer-workout-failure.png")
        fail(message)
    }

    private fun node(match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()

        fun visit(current: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (current == null) return null
            if (current.isVisibleToUser && match(current)) return current
            repeat(current.childCount) { visit(current.getChild(it))?.let { found -> return found } }
            return null
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun click(label: String): Boolean {
        var target = node { it.text?.toString() == label || it.contentDescription?.toString() == label }
        repeat(6) {
            if (target?.isClickable == true && target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
            target = target?.parent
        }
        return false
    }

    private fun scroll(horizontal: Boolean) {
        node {
            val bounds = Rect().apply { it.getBoundsInScreen(this) }
            it.isScrollable && (bounds.height() < 200) == horizontal
        }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    @Test fun repeatedPlayNextMovesOneCopyAndKeepsAudioRunning() {
        val host = FlowMusicSmokeTest()
        val activity = host.activity()
        val files = (0..2).map { File(context.cacheDir, "playlists/workout-native-$it.wav") }
        val bytes = 22_050 * 45 * 2
        val wav = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        wav
            .put("RIFF".toByteArray())
            .putInt(36 + bytes)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(22_050)
            .putInt(44_100)
            .putShort(2)
            .putShort(16)
            .put("data".toByteArray())
            .putInt(bytes)
        val tracks =
            files.mapIndexed { index, file ->
                file.parentFile?.mkdirs()
                file.writeBytes(wav.array())
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                MusicTrack(ListeningIds.of("book", uri.toString()), "Native queue $index", "Heer test", "", 45)
            }
        try {
            instrumentation.runOnMainSync {
                EnhancedMusicPlayerManager.initialize(context, deps.downloads()::isDownloaded)
                EnhancedMusicPlayerManager.playTrack(tracks.first(), "", tracks)
            }
            waitFor("Initial fixture did not play") { EnhancedMusicPlayerManager.playerState.value.isPlaying }
            val before = EnhancedMusicPlayerManager.currentPosition.value
            instrumentation.runOnMainSync {
                repeat(3) { EnhancedMusicPlayerManager.playNext(tracks.last()) }
                assertEquals(
                    listOf(tracks[0].videoId, tracks[2].videoId, tracks[1].videoId),
                    EnhancedMusicPlayerManager.queue.value.map { it.videoId },
                )
                assertEquals(3, EnhancedMusicPlayerManager.playerInstance.value?.mediaItemCount)
                assertEquals(tracks.first().videoId, EnhancedMusicPlayerManager.currentTrack.value?.videoId)
            }
            Thread.sleep(1200)
            assertTrue(EnhancedMusicPlayerManager.currentPosition.value > before)
            instrumentation.runOnMainSync { EnhancedMusicPlayerManager.playNext() }
            waitFor("Next advanced to the wrong track") {
                EnhancedMusicPlayerManager.currentTrack.value?.videoId == tracks.last().videoId &&
                    EnhancedMusicPlayerManager.playerState.value.isPlaying
            }
        } finally {
            instrumentation.runOnMainSync {
                EnhancedMusicPlayerManager.pause()
                activity.finish()
            }
            files.forEach(File::delete)
        }
    }

    @Test fun realWorkoutGenerationSavesAndIsAvailableToCar() =
        runBlocking {
            val original = deps.listening().state.first()
            val activity = FlowMusicSmokeTest().activity()
            val name = "Heer native workout fixture"
            try {
                deps.listening().update { it.copy(downloadedOnly = false) }
                lateinit var vm: WorkoutViewModel
                instrumentation.runOnMainSync {
                    vm = ViewModelProvider(activity)[WorkoutViewModel::class.java]
                    vm.request(WorkoutRequest(minutes = 30))
                    vm.generate()
                }
                waitFor("Workout generation did not finish", 90_000) { !vm.state.value.busy }
                assertFalse(
                    "Real Punjabi workout search returned no eligible tracks",
                    vm.state.value.tracks
                        .isEmpty(),
                )
                assertTrue(
                    vm.state.value.tracks
                        .sumOf { it.duration } <= 1800,
                )
                assertEquals(
                    vm.state.value.tracks.size,
                    vm.state.value.tracks
                        .map { it.videoId }
                        .distinct()
                        .size,
                )
                instrumentation.runOnMainSync { vm.save(name, "Native fixture") }
                waitFor("Workout was not saved") { vm.state.value.saved }
                val playlist =
                    deps
                        .libraryPlaylists()
                        .getMusicPlaylistsFlow()
                        .first()
                        .first { it.name == name }
                val ids =
                    vm.state.value.tracks
                        .map { it.videoId }
                assertEquals(
                    ids,
                    deps
                        .libraryPlaylists()
                        .getPlaylistVideosFlow(playlist.id)
                        .first()
                        .map { it.id },
                )
                assertTrue(deps.autoLibrary().playlists().any { it.id == playlist.id })
                assertEquals(
                    ids,
                    deps
                        .autoLibrary()
                        .tracks("auto:playlist:${playlist.id}")
                        .orEmpty()
                        .map { it.videoId },
                )
            } finally {
                deps
                    .libraryPlaylists()
                    .getMusicPlaylistsFlow()
                    .first()
                    .filter { it.name == name }
                    .forEach { deps.libraryPlaylists().deletePlaylist(it.id) }
                deps.listening().update { original }
                instrumentation.runOnMainSync { activity.finish() }
            }
        }

    @Test fun publicSoundCloudMixPersistsAndFreePlayerOpens() =
        runBlocking {
            val original = deps.listening().state.first()
            val taste = deps.personalization().profile.first()
            deps.personalization().update { it.copy(setupComplete = true) }
            val host = FlowMusicSmokeTest()
            val activity = host.activity()
            try {
                deps.listening().update { it.copy(downloadedOnly = false, soundCloudMixes = emptyList()) }
                lateinit var vm: SoundCloudViewModel
                instrumentation.runOnMainSync {
                    vm = ViewModelProvider(activity)[SoundCloudViewModel::class.java]
                    vm.add("https://soundcloud.com/forss/flickermood?si=fixture")
                }
                waitFor("SoundCloud save did not finish") { !vm.busy.value }
                assertEquals(
                    "https://soundcloud.com/forss/flickermood",
                    deps
                        .listening()
                        .state
                        .first()
                        .soundCloudMixes
                        .single()
                        .url,
                )
                waitFor("Listen tab unavailable") { click(context.getString(R.string.hm_listen)) }
                waitFor("SoundCloud tab unavailable") {
                    if (click(context.getString(R.string.hm_soundcloud))) {
                        true
                    } else {
                        scroll(true)
                        false
                    }
                }
                waitFor("SoundCloud saved mix unavailable") {
                    if (click(context.getString(R.string.hm_soundcloud_play))) {
                        true
                    } else {
                        scroll(false)
                        false
                    }
                }
                waitFor("Official SoundCloud WebView missing") { node { it.className?.toString() == "android.webkit.WebView" } != null }
                waitFor("SoundCloud track did not render") {
                    fun rendered(current: AccessibilityNodeInfo?): Boolean {
                        if (current == null) return false
                        if ((current.text?.toString().orEmpty() + current.contentDescription).contains(
                                "Flickermood",
                                ignoreCase = true,
                            )
                        ) {
                            return true
                        }
                        repeat(current.childCount) { if (rendered(current.getChild(it))) return true }
                        return false
                    }
                    rendered(node { it.className?.toString() == "android.webkit.WebView" })
                }
                delay(8000)
                host.screenshot("heer-soundcloud-widget.png")
                assertFalse(EnhancedMusicPlayerManager.playerState.value.isPlaying)
                assertTrue(click(context.getString(R.string.hm_soundcloud_close)))
                waitFor("Player did not close") { node { it.className?.toString() == "android.webkit.WebView" } == null }
            } finally {
                deps.listening().update { original }
                deps.personalization().update { taste }
                instrumentation.runOnMainSync { activity.finish() }
            }
        }
}
