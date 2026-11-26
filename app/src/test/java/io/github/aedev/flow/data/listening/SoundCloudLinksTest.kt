package io.github.aedev.flow.data.listening

import org.junit.Assert.*
import org.junit.Test

class SoundCloudLinksTest {
    @Test fun `public shared mix is canonical and embed is encoded`() {
        val url = SoundCloudLinks.canonical("https://www.soundcloud.com/artist/sets/workout?si=abc&utm_source=clipboard")
        assertEquals("https://soundcloud.com/artist/sets/workout", url)
        assertTrue(SoundCloudLinks.widget(url!!).startsWith("https://w.soundcloud.com/player/?url=https%3A%2F%2Fsoundcloud.com"))
    }

    @Test fun `private hostile non https and shortened urls are rejected`() {
        listOf(
            "http://soundcloud.com/a/b",
            "https://soundcloud.com.evil/a/b",
            "https://evil@soundcloud.com/a/b",
            "https://soundcloud.com/a/b?secret_token=s-123",
            "https://on.soundcloud.com/abc",
            "https://soundcloud.com/a",
            "javascript:alert(1)",
        ).forEach { assertNull(it, SoundCloudLinks.canonical(it)) }
    }
}
