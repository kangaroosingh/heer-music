package io.github.aedev.flow.player

import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Assert.*
import org.junit.Test

class MusicRepeatPolicyTest {
    private fun track(
        id: String,
        title: String = "Song",
        artist: String = "Artist",
        duration: Int = 180,
    ) = MusicTrack(id, title, artist, "", duration)

    @Test fun `play next removes upcoming copies but preserves history and the playing track`() {
        assertEquals(listOf(2, 4), MusicQueuePlanner.upcomingCopies(listOf("b", "a", "b", "c", "b"), 1, "a", "b"))
        assertEquals(listOf(2), MusicQueuePlanner.upcomingCopies(listOf("a", "b", "a"), 0, "a", "a"))
        assertTrue(MusicQueuePlanner.upcomingCopies(listOf("a", "b"), -1, null, "b").isEmpty())
    }

    @Test fun `alternate official uploads collapse but live remix and acoustic recordings survive`() {
        val tracks =
            listOf(
                track("a"),
                track("b", "Song (Official Audio)"),
                track("c", "Song (Live)"),
                track("d", "Song (Remix)"),
                track("e", "Song (Acoustic)"),
                track("f", artist = "Other Artist"),
            )
        assertEquals(listOf("a", "c", "d", "e", "f"), MusicRecordingPolicy.unique(tracks).map { it.videoId })
        assertFalse(MusicRecordingPolicy.same(track("a", duration = 0), track("b", duration = 0)))
        assertFalse(MusicRecordingPolicy.same(track("a", duration = 180), track("b", duration = 230)))
        assertFalse(MusicRecordingPolicy.same(track("a"), track("b", "Song (Instrumental)")))
    }

    @Test fun `history excludes last twenty tracks after queue pruning including alternate uploads`() {
        val history = MusicRadioHistory()
        (0..20).forEach { history.record(track("id$it", "Song $it")) }
        val candidates =
            listOf(track("id0", "Song 0"), track("copy1", "Song 1 (Official Video)"), track("id20", "Song 20"), track("new", "New Song"))
        assertEquals(listOf("id0", "new"), history.eligible(candidates, emptyList()).map { it.videoId })
        assertTrue(history.eligible(candidates, candidates).isEmpty())
    }
}
