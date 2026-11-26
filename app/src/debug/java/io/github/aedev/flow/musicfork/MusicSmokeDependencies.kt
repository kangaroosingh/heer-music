package io.github.aedev.flow.musicfork

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.recommendation.music.MusicPersonalization

/** Debug-only access to the actual application graph for native smoke tests. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicSmokeDependencies {
    fun libraryPlaylists(): io.github.aedev.flow.data.local.PlaylistRepository

    fun videoSwitch(): io.github.aedev.flow.player.musicvideo.MusicVideoSwitch

    fun listening(): io.github.aedev.flow.data.listening.ListeningStore

    fun autoLibrary(): io.github.aedev.flow.service.auto.AutoLibrary

    fun feeds(): io.github.aedev.flow.data.listening.PodcastFeeds

    fun downloads(): DownloadManager

    fun personalization(): MusicPersonalization
}
