package io.github.aedev.flow.data.listening

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URLEncoder

@Serializable
data class SoundCloudMix(
    val url: String,
    val title: String,
)

object SoundCloudLinks {
    fun canonical(raw: String): String? =
        runCatching {
            val uri = URI(raw.trim())
            require(uri.scheme == "https" && uri.host in setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com"))
            require(uri.userInfo == null && uri.port == -1)
            require(
                uri.rawQuery?.split('&').orEmpty().all {
                    val key = it.substringBefore('=')
                    key == "si" || key.startsWith("utm_")
                },
            )
            val segments = uri.path.trim('/').split('/')
            require(segments.size == 2 || (segments.size == 3 && segments[1] == "sets"))
            require(segments.all { it.matches(Regex("[A-Za-z0-9_-]+")) })
            "https://soundcloud.com/${segments.joinToString("/")}"
        }.getOrNull()

    fun widget(url: String): String {
        val safe = requireNotNull(canonical(url))
        return "https://w.soundcloud.com/player/?url=${URLEncoder.encode(safe, "UTF-8")}&auto_play=false&show_user=true"
    }
}
