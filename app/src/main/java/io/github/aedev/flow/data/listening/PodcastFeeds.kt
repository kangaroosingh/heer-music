package io.github.aedev.flow.data.listening

import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastFeeds
    @Inject
    constructor(
        private val client: OkHttpClient,
    ) {
        private val cache = mutableMapOf<String, Pair<Long, List<ListeningEntry>>>()

        suspend fun load(url: String): List<ListeningEntry> =
            withContext(Dispatchers.IO) {
                require(Uri.parse(url).scheme == "https")
                synchronized(
                    cache,
                ) { cache[url] }?.takeIf { System.currentTimeMillis() - it.first < 3_600_000 }?.let { return@withContext it.second }
                val call = client.newCall(Request.Builder().url(url).build())
                call.timeout().timeout(30, java.util.concurrent.TimeUnit.SECONDS)
                val xml =
                    call.execute().use { response ->
                        check(response.isSuccessful)
                        val body = checkNotNull(response.body)
                        require(body.contentLength() <= 8_000_000)
                        val source = body.source()
                        val bytes = java.io.ByteArrayOutputStream()
                        val buffer = okio.Buffer()
                        while (bytes.size() <= 8_000_000) {
                            val read = source.read(buffer, minOf(8192L, 8_000_001L - bytes.size()))
                            if (read == -1L) break
                            bytes.write(buffer.readByteArray())
                        }
                        require(bytes.size() <= 8_000_000)
                        bytes.toString("UTF-8")
                    }
                parse(xml).also { entries ->
                    synchronized(cache) {
                        cache[url] = System.currentTimeMillis() to entries
                        while (cache.size > 12) cache.remove(cache.keys.first())
                    }
                }
            }

        internal fun parse(xml: String): List<ListeningEntry> {
            require(!xml.contains("<!DOCTYPE", ignoreCase = true))
            val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
            val result = mutableListOf<ListeningEntry>()
            var inItem = false
            var title = ""
            var author = ""
            var audio = ""
            var image = ""
            while (parser.eventType != XmlPullParser.END_DOCUMENT && result.size < 100) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name.substringAfter(':')) {
                        "item" -> {
                            inItem = true
                            title = ""
                            author = ""
                            audio = ""
                            image = ""
                        }

                        "title" -> {
                            if (inItem) title = parser.nextText()
                        }

                        "author", "creator" -> {
                            if (inItem) author = parser.nextText()
                        }

                        "enclosure" -> {
                            if (inItem &&
                                parser.getAttributeValue(null, "type").orEmpty().startsWith("audio/")
                            ) {
                                audio =
                                    parser.getAttributeValue(null, "url").orEmpty()
                            }
                        }

                        "image" -> {
                            if (inItem) image = parser.getAttributeValue(null, "href").orEmpty()
                        }
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "item") {
                    if (title.isNotBlank() &&
                        Uri.parse(audio).scheme == "https"
                    ) {
                        result += ListeningEntry(ListeningIds.of("podcast", audio), title, author, image)
                    }
                    inItem = false
                }
                parser.next()
            }
            return result.distinctBy { it.id }
        }
    }
