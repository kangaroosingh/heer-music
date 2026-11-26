package io.github.aedev.flow.data.listening

import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.primaryArtistKey
import io.github.aedev.flow.player.MusicRecordingPolicy
import io.github.aedev.flow.utils.PunjabiSearch

enum class WorkoutStyle(
    val centreBpm: Int,
) {
    WEIGHTS(130),
    RUNNING(160),
    INTERVALS(150),
}

data class WorkoutRequest(
    val style: WorkoutStyle = WorkoutStyle.WEIGHTS,
    val minutes: Int = 45,
    val centreBpm: Int = 130,
    val includeUnknownTempo: Boolean = true,
)

object WorkoutPolicy {
    private val bpmLabel = Regex("(?i)\\b(\\d{2,3})\\s*bpm\\b")

    // Editorial artist/context selection, not inferred language metadata or a tempo measurement.
    private val punjabiArtists =
        setOf(
            "babbu maan",
            "karan aujla",
            "sidhu moosewala",
            "diljit dosanjh",
            "amrinder gill",
            "arjan dhillon",
            "ap dhillon",
            "shubh",
            "gur sidhu",
            "gippy grewal",
            "garry sandhu",
            "ranjit bawa",
            "harbhajan mann",
            "manmohan waris",
            "kamal heer",
            "jazzy b",
            "kulwinder billa",
            "jordan sandhu",
            "navaan sandhu",
            "mankirt aulakh",
            "kaur b",
            "nimrat khaira",
            "sunanda sharma",
            "gurlez akhtar",
            "miss pooja",
            "prem dhillon",
            "amrit maan",
            "ammy virk",
            "afsana khan",
            "harnoor",
            "kaka",
            "nirvair pannu",
            "sajjan adeeb",
            "cheema y",
            "guru randhawa",
            "sharry mann",
            "himmat sandhu",
            "simar doraha",
            "the prophec",
            "zehr vibe",
            "juss",
            "satinder sartaaj",
            "r nait",
            "chani nattan",
            "inderpal moga",
            "talwiinder",
            "gurnam bhullar",
            "sukh e",
            "baani sandhu",
            "aastha gill",
        )
    private val contextLabel = Regex("(?i)\\b(?:punjabi|panjabi|bhangra)\\b")

    private fun artistContext(name: String): Boolean {
        val key = " ${PunjabiSearch.key(name)} "
        return punjabiArtists.any { " $it " in key }
    }

    fun hasPunjabiContext(track: MusicTrack): Boolean =
        artistContext(track.artist) || track.artists.any { artistContext(it.name) } ||
            contextLabel.containsMatchIn("${track.title} ${track.artist}") ||
            (track.title + track.artist).any { it in '\u0a00'..'\u0a7f' }

    /** Uploader-labelled tempo, not an acoustic measurement. Never infer from genre or artist. */
    fun labelledBpm(track: MusicTrack): Int? =
        bpmLabel
            .find(track.title)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.takeIf { it in 60..220 }

    fun queries(
        request: WorkoutRequest,
        artists: List<String>,
    ): List<String> {
        val mood =
            when (request.style) {
                WorkoutStyle.WEIGHTS -> "gym workout"
                WorkoutStyle.RUNNING -> "running bhangra"
                WorkoutStyle.INTERVALS -> "high energy bhangra workout"
            }
        return listOfNotNull(
            "Punjabi $mood ${request.centreBpm} BPM songs",
            "Punjabi $mood energetic songs",
            "${artists.firstOrNull(::artistContext) ?: "Karan Aujla"} Punjabi $mood songs",
        )
    }

    fun build(
        candidates: List<MusicTrack>,
        request: WorkoutRequest,
    ): List<MusicTrack> {
        val range = (request.centreBpm - 10)..(request.centreBpm + 10)
        val eligible =
            MusicRecordingPolicy
                .unique(candidates)
                .filter { track ->
                    hasPunjabiContext(track) && track.duration in 90..600 &&
                        (labelledBpm(track)?.let { it in range } ?: request.includeUnknownTempo)
                }.sortedBy { labelledBpm(it) == null }
                .toMutableList()
        val chosen = mutableListOf<MusicTrack>()
        var remaining = request.minutes.coerceIn(15, 120) * 60
        while (eligible.isNotEmpty()) {
            val fit = eligible.filter { it.duration <= remaining }
            if (fit.isEmpty()) break
            val track = fit.firstOrNull { it.primaryArtistKey() != chosen.lastOrNull()?.primaryArtistKey() } ?: fit.first()
            chosen += track
            eligible.remove(track)
            remaining -= track.duration
        }
        if (chosen.size < 4) return chosen
        // Put the lowest labelled tempos at the edges; unknown values are never treated as slow.
        val labelled = chosen.filter { labelledBpm(it) != null }.sortedBy { labelledBpm(it) }
        val warmup = labelled.firstOrNull() ?: chosen.first()
        val cooldown = labelled.firstOrNull { it != warmup } ?: chosen.last()
        return listOf(warmup) + chosen.filterNot { it == warmup || it == cooldown } + cooldown
    }
}
