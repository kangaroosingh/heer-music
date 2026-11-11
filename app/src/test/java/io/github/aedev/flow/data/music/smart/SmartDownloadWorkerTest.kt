package io.github.aedev.flow.data.music.smart

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.profile.RunningProfile
import io.github.aedev.flow.data.video.downloader.work.DownloadPowerPolicy
import io.github.aedev.flow.data.video.downloader.work.DownloadQueueWorker
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SmartDownloadWorkerTest {
    private val context = mockk<Context>(relaxed = true)
    private val work = mockk<WorkManager>(relaxed = true)
    private val store = mockk<SmartDownloadStore>()
    private val power = mockk<DownloadPowerPolicy>()
    private val charging = MutableStateFlow(false)
    private val preferences = mockk<PlayerPreferences>()
    private val state = MutableStateFlow(SmartDownloadSettings(networkConfigured = true))
    private val wifi = MutableStateFlow(true)

    @Before fun setup() {
        mockkObject(WorkManager.Companion)
        mockkObject(ActiveProfile)
        every { WorkManager.getInstance(context) } returns work
        every { ActiveProfile.get(context) } returns RunningProfile("", true)
        every { power.chargingOnly } returns charging
        every { store.state } returns state
        every { preferences.downloadOverWifiOnly } returns wifi
        val running = mockk<WorkInfo>()
        every { running.state } returns WorkInfo.State.RUNNING
        every { work.getWorkInfosForUniqueWorkFlow(SmartDownloadWorker.ONCE) } returns flowOf(listOf(running))
    }

    @After fun cleanup() = unmockkAll()

    @Test fun `an enabled app restore does not cancel the job that started its process`() =
        runTest {
            state.value = state.value.copy(enabled = true)
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { SmartDownloadWorker.follow(context, store, preferences, power) }
            runCurrent()
            verify(exactly = 0) { work.cancelUniqueWork(SmartDownloadWorker.ONCE) }
            verify(exactly = 0) { work.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
            verify(exactly = 1) { work.enqueueUniquePeriodicWork(SmartDownloadWorker.PERIODIC, any(), any()) }
        }

    @Test fun `enabling creates one immediate check and a delayed periodic check with the selected network`() =
        runTest {
            val periodic = slot<PeriodicWorkRequest>()
            val immediate = slot<OneTimeWorkRequest>()
            every { work.enqueueUniquePeriodicWork(any(), any(), capture(periodic)) } returns mockk(relaxed = true)
            every { work.enqueueUniqueWork(any(), any(), capture(immediate)) } returns mockk(relaxed = true)
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { SmartDownloadWorker.follow(context, store, preferences, power) }
            runCurrent()
            state.value = state.value.copy(enabled = true)
            runCurrent()
            assertThat(periodic.captured.workSpec.initialDelay).isEqualTo(TimeUnit.HOURS.toMillis(12))
            assertThat(immediate.captured.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
            verify(exactly = 1) { work.enqueueUniqueWork(SmartDownloadWorker.ONCE, ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>()) }
        }

    @Test fun `restoring enabled settings with no previous check repairs the missing immediate job`() =
        runTest {
            state.value = state.value.copy(enabled = true)
            every { work.getWorkInfosForUniqueWorkFlow(SmartDownloadWorker.ONCE) } returns flowOf(emptyList())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                SmartDownloadWorker.follow(context, store, preferences, power)
            }
            runCurrent()
            verify(exactly = 1) { work.enqueueUniqueWork(SmartDownloadWorker.ONCE, ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>()) }
        }

    @Test fun `any network is an explicit connected network request`() {
        val immediate = slot<OneTimeWorkRequest>()
        every { work.enqueueUniqueWork(any(), any(), capture(immediate)) } returns mockk(relaxed = true)
        SmartDownloadWorker.checkNow(context, wifiOnly = false, chargingOnly = false)
        assertThat(immediate.captured.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test fun `turning off cancels selectors but never the actual download queue`() =
        runTest {
            state.value = state.value.copy(enabled = true)
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { SmartDownloadWorker.follow(context, store, preferences, power) }
            runCurrent()
            state.value = state.value.copy(enabled = false)
            runCurrent()
            verify(exactly = 1) { work.cancelUniqueWork(SmartDownloadWorker.ONCE) }
            verify(exactly = 1) { work.cancelUniqueWork(SmartDownloadWorker.PERIODIC) }
            verify(exactly = 0) { work.cancelUniqueWork(DownloadQueueWorker.UNIQUE_NAME) }
        }

    @Test fun `history and status updates do not create repeated work`() =
        runTest {
            state.value = state.value.copy(enabled = true)
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) { SmartDownloadWorker.follow(context, store, preferences, power) }
            runCurrent()
            state.value = state.value.copy(lastRunAt = 123, lastResult = SmartDownloadResult.QUEUED)
            runCurrent()
            verify(exactly = 1) { work.enqueueUniquePeriodicWork(SmartDownloadWorker.PERIODIC, any(), any()) }
            verify(exactly = 0) { work.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
        }

    @Test fun `charging only also constrains suggestion work`() {
        val immediate = slot<OneTimeWorkRequest>()
        every { work.enqueueUniqueWork(any(), any(), capture(immediate)) } returns mockk(relaxed = true)
        SmartDownloadWorker.checkNow(context, wifiOnly = true, chargingOnly = true)
        assertThat(
            immediate.captured.workSpec.constraints
                .requiresCharging(),
        ).isTrue()
        assertThat(immediate.captured.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
    }
}
