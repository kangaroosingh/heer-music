package io.github.aedev.flow.ui.screens.listening

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import io.github.aedev.flow.ui.components.layout.flowBottomContentPadding
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListeningScreen(
    onPlay: (
        MusicTrack,
        List<MusicTrack>,
    ) -> Unit,
    onBackup: () -> Unit,
    vm: ListeningViewModel = hiltViewModel(),
) {
    val state by vm.library.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    var query by rememberSaveable {
        mutableStateOf("")
    }

    var tab by rememberSaveable {
        mutableIntStateOf(0)
    }

    var link by rememberSaveable {
        mutableStateOf("")
    }

    var adding by rememberSaveable {
        mutableStateOf(false)
    }

    val context = LocalContext.current

    fun open(url: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url),
                ),
            )
        }
    }

    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            vm.importAudio(uris)
        }

    Column(Modifier.fillMaxSize()) {
        FlowTopBar(title = stringResource(R.string.hm_listen))

        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            listOf(
                R.string.hm_radio,
                R.string.hm_books,
                R.string.hm_podcasts,
                R.string.hm_mixes,
            ).forEachIndexed {
                index,
                title,
                ->
                Tab(
                    selected = tab == index,
                    onClick = {
                        tab = index
                    },
                    text = {
                        Text(stringResource(title))
                    },
                )
            }
        }

        if (tab == 3) {
            ListeningMixes(onPlay)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = flowBottomContentPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.hm_downloaded_only),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            state.downloadedOnly,
                            vm::offline,
                        )
                    }

                    Text(
                        stringResource(R.string.hm_downloaded_only_help),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (tab == 0) {
                    radioItems(state, onPlay, vm::favourite, ::open)
                } else {
                    item {
                        Text(
                            stringResource(if (tab == 1) R.string.hm_books_help else R.string.hm_podcasts_help),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    adding = true
                                },
                                enabled = !busy && !state.downloadedOnly,
                            ) {
                                Text(stringResource(R.string.hm_add_link))
                            }

                            if (tab == 1) {
                                OutlinedButton(
                                    onClick = {
                                        importer.launch(arrayOf("audio/*"))
                                    },
                                ) {
                                    Text(stringResource(R.string.hm_import_audio))
                                }
                            } else {
                                OutlinedButton(
                                    onClick = vm::refresh,
                                    enabled = !busy && !state.downloadedOnly,
                                ) {
                                    Text(stringResource(R.string.hm_refresh_feeds))
                                }
                            }
                        }

                        if (tab == 1) {
                            TextButton(
                                onClick = {
                                    open("https://www.audible.com.au/library/titles")
                                },
                            ) {
                                Text(stringResource(R.string.hm_open_audible))
                            }

                            TextButton(
                                onClick = {
                                    open("https://www.youtube.com/results?search_query=punjabi+audiobook+official")
                                },
                            ) {
                                Text(stringResource(R.string.hm_find_books))
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    vm.addLink(
                                        "https://www.abc.net.au/radio/programs/conversations/feed/7711104/podcast.xml",
                                        false,
                                    )
                                },
                                enabled = !busy && !state.downloadedOnly,
                            ) {
                                Text(stringResource(R.string.hm_abc_conversations))
                            }

                            TextButton(
                                onClick = {
                                    open("https://www.sbs.com.au/language/punjabi/en/listen")
                                },
                            ) {
                                Text(stringResource(R.string.hm_sbs_punjabi))
                            }
                        }

                        if (busy) Text(stringResource(R.string.hm_loading_sources), style = MaterialTheme.typography.bodyMedium)
                        if (error) {
                            Text(
                                stringResource(R.string.hm_listen_error),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                            },
                            label = {
                                Text(stringResource(R.string.hm_search_spoken))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                vm.search(
                                    query,
                                    tab == 1,
                                )
                            },
                            enabled = query.isNotBlank() && !busy && !state.downloadedOnly,
                        ) {
                            Text(stringResource(R.string.hm_search_youtube))
                        }
                    }

                    items(
                        results.filter {
                            ListeningIds.kind(it.id) == if (tab == 1) "youtube_book" else "youtube_podcast"
                        },
                        key = {
                            "search:${it.id}"
                        },
                    ) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(entry.title)
                            },
                            supportingContent = {
                                Text(entry.author)
                            },
                            modifier =
                                Modifier.clickable(enabled = !state.downloadedOnly) {
                                    vm.saveResult(entry)
                                    onPlay(
                                        entry.track(),
                                        listOf(entry.track()),
                                    )
                                },
                        )
                    }

                    item {
                        TextButton(onClick = vm::bookmark) {
                            Text(stringResource(R.string.hm_save_bookmark))
                        }

                        Text(
                            stringResource(R.string.hm_bookmark_help),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val entries =
                        state.entries.filter { entry ->
                            val kind = ListeningIds.kind(entry.id).orEmpty()
                            (if (tab == 1) kind.endsWith("book") else kind.endsWith("podcast")) && (!state.downloadedOnly || kind == "book")
                        }

                    if (entries.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.hm_listen_empty),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    items(
                        entries,
                        key = {
                            it.id
                        },
                    ) { entry ->
                        ListItem(
                            headlineContent = {
                                Text(entry.title)
                            },
                            supportingContent = {
                                Text(entry.author)
                            },
                            modifier =
                                Modifier.clickable {
                                    onPlay(
                                        entry.track(),
                                        entries.map {
                                            it.track()
                                        },
                                    )
                                },
                        )
                        Row {
                            if (entry.id in state.bookmarks) {
                                TextButton(
                                    onClick = {
                                        onPlay(
                                            entry.track(),
                                            entries.map {
                                                it.track()
                                            },
                                        )
                                    },
                                ) {
                                    Text(stringResource(R.string.hm_resume_bookmark))
                                }
                            }

                            TextButton(
                                onClick = {
                                    vm.remove(entry.id)
                                },
                            ) {
                                Text(stringResource(R.string.hm_remove_entry))
                            }
                        }
                    }
                }

                item {
                    TextButton(onClick = onBackup) {
                        Text(stringResource(R.string.hm_backup_library))
                    }
                }
            }
        }
    }

    if (adding) {
        ListeningLinkDialog(
            link = link,
            books = tab == 1,
            onChange = { link = it },
            onDismiss = { adding = false },
            onAdd = {
                vm.addLink(link, tab == 1)
                adding = false
                link = ""
            },
        )
    }
}
