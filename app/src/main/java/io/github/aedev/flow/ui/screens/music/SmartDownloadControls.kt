package io.github.aedev.flow.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.smart.SmartDownloadPolicy
import io.github.aedev.flow.data.music.smart.SmartDownloadResult
import io.github.aedev.flow.data.music.smart.SmartDownloadStorage

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SmartDownloadControls(vm: SmartDownloadsViewModel) {
    val current by vm.state.collectAsStateWithLifecycle()
    val state = current ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.fm_smart_help), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.fm_smart_enabled), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = state.settings.enabled, onCheckedChange = vm::enabled, enabled = state.settings.networkConfigured)
        }
        var gigabytes by remember(state.settings.maxStorageBytes) {
            mutableFloatStateOf(
                (
                    state.settings.maxStorageBytes
                        ?.toFloat()
                        ?.div(SmartDownloadStorage.GB) ?: 1f
                ).coerceIn(0.5f, 20f),
            )
        }
        Text(stringResource(R.string.hm_budget_gb, gigabytes), style = MaterialTheme.typography.titleMedium)
        val budgetLabel = stringResource(R.string.hm_budget_gb, gigabytes)
        Slider(
            value = gigabytes,
            onValueChange = { gigabytes = it },
            valueRange = 0.5f..20f,
            steps = 38,
            onValueChangeFinished = { vm.budget(gigabytes) },
            modifier = Modifier.semantics { contentDescription = budgetLabel },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5f, 1f, 2f, 5f, 10f, 20f).forEach { gb ->
                FilterChip(
                    selected = state.settings.maxStorageBytes != null && gigabytes == gb,
                    onClick = {
                        gigabytes = gb
                        vm.budget(gb)
                    },
                    label = { Text(stringResource(R.string.hm_gb, gb)) },
                )
            }
        }
        val estimate = state.estimate
        val estimatedSongs =
            (
                gigabytes * SmartDownloadStorage.GB /
                    SmartDownloadStorage.estimateBytes(estimate.averageSeconds, estimate.bitsPerSecond)
            ).toInt()
        Text(
            stringResource(R.string.hm_budget_estimate, estimatedSongs, estimatedSongs * estimate.averageSeconds / 3600.0),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.hm_estimate_assumptions, estimate.bitsPerSecond / 1000, estimate.averageSeconds / 60f),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(stringResource(R.string.fm_smart_network), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.settings.networkConfigured && state.wifiOnly, onClick = {
                vm.network(true)
            }, label = { Text(stringResource(R.string.fm_smart_wifi)) })
            FilterChip(selected = state.settings.networkConfigured && !state.wifiOnly, onClick = {
                vm.network(false)
            }, label = { Text(stringResource(R.string.fm_smart_any)) })
        }
        Text(stringResource(R.string.fm_smart_network_help), style = MaterialTheme.typography.bodySmall)
        val chargingLabel = stringResource(R.string.fm_smart_charging)
        Row(Modifier.fillMaxWidth()) {
            Text(chargingLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.chargingOnly,
                onCheckedChange = vm::charging,
                modifier = Modifier.semantics { contentDescription = chargingLabel },
            )
        }
        Text(
            stringResource(R.string.hm_storage_used, state.usedBytes / 1_000_000_000.0, state.ready),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.hm_storage_reserved, state.reservedBytes / 1_000_000_000.0),
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.settings.enabled && state.settings.maxStorageBytes == null) {
            Text(stringResource(R.string.hm_legacy_limit, state.settings.maxSongs), style = MaterialTheme.typography.bodySmall)
        }
        if (state.failed > 0) Text(stringResource(R.string.hm_failed_downloads, state.failed), style = MaterialTheme.typography.bodySmall)
        val status =
            when {
                state.settings.enabled && state.blocker != null -> {
                    state.blocker
                }

                state.running -> {
                    R.string.fm_smart_checking
                }

                !state.settings.networkConfigured -> {
                    R.string.fm_smart_choose_network
                }

                state.queued && state.settings.enabled -> {
                    R.string.hm_check_queued
                }

                else -> {
                    when (state.settings.lastResult) {
                        SmartDownloadResult.DISABLED -> R.string.fm_smart_off
                        SmartDownloadResult.WAITING -> R.string.fm_smart_waiting
                        SmartDownloadResult.QUEUED -> R.string.fm_smart_queued
                        SmartDownloadResult.FULL -> R.string.fm_smart_full
                        SmartDownloadResult.EMPTY -> R.string.fm_smart_empty
                        SmartDownloadResult.LOW_STORAGE -> R.string.fm_smart_low_storage
                        SmartDownloadResult.PRIVATE -> R.string.fm_smart_private
                        SmartDownloadResult.COOLDOWN -> R.string.fm_smart_recent
                        SmartDownloadResult.ERROR -> R.string.fm_smart_error
                    }
                }
            }
        Text(stringResource(status), style = MaterialTheme.typography.bodySmall)
        Button(onClick = vm::checkNow, enabled = state.settings.enabled && !state.running) { Text(stringResource(R.string.fm_smart_find)) }
        Text(stringResource(R.string.fm_smart_limits_help), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.fm_smart_keep_files), style = MaterialTheme.typography.bodySmall)
    }
}
