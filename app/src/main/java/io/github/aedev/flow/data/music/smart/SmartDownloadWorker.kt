package io.github.aedev.flow.data.music.smart

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.video.downloader.work.DownloadPowerPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SmartDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun manager(): SmartDownloadManager

        fun store(): SmartDownloadStore
    }

    override suspend fun doWork(): Result {
        if (inputData.getString(PROFILE) != ActiveProfile.get(applicationContext).id) return Result.success()
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        return try {
            dependencies.manager().fill(inputData.getBoolean(MANUAL, false))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("SmartDownloads", "Automatic selection failed", e)
            runCatching { dependencies.store().update { it.copy(lastResult = SmartDownloadResult.ERROR) } }
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val PERIODIC = "flow_smart_downloads_periodic"
        const val ONCE = "flow_smart_downloads_once"
        private const val PROFILE = "profile"
        private const val MANUAL = "manual"

        private fun constraints(
            wifiOnly: Boolean,
            chargingOnly: Boolean,
        ) = Constraints
            .Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        suspend fun follow(
            context: Context,
            store: SmartDownloadStore,
            preferences: PlayerPreferences,
            power: DownloadPowerPolicy,
        ) {
            val work = WorkManager.getInstance(context)
            var previous: Triple<Boolean, Boolean, Boolean>? = null
            combine(store.state, preferences.downloadOverWifiOnly, power.chargingOnly) { state, wifi, charging ->
                Triple(state.enabled && state.networkConfigured, wifi, charging)
            }.distinctUntilChanged()
                .collect { (enabled, wifi, charging) ->
                    val configuration = Triple(enabled, wifi, charging)
                    val first = previous == null
                    val changed = previous != null && previous != configuration
                    previous = configuration
                    if (enabled) {
                        val request =
                            PeriodicWorkRequestBuilder<SmartDownloadWorker>(12, TimeUnit.HOURS)
                                .setInitialDelay(12, TimeUnit.HOURS)
                                .setConstraints(constraints(wifi, charging))
                                .setInputData(workDataOf(PROFILE to ActiveProfile.get(context).id))
                                .build()
                        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
                        val initialMissing =
                            first && store.state.first().lastRunAt == 0L &&
                                work.getWorkInfosForUniqueWorkFlow(ONCE).first().none { !it.state.isFinished }
                        if (changed || initialMissing) checkNow(context, wifi, charging)
                    } else {
                        work.cancelUniqueWork(ONCE)
                        work.cancelUniqueWork(PERIODIC)
                    }
                }
        }

        fun checkNow(
            context: Context,
            wifiOnly: Boolean,
            chargingOnly: Boolean,
        ) {
            val request =
                OneTimeWorkRequestBuilder<SmartDownloadWorker>()
                    .setConstraints(constraints(wifiOnly, chargingOnly))
                    .setInputData(workDataOf(PROFILE to ActiveProfile.get(context).id, MANUAL to true))
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
