package io.github.aedev.flow.ui.screens.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.MediaArtistToggleRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MusicTasteControls(
    vm: MusicTasteViewModel,
    onChanged: () -> Unit,
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showSmartDownloads by remember { mutableStateOf(false) }
    LaunchedEffect(profile?.setupComplete) {
        if (profile?.setupComplete == false) {
            open = true
            vm.picker.onQueryChange("Punjabi")
        }
    }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Tune, stringResource(R.string.fm_taste))
    }
    IconButton(onClick = {
        showSmartDownloads = true
    }) {
        Icon(Icons.Outlined.Download, stringResource(R.string.fm_smart_title))
    }
    val settings = profile ?: return
    if (showSmartDownloads) SmartDownloadsSheet(onDismiss = { showSmartDownloads = false })
    if (!open) return
    val artists by vm.picker.state.collectAsStateWithLifecycle()
    val query by vm.picker.query.collectAsStateWithLifecycle()
    var discovery by remember(settings.discovery) { mutableFloatStateOf(settings.discovery.toFloat()) }

    fun done() {
        vm.finish {
            open = false
            onChanged()
        }
    }
    ModalBottomSheet(
        onDismissRequest = ::done,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.fm_taste), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.fm_local_notice), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                vm.finish {
                    open = false
                    showSmartDownloads = true
                    onChanged()
                }
            }) { Text(stringResource(R.string.fm_smart_title)) }
            Text(stringResource(R.string.fm_languages), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val languages =
                    listOf(
                        "pa" to R.string.fm_punjabi,
                        "hi" to R.string.fm_hindi,
                        "en" to R.string.fm_english,
                        "ur" to R.string.fm_urdu,
                    )
                languages.forEach { (code, label) ->
                    FilterChip(
                        selected = code in settings.languages,
                        label = { Text(stringResource(label)) },
                        onClick = {
                            vm.save { old ->
                                val chosen = if (code in old.languages) old.languages - code else old.languages + code
                                old.copy(languages = chosen.ifEmpty { old.languages })
                            }
                        },
                    )
                }
            }
            Text(stringResource(R.string.fm_era), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val eras =
                    listOf(
                        "all" to R.string.fm_all_eras,
                        "classic" to R.string.fm_classics,
                        "recent" to R.string.fm_recent,
                    )
                eras.forEach { (era, label) ->
                    FilterChip(
                        selected = era in settings.eras,
                        label = { Text(stringResource(label)) },
                        onClick = { vm.save { it.copy(eras = setOf(era)) } },
                    )
                }
            }
            Text(stringResource(R.string.fm_discovery), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = discovery,
                onValueChange = { discovery = it },
                onValueChangeFinished = { vm.save { it.copy(discovery = discovery.toDouble()) } },
            )
            val familiarPercent = ((1 - discovery) * 100).toInt()
            val relatedPercent = (discovery * 75).toInt()
            val widerPercent = (discovery * 25).toInt()
            val mixLabel = stringResource(R.string.fm_mix, familiarPercent, relatedPercent, widerPercent)
            Text(mixLabel, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.fm_favourite_artists), style = MaterialTheme.typography.titleMedium)
            FlowSearchField(
                query = query,
                onQueryChange = vm.picker::onQueryChange,
                placeholder = stringResource(R.string.favourite_artists_search),
                onClear = { vm.picker.onQueryChange("") },
                modifier = Modifier.fillMaxWidth(),
            )
            (artists.picked + artists.candidates.orEmpty()).distinctBy { it.id }.take(10).forEach { artist ->
                MediaArtistToggleRow(
                    name = artist.name,
                    thumbnailUrl = artist.thumbnailUrl,
                    picked = artist.id in artists.pickedIds,
                    shape = MaterialTheme.shapes.medium,
                    onToggle = { vm.picker.setFavourite(artist, it) },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.fm_improved))
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { selected -> vm.save { it.copy(enabled = selected) } },
                )
            }
            Text(stringResource(R.string.fm_feedback_help), style = MaterialTheme.typography.bodySmall)
            if (settings.disliked.isNotEmpty()) {
                Text(stringResource(R.string.fm_disliked), style = MaterialTheme.typography.titleMedium)
                settings.disliked.forEach { (id, title) ->
                    Row {
                        Text(title, Modifier.weight(1f))
                        TextButton(onClick = { vm.undo(id) }) { Text(stringResource(R.string.fm_undo)) }
                    }
                }
            }
            Text(stringResource(R.string.fm_stats), style = MaterialTheme.typography.titleMedium)
            settings.metrics.forEach { (mode, stats) ->
                val modeLabel = stringResource(if (mode == "improved") R.string.fm_improved else R.string.fm_original)
                val summary =
                    stringResource(
                        R.string.fm_metrics,
                        modeLabel,
                        stats.starts,
                        stats.completions,
                        stats.earlyManualSkips,
                        stats.discoveryLikes,
                        stats.repeats,
                    )
                Text(summary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.fm_reset)) }
            Button(onClick = ::done, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.fm_save_listen))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.fm_reset)) },
            text = { Text(stringResource(R.string.fm_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.reset()
                    confirmReset = false
                }) { Text(stringResource(R.string.fm_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
