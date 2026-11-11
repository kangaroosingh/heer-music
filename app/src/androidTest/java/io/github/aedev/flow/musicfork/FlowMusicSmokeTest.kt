package io.github.aedev.flow.musicfork

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.FlowApplication
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.service.Media3MusicService
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FlowMusicSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun shell(command: String) {
        android.os.ParcelFileDescriptor
            .AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command),
            ).use { it.readBytes() }
    }

    private fun browser(): MediaBrowser {
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaBrowser>
        instrumentation.runOnMainSync {
            future =
                MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, Media3MusicService::class.java))).buildAsync()
        }
        return future.get(60, TimeUnit.SECONDS)
    }

    internal fun activity(): MainActivity {
        assertTrue(context.applicationContext is FlowApplication)
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        val activity =
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                checkNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 30_000) as? MainActivity) {
                    "Music activity did not start"
                }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        instrumentation.runOnMainSync {
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            }
        }
        return activity
    }

    internal fun screenshot(name: String) {
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun carBrowserStartsBeforeAnyPhoneScreen() {
        val player = browser()
        try {
            lateinit var root: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.LibraryResult<MediaItem>>
            instrumentation.runOnMainSync { root = player.getLibraryRoot(null) }
            val result = root.get(30, TimeUnit.SECONDS)
            assertEquals(0, result.resultCode)
            assertEquals(true, result.value?.mediaMetadata?.isBrowsable)
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }

    @Test fun generatedOfflineAudioContinuesWithScreenOff() {
        val activity = activity()
        val file = File(context.cacheDir, "music-offline-smoke.wav")
        val bytes = 22_050 * 45 * 2
        val buffer = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
        buffer
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(22_050)
            .putInt(44_100)
            .putShort(2)
            .putShort(16)
        buffer.put("data".toByteArray()).putInt(bytes)
        file.writeBytes(buffer.array())
        val player = browser()
        try {
            instrumentation.runOnMainSync {
                player.setMediaItem(
                    MediaItem
                        .Builder()
                        .setMediaId("local_999001")
                        .setUri(android.net.Uri.fromFile(file))
                        .setMediaMetadata(
                            MediaMetadata
                                .Builder()
                                .setTitle("Generated offline test")
                                .setArtist("Test audio")
                                .build(),
                        ).build(),
                )
                player.prepare()
                player.play()
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
            var playing = false
            while (!playing && android.os.SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { playing = player.isPlaying }
                Thread.sleep(200)
            }
            assertTrue("Local audio must play", playing)
            var before = 0L
            instrumentation.runOnMainSync { before = player.currentPosition }
            instrumentation.runOnMainSync {
                activity.setTurnScreenOn(false)
                activity.window.clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                )
            }
            shell("svc power stayon false")
            shell("input keyevent 223")
            Thread.sleep(4_000)
            assertFalse(context.getSystemService(PowerManager::class.java).isInteractive)
            instrumentation.runOnMainSync {
                assertTrue(player.isPlaying)
                assertTrue(player.currentPosition - before > 2_500)
            }
        } finally {
            shell("input keyevent 224")
            instrumentation.runOnMainSync {
                player.stop()
                player.clearMediaItems()
                player.release()
                activity.finish()
            }
            file.delete()
        }
    }

    @Test fun nativeDownloadPlaysInAirplaneMode() {
        val activity = activity()
        lateinit var vm: MusicPlayerViewModel
        instrumentation.runOnMainSync { vm = ViewModelProvider(activity)[MusicPlayerViewModel::class.java] }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val collection = scope.launch { vm.uiState.collect { } }
        val sample = MusicTrack("jNQXAC9IVRw", "Download test clip", "Test clip", "", 19)
        val player = browser()
        try {
            instrumentation.runOnMainSync { vm.downloadTrack(sample) }
            val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
            var completed = false
            while (!completed && android.os.SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { completed = sample.videoId in vm.uiState.value.downloadedTrackIds }
                Thread.sleep(1_000)
            }
            assertTrue("Native download must complete before offline test", completed)
            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable")
            shell("svc data disable")
            val network = context.getSystemService(android.net.ConnectivityManager::class.java)
            val disconnectDeadline = android.os.SystemClock.elapsedRealtime() + 30_000
            while (network.activeNetwork != null && android.os.SystemClock.elapsedRealtime() < disconnectDeadline) {
                Thread.sleep(200)
            }
            assertNull("Airplane-mode test must disconnect the network", network.activeNetwork)
            val dependencies =
                dagger.hilt.android.EntryPointAccessors
                    .fromApplication(context, MusicSmokeDependencies::class.java)
            val offlineCandidates = runBlocking { dependencies.personalization().augment(emptyList(), "quick_picks", emptyList()) }
            assertTrue("Offline recommendations must reuse completed downloads", offlineCandidates.any { it.videoId == sample.videoId })
            instrumentation.runOnMainSync { vm.loadAndPlayTrack(sample, listOf(sample)) }
            val playDeadline = android.os.SystemClock.elapsedRealtime() + 30_000
            var playing = false
            while (!playing && android.os.SystemClock.elapsedRealtime() < playDeadline) {
                instrumentation.runOnMainSync { playing = player.isPlaying && player.currentMediaItem?.mediaId == sample.videoId }
                Thread.sleep(200)
            }
            assertTrue("Completed download must play with network disabled", playing)
            screenshot("flow-music-offline.png")
        } finally {
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable")
            shell("svc data enable")
            collection.cancel()
            scope.cancel()
            instrumentation.runOnMainSync {
                player.stop()
                player.clearMediaItems()
                player.release()
                activity.finish()
            }
        }
    }

    private fun visibleText(): String {
        fun visit(node: android.view.accessibility.AccessibilityNodeInfo?): String {
            if (node == null) return ""
            return buildString {
                append(node.text).append(' ').append(node.contentDescription).append(' ')
                repeat(node.childCount) { append(visit(node.getChild(it))) }
            }
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    @Test fun tasteOnboardingAndMusicTabsRender() {
        val activity = activity()
        try {
            val deadline = android.os.SystemClock.elapsedRealtime() + 40_000
            while (!visibleText().contains("Punjabi") && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(500)
            }
            assertTrue("Punjabi-first taste onboarding must be visible: ${visibleText()}", visibleText().contains("Punjabi"))
            screenshot("flow-music-taste.png")
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(2_000)
            val text = visibleText()
            listOf("Home", "Search", "Library", "Downloads").forEach { assertTrue("Missing music tab $it: $text", text.contains(it)) }
            screenshot("flow-music-home.png")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
