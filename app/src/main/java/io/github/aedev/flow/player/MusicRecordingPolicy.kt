package io.github.aedev.flow.player

import io.github.aedev.flow.data.lyrics.LyricsMatching
import io.github.aedev.flow.data.music.model.MusicTrack
import java.util.Locale
import kotlin.math.abs

/** Conservative upload matching: never collapse different recordings on title alone. */
internal object MusicRecordingPolicy {
    private data class Identity(
        val title: String,
        val artist: String,
        val versions: Set<String>,
        val duration: Int,
    )

    private fun identity(track: MusicTrack): Identity? {
        if (track.title.isBlank() || track.artist.isBlank() || track.duration <= 0) return null
        return Identity(
            LyricsMatching.cleanTitle(track.title).lowercase(Locale.ROOT),
            LyricsMatching.cleanArtist(track.artist).lowercase(Locale.ROOT),
            LyricsMatching.versionTags(track.title),
            track.duration,
        )
    }

    private fun matches(
        a: Identity,
        b: Identity,
    ): Boolean = a.title == b.title && a.artist == b.artist && a.versions == b.versions && abs(a.duration - b.duration) <= 3

    fun same(
        a: MusicTrack,
        b: MusicTrack,
    ): Boolean {
        if (a.videoId == b.videoId) return true
        val first = identity(a) ?: return false
        val second = identity(b) ?: return false
        return matches(first, second)
    }

    fun unique(
        tracks: List<MusicTrack>,
        excluded: List<MusicTrack> = emptyList(),
    ): List<MusicTrack> {
        val ids = excluded.mapTo(HashSet()) { it.videoId }
        val identities = excluded.mapNotNull(::identity).toMutableList()
        return buildList {
            tracks.forEach { track ->
                val key = identity(track)
                if (track.videoId !in ids && (key == null || identities.none { matches(it, key) })) {
                    add(track)
                    ids += track.videoId
                    if (key != null) identities += key
                }
            }
        }
    }
}

/** In-memory only; does not add listening telemetry or persist history in private modes. */
internal class MusicRadioHistory {
    private var recent = emptyList<MusicTrack>()

    @Synchronized fun record(track: MusicTrack?) {
        if (track == null ||
            io.github.aedev.flow.data.localmedia.LocalMediaIds
                .isLocal(track.videoId) ||
            recent.lastOrNull()?.videoId == track.videoId
        ) {
            return
        }
        recent = (recent + track).takeLast(20)
    }

    @Synchronized fun eligible(
        tracks: List<MusicTrack>,
        queued: List<MusicTrack>,
    ): List<MusicTrack> = MusicRecordingPolicy.unique(tracks, recent + queued)
}
