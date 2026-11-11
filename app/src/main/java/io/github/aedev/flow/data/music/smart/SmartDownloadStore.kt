package io.github.aedev.flow.data.music.smart

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.smartDownloadData by safePreferencesDataStore(name = "smart_downloads_v1", scope = StoreScope.PROFILE_PRIVATE)

@Serializable
data class SmartDownloadSettings(
    val version: Int = 1,
    val enabled: Boolean = false,
    val networkConfigured: Boolean = false,
    val maxSongs: Int = 20,
    val maxStorageBytes: Long? = null,
    val lastRunAt: Long = 0,
    val recentAdditions: Map<String, Long> = emptyMap(),
    val lastResult: SmartDownloadResult = SmartDownloadResult.DISABLED,
)

@Serializable
enum class SmartDownloadResult { DISABLED, WAITING, QUEUED, FULL, EMPTY, LOW_STORAGE, PRIVATE, COOLDOWN, ERROR }

@Singleton
class SmartDownloadStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val key = stringPreferencesKey("settings")
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        private fun decode(raw: String?): SmartDownloadSettings =
            raw?.let {
                runCatching { json.decodeFromString<SmartDownloadSettings>(it) }.getOrNull()
            } ?: SmartDownloadSettings()

        val state = context.smartDownloadData.data.map { decode(it[key]) }

        suspend fun update(change: (SmartDownloadSettings) -> SmartDownloadSettings) {
            context.smartDownloadData.edit { prefs ->
                val old = decode(prefs[key])
                require(old.version == 1)
                prefs[key] = json.encodeToString(SmartDownloadSettings.serializer(), change(old))
            }
        }

        suspend fun reserveAddition(
            id: String,
            now: Long,
            used: Int,
            usedBytes: Long = 0,
            newBytes: Long = 0,
        ): Boolean {
            var accepted = false
            update { old ->
                if (!old.enabled || !old.networkConfigured) return@update old
                if (old.maxStorageBytes == null && used >= old.maxSongs.coerceIn(10, 50)) return@update old
                if (old.maxStorageBytes?.let { usedBytes + newBytes > it } == true) return@update old
                val recent = old.recentAdditions.toMutableMap()
                recent.remove(id)
                recent[id] = now
                val kept =
                    recent.entries
                        .toList()
                        .takeLast(200)
                        .associate { it.toPair() }
                accepted = true
                old.copy(recentAdditions = kept)
            }
            return accepted
        }
    }
