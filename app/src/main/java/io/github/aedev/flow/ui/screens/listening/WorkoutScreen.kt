package io.github.aedev.flow.ui.screens.listening

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.listening.*
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.layout.flowBottomContentPadding

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutScreen(
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    vm: WorkoutViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val request = state.request
    val title = stringResource(R.string.hm_workout_name)
    val description = stringResource(R.string.hm_workout_description, request.centreBpm - 10, request.centreBpm + 10)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = flowBottomContentPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.hm_workout_help), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutStyle.entries.forEach { style ->
                    val label =
                        when (style) {
                            WorkoutStyle.WEIGHTS -> R.string.hm_weights
                            WorkoutStyle.RUNNING -> R.string.hm_running
                            WorkoutStyle.INTERVALS -> R.string.hm_intervals
                        }
                    FilterChip(
                        selected = request.style == style,
                        enabled = !state.busy,
                        onClick = { vm.request(request.copy(style = style, centreBpm = style.centreBpm)) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 45, 60).forEach { minutes ->
                    FilterChip(
                        selected = request.minutes == minutes,
                        enabled = !state.busy,
                        onClick = { vm.request(request.copy(minutes = minutes)) },
                        label = { Text(stringResource(R.string.hm_workout_minutes, minutes)) },
                    )
                }
            }
        }
        item {
            Text(stringResource(R.string.hm_workout_tempo, request.centreBpm - 10, request.centreBpm + 10))
            var tempo by remember(request.centreBpm) { mutableFloatStateOf(request.centreBpm.toFloat()) }
            Slider(
                value = tempo,
                enabled = !state.busy,
                valueRange = 110f..180f,
                steps = 13,
                onValueChange = { tempo = it },
                onValueChangeFinished = { vm.request(request.copy(centreBpm = tempo.toInt())) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.hm_workout_unknown), Modifier.weight(1f))
                Switch(
                    checked = request.includeUnknownTempo,
                    enabled = !state.busy,
                    onCheckedChange = { vm.request(request.copy(includeUnknownTempo = it)) },
                )
            }
            Text(stringResource(R.string.hm_workout_tempo_help), style = MaterialTheme.typography.bodySmall)
        }
        item {
            Button(onClick = vm::generate, enabled = !state.busy) { Text(stringResource(R.string.hm_workout_build)) }
            if (state.busy) Text(stringResource(R.string.hm_loading_sources))
            if (state.error) Text(stringResource(R.string.hm_workout_error))
            if (state.generated && state.tracks.isEmpty()) Text(stringResource(R.string.hm_workout_empty))
        }
        if (state.tracks.isNotEmpty()) {
            item {
                Text(stringResource(R.string.hm_workout_result, state.tracks.size, state.tracks.sumOf { it.duration } / 60))
                Text(stringResource(R.string.hm_workout_order), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(state.tracks.first(), state.tracks) }, enabled = !state.busy) {
                        Text(stringResource(R.string.hm_workout_play))
                    }
                    OutlinedButton(onClick = { vm.save(title, description) }, enabled = !state.busy && !state.saved) {
                        Text(stringResource(if (state.saved) R.string.hm_workout_saved else R.string.hm_workout_save))
                    }
                }
            }
            itemsIndexed(state.tracks, key = { _, track -> track.videoId }) { index, track ->
                val bpm = WorkoutPolicy.labelledBpm(track)
                ListItem(
                    headlineContent = { Text("${index + 1}. ${track.title}") },
                    supportingContent = {
                        Text(
                            track.artist + " · " +
                                if (bpm == null) {
                                    stringResource(R.string.hm_bpm_unknown)
                                } else {
                                    stringResource(R.string.hm_bpm_labelled, bpm)
                                },
                        )
                    },
                    modifier = Modifier.clickable { onPlay(track, state.tracks) },
                )
            }
        }
    }
}
