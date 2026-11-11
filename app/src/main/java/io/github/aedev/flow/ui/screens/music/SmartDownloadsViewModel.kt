package io.github.aedev.flow.ui.screens.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.MusicAudioQuality
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.music.smart.SmartDownloadEnvironment
import io.github.aedev.flow.data.music.smart.SmartDownloadResult
import io.github.aedev.flow.data.music.smart.SmartDownloadSettings
import io.github.aedev.flow.data.music.smart.SmartDownloadStorage
import io.github.aedev.flow.data.music.smart.SmartDownloadStore
import io.github.aedev.flow.data.music.smart.SmartDownloadWorker
import io.github.aedev.flow.data.music.smart.isSmartDownload
import io.github.aedev.flow.data.video.downloader.work.DownloadController
import io.github.aedev.flow.data.video.downloader.work.DownloadPowerPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmartDownloadUiState(
    val settings: SmartDownloadSettings,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val used: Int,
    val ready: Int,
    val running: Boolean,
    val queued: Boolean,
    val usedBytes: Long,
    val reservedBytes: Long,
    val estimate: SmartDownloadStorage.Estimate,
    val blocker: Int?,
    val failed: Int,
)

@HiltViewModel
class SmartDownloadsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: SmartDownloadStore,
        private val preferences: PlayerPreferences,
        private val controller: DownloadController,
        dao: DownloadDao,
        private val power: DownloadPowerPolicy,
        private val environment: SmartDownloadEnvironment,
    ) : ViewModel() {
        private val work = WorkManager.getInstance(context)
        private val jobs =
            combine(
                work.getWorkInfosForUniqueWorkFlow(SmartDownloadWorker.PERIODIC),
                work.getWorkInfosForUniqueWorkFlow(SmartDownloadWorker.ONCE),
            ) { a, b ->
                (a + b).any { it.state == WorkInfo.State.RUNNING } to (a + b).any { it.state == WorkInfo.State.ENQUEUED }
            }.distinctUntilChanged()

        private data class Configuration(
            val settings: SmartDownloadSettings,
            val wifi: Boolean,
            val charging: Boolean,
            val quality: MusicAudioQuality,
        )

        private val configuration =
            combine(store.state, preferences.downloadOverWifiOnly, power.chargingOnly, preferences.musicDownloadQuality, ::Configuration)
        val state =
            combine(configuration, dao.getAllDownloadsWithItems(), jobs) { config, rows, job ->
                val automatic = rows.filter { it.isSmartDownload() }
                val blocker =
                    when {
                        !environment.networkAllowed(config.wifi) -> if (config.wifi) R.string.hm_wait_wifi else R.string.hm_wait_network

                        !environment.chargingAllowed(config.charging) -> R.string.hm_wait_charging

                        !environment.batteryAllowed() -> R.string.hm_wait_battery

                        environment.availableBytes() <
                            io.github.aedev.flow.data.music.smart.SmartDownloadPolicy.MIN_FREE_BYTES +
                            io.github.aedev.flow.data.music.smart.SmartDownloadPolicy.RESERVE_PER_SONG -> R.string.fm_smart_low_storage

                        else -> null
                    }
                SmartDownloadUiState(
                    config.settings,
                    config.wifi,
                    config.charging,
                    automatic.size,
                    automatic.count { it.overallStatus == DownloadItemStatus.COMPLETED },
                    job.first,
                    job.second,
                    SmartDownloadStorage.actualBytes(rows),
                    SmartDownloadStorage.reservedBytes(rows),
                    SmartDownloadStorage.estimate(config.settings.maxStorageBytes ?: SmartDownloadStorage.GB, rows, config.quality),
                    blocker,
                    automatic.count { it.overallStatus == DownloadItemStatus.FAILED },
                )
            }.distinctUntilChanged().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        fun enabled(value: Boolean) {
            viewModelScope.launch {
                store.update {
                    it.copy(
                        enabled = value && it.networkConfigured,
                        maxStorageBytes = if (value) it.maxStorageBytes ?: SmartDownloadStorage.GB else it.maxStorageBytes,
                        lastResult = if (value) SmartDownloadResult.WAITING else SmartDownloadResult.DISABLED,
                    )
                }
            }
        }

        fun network(wifiOnly: Boolean) {
            viewModelScope.launch {
                preferences.setDownloadOverWifiOnly(wifiOnly)
                controller.applyNetworkPolicy().join()
                store.update { it.copy(networkConfigured = true) }
            }
        }

        fun charging(value: Boolean) {
            viewModelScope.launch {
                power.setChargingOnly(value)
                controller.applyNetworkPolicy().join()
            }
        }

        fun budget(gigabytes: Float) {
            val bytes = (gigabytes.coerceIn(0.5f, 20f).toDouble() * SmartDownloadStorage.GB).toLong()
            viewModelScope.launch {
                store.update { it.copy(maxStorageBytes = bytes) }
                val settings = store.state.first()
                if (settings.enabled && settings.networkConfigured) {
                    SmartDownloadWorker.checkNow(context, preferences.downloadOverWifiOnly.first(), power.chargingOnly.first())
                }
            }
        }

        fun checkNow() {
            val current = state.value ?: return
            if (current.settings.enabled &&
                current.settings.networkConfigured
            ) {
                SmartDownloadWorker.checkNow(context, current.wifiOnly, current.chargingOnly)
            }
        }
    }
