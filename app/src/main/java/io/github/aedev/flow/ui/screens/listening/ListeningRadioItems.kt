package io.github.aedev.flow.ui.screens.listening

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.listening.ListeningIds
import io.github.aedev.flow.data.listening.RadioCatalog
import io.github.aedev.flow.data.music.model.MusicTrack

internal fun LazyListScope.radioItems(
    state: io.github.aedev.flow.data.listening.ListeningState,
    onPlay: (MusicTrack, List<MusicTrack>) -> Unit,
    onFavourite: (String) -> Unit,
    open: (String) -> Unit,
) {
    item {
        Text(
            stringResource(R.string.hm_victoria),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.hm_emergency_help),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = {
                open("https://emergency.vic.gov.au/")
            },
        ) {
            Text(stringResource(R.string.hm_vicemergency))
        }

        TextButton(
            onClick = {
                open("https://www.abc.net.au/emergency/")
            },
        ) {
            Text(stringResource(R.string.hm_abc_emergency))
        }
    }

    items(
        RadioCatalog.stations.sortedByDescending {
            it.track().videoId in state.favourites
        },
        key = {
            it.stream
        },
    ) { station ->
        val track = station.track()
        ListItem(
            headlineContent = {
                Text(station.title)
            },
            supportingContent = {
                Text(station.subtitle)
            },
            leadingContent = {
                Text(
                    stringResource(R.string.hm_live),
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            trailingContent = {
                IconButton(
                    onClick = {
                        onFavourite(track.videoId)
                    },
                ) {
                    Icon(
                        if (track.videoId in state.favourites) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        stringResource(R.string.hm_favourite_station),
                    )
                }
            },
            modifier =
                Modifier.clickable(enabled = !state.downloadedOnly) {
                    onPlay(
                        track,
                        listOf(track),
                    )
                },
        )
        TextButton(
            onClick = {
                open(station.website)
            },
        ) {
            Text(stringResource(R.string.hm_official_player))
        }
    }
}
