package io.github.aedev.flow.data.listening

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListeningSourcesTest {
    @Test fun `all radio sources use https and never become youtube videos`() {
        RadioCatalog.stations.forEach { station ->
            val track = station.track()
            assertThat(Uri.parse(station.stream).scheme).isEqualTo("https")
            assertThat(LocalMediaIds.isLocal(track.videoId)).isTrue()
            assertThat(ListeningIds.audioUri(track.videoId).toString()).isEqualTo(station.stream)
            assertThat(ListeningIds.youtubeId(track.videoId)).isNull()
            assertThat(track.highResThumbnailUrl).isEmpty()
        }
    }

    @Test fun `youtube long form retains extraction id while isolated from music learning`() {
        val id = ListeningIds.of("youtube_book", "https://www.youtube.com/watch?v=abcdefghijk")
        assertThat(LocalMediaIds.isLocal(id)).isTrue()
        assertThat(ListeningIds.audioUri(id).toString()).isEqualTo("music://abcdefghijk")
        assertThat(ListeningIds.youtubeId(id)).isEqualTo("abcdefghijk")
    }

    @Test fun `rss preserves episode identity ignores video and clears fields between items`() {
        val episodes =
            PodcastFeeds(
                OkHttpClient(),
            ).parse(
                """<rss><channel><item><title>First &amp; second</title><author>Host</author><enclosure url="https://example.org/one.mp3" type="audio/mpeg"/></item><item><title>Video</title><enclosure url="https://example.org/movie.mp4" type="video/mp4"/></item><item><title>Third</title><enclosure url="https://example.org/three.mp3" type="audio/mpeg"/></item></channel></rss>""",
            )
        assertThat(episodes.map { it.title }).containsExactly("First & second", "Third").inOrder()
        assertThat(episodes.last().author).isEmpty()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `external XML entities are rejected`() {
        PodcastFeeds(OkHttpClient()).parse("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss/>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `insecure sources are not imported`() {
        ListeningIds.of("podcast", "http://example.org/audio.mp3")
    }
}
