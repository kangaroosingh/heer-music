package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.newmusic.InnertubeMusicService
import kotlinx.coroutines.withTimeoutOrNull

internal object MusicLinkTracks {
    suspend fun song(videoId: String): MusicTrack =
        InnertubeMusicService.fetchQueue(videoIds = listOf(videoId)).firstOrNull()
            ?: MusicTrack(videoId = videoId, title = "", artist = "", thumbnailUrl = "", duration = 0)

    suspend fun playlist(
        id: String,
        params: String?,
    ): List<MusicTrack> = withTimeoutOrNull(10_000L) { InnertubeMusicService.fetchWatchPlaylist(id, params) }.orEmpty()
}
