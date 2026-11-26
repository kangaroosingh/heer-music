package io.github.aedev.flow.data.listening

import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Assert.*
import org.junit.Test

class WorkoutPolicyTest {
    private fun track(
        id: String,
        title: String,
        duration: Int = 180,
        artist: String = "Karan Aujla",
    ) = MusicTrack(id, title, artist, "", duration)

    @Test fun `tempo is never invented from genre or search words`() {
        assertNull(WorkoutPolicy.labelledBpm(track("a", "Punjabi energetic gym")))
        assertNull(WorkoutPolicy.labelledBpm(track("a", "2026 BPM")))
        assertEquals(130, WorkoutPolicy.labelledBpm(track("a", "Song - 130 BPM")))
    }

    @Test fun `strict tempo filters unknowns long mixes and out of range labels`() {
        val candidates =
            listOf(
                track("a", "Song 125 BPM"),
                track("b", "Song 140 BPM"),
                track("c", "Song 170 BPM"),
                track("d", "Song"),
                track("e", "Mix 130 BPM", 3600),
            )
        val result = WorkoutPolicy.build(candidates, WorkoutRequest(includeUnknownTempo = false))
        assertEquals(setOf("a", "b"), result.map { it.videoId }.toSet())
    }

    @Test fun `duration stays within request and duplicate upload is not selected`() {
        val tracks = (1..20).map { track("$it", "Song $it 130 BPM") }
        val result = WorkoutPolicy.build(tracks + tracks.first().copy(videoId = "copy"), WorkoutRequest(minutes = 30))
        assertEquals(1800, result.sumOf { it.duration })
        assertEquals(10, result.size)
    }

    @Test fun `queries are Punjabi bounded and include favourite artist`() {
        val queries = WorkoutPolicy.queries(WorkoutRequest(), listOf("Babbu Maan", "Other"))
        assertEquals(3, queries.size)
        assertTrue(queries.all { "Punjabi" in it })
        assertTrue(queries.any { "Babbu Maan" in it })
    }

    @Test fun `generic English fitness tracks cannot qualify from BPM alone`() {
        val candidates =
            listOf(
                track("a", "Target Rate 120 BPM", artist = "Workout Electronica"),
                track("b", "Antisocial Workout Remix 135 BPM", artist = "Power Music Workout"),
                track("c", "Status", artist = "Jordan Sandhu"),
            )
        assertEquals(listOf("c"), WorkoutPolicy.build(candidates, WorkoutRequest()).map { it.videoId })
    }

    @Test fun `Punjabi context selection preserves unknown tempo without inventing language`() {
        val track = track("new", "Punjabi Bhangra Workout", artist = "New Artist")
        assertTrue(WorkoutPolicy.hasPunjabiContext(track))
        assertNull(WorkoutPolicy.labelledBpm(track))
        assertTrue(WorkoutPolicy.build(listOf(track), WorkoutRequest(includeUnknownTempo = false)).isEmpty())
    }
}
