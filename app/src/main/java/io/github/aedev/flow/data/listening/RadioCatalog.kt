package io.github.aedev.flow.data.listening

import io.github.aedev.flow.data.music.model.MusicTrack

object RadioCatalog {
    data class Station(
        val title: String,
        val subtitle: String,
        val stream: String,
        val website: String,
    ) {
        fun track() = MusicTrack(ListeningIds.of("radio", stream), title, subtitle, "", 0, sourceUrl = website)
    }

    val stations =
        listOf(
            Station(
                "Radio Haanji Melbourne",
                "Punjabi · Melbourne · 1674 AM",
                "https://cast1.my-control-panel.com/proxy/haanji_mel/stream",
                "https://haanji.com.au/live",
            ),
            Station(
                "Radio Haanji Sydney",
                "Punjabi · Sydney",
                "https://cast1.my-control-panel.com/proxy/sydney/stream",
                "https://haanji.com.au/live",
            ),
            Station("Haanji Gurbani", "Gurbani", "https://cast1.my-control-panel.com/proxy/gurbani/stream", "https://haanji.com.au/live"),
            Station(
                "Haanji Kitaab Kahani",
                "Punjabi audio stories · Live",
                "https://cast1.my-control-panel.com/proxy/kitaab_kahani/stream",
                "https://haanji.com.au/live",
            ),
            Station(
                "ABC Radio Melbourne",
                "Melbourne / Victoria · 774 AM",
                "https://streaming.abc-cdn.net.au/audio/hls/localmelbourne.m3u8?source=web",
                "https://www.abc.net.au/listen/live/melbourne",
            ),
            Station(
                "SBS Radio 2",
                "Multilingual - Punjabi programmes",
                "https://sbs-hls.streamguys1.com/hls/sbs2/playlist.m3u8?aw_0_1st.playerid=sbs-web",
                "https://www.sbs.com.au/language/punjabi/en/listen",
            ),
            Station(
                "SBS PopDesi",
                "South Asian music",
                "https://sbs-hls.streamguys1.com/hls/popdesi/playlist.m3u8?aw_0_1st.playerid=sbs-web",
                "https://www.sbs.com.au/audio/radio/popdesi",
            ),
            Station(
                "3AW",
                "Melbourne / Victoria · 693 AM",
                "https://playerservices.streamtheworld.com/api/livestream-redirect/3AW.mp3",
                "https://www.3aw.com.au/listen-live/",
            ),
        )
}
