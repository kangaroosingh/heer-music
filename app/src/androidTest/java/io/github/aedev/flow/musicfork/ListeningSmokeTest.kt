package io.github.aedev.flow.musicfork

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import io.github.aedev.flow.R
import io.github.aedev.flow.data.listening.ListeningEntry
import io.github.aedev.flow.data.listening.ListeningIds
import io.github.aedev.flow.data.listening.RadioCatalog
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class ListeningSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val deps get() =
        EntryPointAccessors.fromApplication(
            context,
            MusicSmokeDependencies::class.java,
        )

    private fun waitFor(
        message: String,
        timeout: Long = 45_000,
        check: () -> Boolean,
    ) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < until) {
            if (check()) return
            Thread.sleep(200)
        }

        FlowMusicSmokeTest().screenshot("heer-native-failure.png")
        fail(message)
    }

    private fun click(label: String): Boolean {
        instrumentation.uiAutomation.waitForIdle(400, 5_000)

        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null || !node.isVisibleToUser) return false
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) {
                var target: AccessibilityNodeInfo? = node
                repeat(6) {
                    if (target?.isClickable == true && target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
                    target = target?.parent
                }
            }

            repeat(node.childCount) {
                if (visit(node.getChild(it))) return true
            }

            return false
        }

        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun visible(label: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()

        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null || !node.isVisibleToUser) return false
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return true
            repeat(node.childCount) { if (visit(node.getChild(it))) return true }
            return false
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    @Test fun listenTabsAndCarLibraryAreReachable() =
        runBlocking {
            deps.personalization().update {
                it.copy(setupComplete = true)
            }

            val host = FlowMusicSmokeTest()
            val activity = host.activity()
            try {
                waitFor("Listen navigation missing") {
                    click(context.getString(R.string.hm_listen))
                }

                waitFor("Books tab missing") {
                    click(context.getString(R.string.hm_books))
                }

                waitFor("Book controls did not render") { visible(context.getString(R.string.hm_import_audio)) }
                host.screenshot("heer-books.png")
                waitFor("Podcasts tab missing") {
                    click(context.getString(R.string.hm_podcasts))
                }

                waitFor("Podcast controls did not render") { visible(context.getString(R.string.hm_refresh_feeds)) }
                host.screenshot("heer-podcasts.png")
                waitFor("Radio tab missing") {
                    click(context.getString(R.string.hm_radio))
                }

                waitFor("Radio controls did not render") { visible(context.getString(R.string.hm_vicemergency)) }
                host.screenshot("heer-radio.png")
                assertTrue(
                    deps
                        .autoLibrary()
                        .tracks("auto:radio")
                        .orEmpty()
                        .size >= 8,
                )
                assertNotNull(deps.autoLibrary().tracks("auto:books"))
                assertNotNull(deps.autoLibrary().tracks("auto:podcasts"))
            } finally {
                instrumentation.runOnMainSync {
                    activity.finish()
                }
            }
        }

    @Test fun liveHaanjiPlaysAndSurvivesScreenOff() =
        runBlocking {
            deps.listening().update {
                it.copy(downloadedOnly = false)
            }

            val activity = FlowMusicSmokeTest().activity()
            try {
                instrumentation.runOnMainSync {
                    val vm = ViewModelProvider(activity)[MusicPlayerViewModel::class.java]
                    vm.setEndlessRadioEnabled(false)
                    vm.loadAndPlayTrack(RadioCatalog.stations.first().track())
                }

                waitFor(
                    "Haanji did not start: ${EnhancedMusicPlayerManager.playerState.value}",
                    60_000,
                ) {
                    EnhancedMusicPlayerManager.playerState.value.isPlaying
                }

                val before = EnhancedMusicPlayerManager.currentPosition.value
                instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
                delay(6000)
                assertTrue(EnhancedMusicPlayerManager.playerState.value.isPlaying)
                assertTrue(EnhancedMusicPlayerManager.currentPosition.value > before)
            } finally {
                instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
                instrumentation.runOnMainSync {
                    EnhancedMusicPlayerManager.pause()
                    activity.finish()
                }
            }
        }

    @Test fun ownedAudioBookmarksAndOfflineCarLibraryPersist() =
        runBlocking {
            val original = deps.listening().state.first()
            val file =
                File(
                    context.cacheDir,
                    "playlists/owned-book.wav",
                ).apply {
                    parentFile?.mkdirs()
                }

            val bytes = 22_050 * 12 * 2
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
            file.writeBytes(wav.array())
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.files",
                    file,
                )
            val entry =
                ListeningEntry(
                    ListeningIds.of(
                        "book",
                        uri.toString(),
                    ),
                    "Owned audio fixture",
                    "Heer test",
                    duration = 12,
                )
            val activity = FlowMusicSmokeTest().activity()
            try {
                deps.listening().add(listOf(entry))
                deps.listening().update {
                    it.copy(
                        downloadedOnly = true,
                        bookmarks = it.bookmarks + (entry.id to 3000L),
                    )
                }

                assertTrue(
                    deps
                        .autoLibrary()
                        .tracks("auto:radio")
                        .orEmpty()
                        .isEmpty(),
                )
                assertTrue(
                    deps.autoLibrary().tracks("auto:books").orEmpty().any {
                        it.videoId == entry.id
                    },
                )
                assertEquals(
                    3000L,
                    deps
                        .listening()
                        .state
                        .first()
                        .bookmarks[entry.id],
                )
                assertEquals(3000L, deps.autoLibrary().resumePositionMs(entry.id))
                instrumentation.runOnMainSync {
                    ViewModelProvider(activity)[MusicPlayerViewModel::class.java].loadAndPlayTrack(entry.track())
                }

                waitFor("Owned book did not play offline") {
                    EnhancedMusicPlayerManager.playerState.value.isPlaying
                }

                waitFor("Book resume position missing") { EnhancedMusicPlayerManager.currentPosition.value >= 3000L }
            } finally {
                instrumentation.runOnMainSync {
                    EnhancedMusicPlayerManager.pause()
                    activity.finish()
                }

                deps.listening().update {
                    original
                }

                file.delete()
            }
        }

    @Test fun officialPodcastFeedLoadsEpisodes() =
        runBlocking {
            val episodes = deps.feeds().load("https://www.abc.net.au/radio/programs/conversations/feed/7711104/podcast.xml")
            assertTrue(episodes.isNotEmpty())
            assertTrue(
                episodes.all {
                    ListeningIds.kind(it.id) == "podcast"
                },
            )
        }
}
