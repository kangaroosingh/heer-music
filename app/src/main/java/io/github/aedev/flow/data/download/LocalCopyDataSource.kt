package io.github.aedev.flow.data.download

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Opens a song from the device when it is there: a file or document URI, or a `music://` item
 * whose download [localCopyOf] finds. Those bypass both caches, which only ever hold streamed
 * bytes under the same id. Direct HTTPS audio without a song cache key bypasses [remote], keeping
 * live radio out of the YouTube resolver and caches. Media3 routes by URI scheme, not download
 * availability, so the saved-file lookup happens here.
 */
@OptIn(UnstableApi::class)
internal class LocalCopyDataSource(
    private val local: DataSource,
    private val remote: DataSource,
    private val allowNetwork: () -> Boolean = { true },
    private val localCopyOf: (videoId: String) -> Uri?,
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        local.addTransferListener(transferListener)
        remote.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val onDevice =
            when (dataSpec.uri.scheme) {
                in DEVICE_SCHEMES -> {
                    dataSpec
                }

                MUSIC_SCHEME -> {
                    (dataSpec.key ?: dataSpec.uri.host)
                        ?.let(
                            localCopyOf,
                        )?.let {
                            dataSpec
                                .buildUpon()
                                .setUri(it)
                                .setKey(null)
                                .build()
                        }
                }

                else -> {
                    null
                }
            }
        if (onDevice == null && !allowNetwork()) throw java.io.IOException("Downloaded-only playback is enabled")
        val directAudio = dataSpec.uri.scheme == "https" && dataSpec.key == null
        val source = if (onDevice != null || directAudio) local else remote
        current = source
        return source.open(onDevice ?: dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = checkNotNull(current) { "read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders.orEmpty()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    class Factory(
        private val local: DataSource.Factory,
        private val remote: DataSource.Factory,
        private val allowNetwork: () -> Boolean = { true },
        private val localCopyOf: (videoId: String) -> Uri?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            LocalCopyDataSource(local.createDataSource(), remote.createDataSource(), allowNetwork, localCopyOf)
    }

    internal companion object {
        const val MUSIC_SCHEME = "music"
        val DEVICE_SCHEMES = setOf("file", "content", "android.resource")
    }
}
