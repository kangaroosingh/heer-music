package io.github.aedev.flow.musicfork

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.EntryPointAccessors
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.music.smart.SmartDownloadWorker
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.video.downloader.work.DownloadPowerPolicy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SmartDownloadsSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store get() = EntryPointAccessors.fromApplication(context, SmartDownloadWorker.Dependencies::class.java).store()

    private fun click(label: String): Boolean {
        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) {
                var target: AccessibilityNodeInfo? = node
                repeat(4) {
                    if (target?.isClickable == true && target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    target = target?.parent
                }
            }
            repeat(node.childCount) { if (visit(node.getChild(it))) return true }
            return false
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun scroll(forward: Boolean): Boolean {
        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isScrollable &&
                node.performAction(
                    if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                )
            ) {
                return true
            }
            repeat(node.childCount) { if (visit(node.getChild(it))) return true }
            return false
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun awaitClick(label: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
        repeat(3) {
            scroll(false)
            Thread.sleep(100)
        }
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (click(label)) return
            scroll(true)
            Thread.sleep(300)
        }
        fail("Control missing: $label")
    }

    @Test fun networkAndGbBudgetCanBeChosenWithoutEnablingDownloads() =
        runBlocking {
            val original = store.state.first()
            val preferences = PlayerPreferences(context)
            val originalWifi = preferences.downloadOverWifiOnly.first()
            val power = DownloadPowerPolicy(context)
            val originalCharging = power.chargingOnly.first()
            val host = FlowMusicSmokeTest()
            val activity = host.activity()
            try {
                val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
                var opened = false
                while (!opened && android.os.SystemClock.elapsedRealtime() < deadline) {
                    opened = click("Smart Downloads")
                    if (!opened) click("Your music taste")
                    Thread.sleep(200)
                }
                assertTrue("Smart Downloads settings must open", opened)
                awaitClick("Wi-Fi / unmetered")
                withTimeout(15_000) { store.state.first { it.networkConfigured } }
                assertTrue(preferences.downloadOverWifiOnly.first())
                awaitClick("Any network")
                withTimeout(15_000) { preferences.downloadOverWifiOnly.first { !it } }
                awaitClick("Only download while charging")
                withTimeout(15_000) { power.chargingOnly.first { it != originalCharging } }
                awaitClick("2.0 GB")
                withTimeout(15_000) { store.state.first { it.maxStorageBytes == 2_000_000_000L } }
                assertFalse(store.state.first().enabled)
                Thread.sleep(500)
                host.screenshot("heer-music-smart-downloads.png")
            } finally {
                store.update { original }
                preferences.setDownloadOverWifiOnly(originalWifi)
                power.setChargingOnly(originalCharging)
                instrumentation.runOnMainSync { activity.finish() }
            }
        }

    @Test fun disabledWorkerConstructsRealDependenciesWithoutDownloading() =
        runBlocking {
            val original = store.state.first()
            val work = WorkManager.getInstance(context)
            val name = "smart-smoke-${UUID.randomUUID()}"
            try {
                store.update { it.copy(enabled = false) }
                val request =
                    OneTimeWorkRequestBuilder<SmartDownloadWorker>()
                        .setInputData(workDataOf("profile" to ActiveProfile.get(context).id))
                        .build()
                work.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get(20, TimeUnit.SECONDS)
                val result =
                    withTimeout(60_000) {
                        work.getWorkInfoByIdFlow(request.id).filterNotNull().first { it.state.isFinished }
                    }
                assertEquals(WorkInfo.State.SUCCEEDED, result.state)
                assertFalse(store.state.first().enabled)
            } finally {
                work.cancelUniqueWork(name)
                store.update { original }
            }
        }

    @Test fun settingsPersistAcrossRepositoryReopen() =
        runBlocking {
            val original = store.state.first()
            try {
                store.update { it.copy(networkConfigured = true, maxSongs = 50) }
                val reopened =
                    io.github.aedev.flow.data.music.smart
                        .SmartDownloadStore(context)
                        .state
                        .first()
                assertTrue(reopened.networkConfigured)
                assertEquals(50, reopened.maxSongs)
            } finally {
                store.update { original }
            }
        }

    @Test fun enabledWorkerDownloadsOnePersonalSuggestionThroughTheNativeQueue() =
        runBlocking {
            val original = store.state.first()
            val preferences = PlayerPreferences(context)
            val originalWifi = preferences.downloadOverWifiOnly.first()
            val power = DownloadPowerPolicy(context)
            val originalCharging = power.chargingOnly.first()
            val dependencies = EntryPointAccessors.fromApplication(context, MusicSmokeDependencies::class.java)
            val personalization = dependencies.personalization()
            val originalTaste = personalization.profile.first()
            val dao =
                io.github.aedev.flow.data.local.AppDatabase
                    .profile(context)
                    .downloadDao()
            val library =
                io.github.aedev.flow.data.music
                    .PlaylistRepository(context)
            val song =
                io.github.aedev.flow.data.music.model
                    .MusicTrack("jNQXAC9IVRw", "Automatic download fixture", "Test clip", "", 19)
            val originalFavorite = library.favorites.first().any { it.videoId == song.videoId }
            val slots = (1..9).map { "smart-slot-${UUID.randomUUID()}-$it" }
            val host = FlowMusicSmokeTest()
            val activity = host.activity()
            try {
                store.update { it.copy(enabled = false) }
                dependencies.downloads().deleteDownload(song.videoId)
                library.addToFavorites(song)
                personalization.update { it.copy(enabled = false) }
                preferences.setDownloadOverWifiOnly(true)
                power.setChargingOnly(false)
                for (id in slots) {
                    val request =
                        io.github.aedev.flow.data.video.downloader.request.DownloadRequest(
                            tags =
                                io.github.aedev.flow.data.video.downloader.tags.DownloadTags(
                                    kind = io.github.aedev.flow.data.video.downloader.tags.DownloadKind.MUSIC,
                                    videoId = id,
                                    title = "Reserved fixture slot",
                                ),
                            smartDownload = true,
                            smartEstimatedBytes = 60_000_000L,
                        )
                    val row =
                        io.github.aedev.flow.data.local.entity.DownloadEntity(
                            videoId = id,
                            title = "Reserved fixture slot",
                            uploader = "Fixture",
                            requestJson = request.encode(),
                        )
                    val item =
                        io.github.aedev.flow.data.local.entity.DownloadItemEntity(
                            videoId = id,
                            fileType = io.github.aedev.flow.data.local.entity.DownloadFileType.AUDIO,
                            fileName = "$id.pending",
                            filePath = java.io.File(context.cacheDir, "$id.pending").path,
                            status = io.github.aedev.flow.data.local.entity.DownloadItemStatus.FAILED,
                        )
                    dao.replaceDownload(row, listOf(item))
                }
                store.update {
                    it.copy(
                        enabled = true,
                        networkConfigured = true,
                        maxSongs = 10,
                        maxStorageBytes = 550_000_000L,
                        lastRunAt = 0,
                    )
                }
                val completed =
                    withTimeout(180_000) {
                        dao.getDownloadWithItemsFlow(song.videoId).filterNotNull().first {
                            it.overallStatus == io.github.aedev.flow.data.local.entity.DownloadItemStatus.COMPLETED
                        }
                    }
                val request =
                    io.github.aedev.flow.data.video.downloader.request.DownloadRequest
                        .decode(completed.download.requestJson)
                assertTrue(request?.smartDownload == true)
                assertEquals(1, request?.threads)
                assertNotNull(dependencies.downloads().getDownloadedTrackPath(song.videoId))
                assertEquals(
                    10,
                    dao.getAllDownloadsWithItemsOnce().count {
                        io.github.aedev.flow.data.video.downloader.request.DownloadRequest
                            .decode(it.download.requestJson)
                            ?.smartDownload == true
                    },
                )
            } finally {
                store.update { it.copy(enabled = false) }
                val work = WorkManager.getInstance(context)
                work.cancelUniqueWork(SmartDownloadWorker.ONCE).result.get(20, TimeUnit.SECONDS)
                work.cancelUniqueWork(SmartDownloadWorker.PERIODIC).result.get(20, TimeUnit.SECONDS)
                dependencies.downloads().deleteDownload(song.videoId)
                slots.forEach { dao.deleteDownload(it) }
                if (!originalFavorite) library.removeFromFavorites(song.videoId)
                personalization.update { originalTaste }
                preferences.setDownloadOverWifiOnly(originalWifi)
                power.setChargingOnly(originalCharging)
                store.update { original }
                instrumentation.runOnMainSync { activity.finish() }
            }
        }
}
