package io.github.aedev.flow.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistCatalog
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistPicker
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicPersonalization
import io.github.aedev.flow.data.recommendation.music.MusicRecommendationProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class MusicTasteViewModel
    @Inject
    constructor(
        val personalization: MusicPersonalization,
        private val brain: MusicBrainEngine,
        private val artists: FavouriteArtistsStore,
        private val listening: io.github.aedev.flow.data.listening.ListeningStore,
        catalog: FavouriteArtistCatalog,
    ) : ViewModel() {
        val profile =
            personalization.profile
                .map { it as MusicRecommendationProfile? }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        val picker = FavouriteArtistPicker(viewModelScope, artists, catalog)
        val reasons = personalization.reasons

        fun save(change: (MusicRecommendationProfile) -> MusicRecommendationProfile) {
            viewModelScope.launch { personalization.update(change) }
        }

        fun undo(id: String) {
            viewModelScope.launch {
                listening.update { it.copy(pendingDislikes = it.pendingDislikes - id) }
                personalization.undoDislike(id)
            }
        }

        fun reset() {
            viewModelScope.launch {
                brain.resetBrain()
                personalization.reset()
                artists.artists.first().forEach { brain.setFavouriteArtist(it.id, it.name, true) }
            }
        }

        fun finish(onDone: () -> Unit) {
            viewModelScope.launch {
                personalization.update { it.copy(setupComplete = true) }
                onDone()
            }
        }
    }
