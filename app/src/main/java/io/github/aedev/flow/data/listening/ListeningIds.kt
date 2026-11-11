package io.github.aedev.flow.data.listening

import android.net.Uri

object ListeningIds {
    private const val PREFIX = "local_heer_"

    fun of(
        kind: String,
        uri: String,
    ): String {
        require(kind in setOf("radio", "book", "podcast", "youtube_book", "youtube_podcast"))
        require(Uri.parse(uri).scheme in setOf("https", "content"))
        return "$PREFIX$kind:${Uri.encode(uri)}"
    }

    fun kind(id: String): String? = id.takeIf { it.startsWith(PREFIX) }?.substringAfter(PREFIX)?.substringBefore(':')

    fun uri(id: String): Uri? = kind(id)?.let { Uri.parse(Uri.decode(id.substringAfter(':'))) }

    fun youtubeId(id: String): String? =
        uri(id)
            ?.takeIf { kind(id)?.startsWith("youtube_") == true }
            ?.let {
                if (it.host == "youtu.be") it.lastPathSegment else it.getQueryParameter("v")
            }?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }

    fun audioUri(id: String): Uri? = youtubeId(id)?.let { Uri.parse("music://$it") } ?: uri(id)

    fun isRadio(id: String): Boolean = kind(id) == "radio"
}
