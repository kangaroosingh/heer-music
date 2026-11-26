package io.github.aedev.flow.data.listening

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.listeningData by safePreferencesDataStore(name = "heer_listening_v1", scope = StoreScope.PROFILE_PRIVATE)

@Serializable
data class ListeningEntry(
    val id: String,
    val title: String,
    val author: String = "",
    val artwork: String = "",
    val duration: Int = 0,
) {
    fun track() = MusicTrack(id, title, author, artwork, duration)
}

@Serializable
data class ListeningState(
    val version: Int = 1,
    val entries: List<ListeningEntry> = emptyList(),
    val feeds: List<String> = emptyList(),
    val favourites: Set<String> = emptySet(),
    val bookmarks: Map<String, Long> = emptyMap(),
    val downloadedOnly: Boolean = false,
    val pendingDislikes: Map<String, Long> = emptyMap(),
    val soundCloudMixes: List<SoundCloudMix> = emptyList(),
)

@Singleton
class ListeningStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val key = stringPreferencesKey("library")
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        private fun decode(value: String?) =
            value?.let { runCatching { json.decodeFromString<ListeningState>(it) }.getOrNull() } ?: ListeningState()

        val state = context.listeningData.data.map { decode(it[key]) }

        suspend fun update(change: (ListeningState) -> ListeningState) {
            context.listeningData.edit { it[key] = json.encodeToString(ListeningState.serializer(), change(decode(it[key]))) }
        }

        suspend fun add(entries: List<ListeningEntry>) =
            update {
                it.copy(entries = (it.entries + entries).distinctBy { e -> e.id }.takeLast(1000))
            }
    }
