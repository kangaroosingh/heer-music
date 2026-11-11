package io.github.aedev.flow.ui.tv.navigation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.components.layout.navigation.FlowTab
import io.github.aedev.flow.ui.components.layout.navigation.NavigationVisibility
import org.junit.Test

class TvTabsTest {
    private val allOn = NavigationVisibility(home = true, shorts = true, music = true, search = true, categories = true)

    @Test
    fun `music only TV navigation keeps the audio destinations`() {
        val nav = tvNavigation(emptyList(), FlowTab.Music.id, allOn, musicOnly = true)
        assertThat(nav.tabs).containsExactly(FlowTab.Music, FlowTab.Search, FlowTab.Library, FlowTab.Downloads).inOrder()
        assertThat(nav.startTab).isEqualTo(FlowTab.Music)
    }

    @Test
    fun `follows the user's order and leaves Shorts and Explore off`() {
        val order = listOf(FlowTab.Library, FlowTab.Shorts, FlowTab.Music, FlowTab.Explore, FlowTab.Home).map(FlowTab::id)

        val tabs = tvNavigation(order, defaultTabId = FlowTab.Home.id, visibility = allOn, musicOnly = false).tabs

        assertThat(tabs.take(3)).containsExactly(FlowTab.Library, FlowTab.Music, FlowTab.Home).inOrder()
        assertThat(tabs).containsNoneOf(FlowTab.Shorts, FlowTab.Explore)
    }

    @Test
    fun `always shows Search, which TV has nowhere else to put`() {
        val tabs = tvNavigation(emptyList(), FlowTab.Home.id, NavigationVisibility(search = false), musicOnly = false).tabs

        assertThat(tabs).contains(FlowTab.Search)
    }

    @Test
    fun `hides the tabs the user hid`() {
        val tabs = tvNavigation(emptyList(), FlowTab.Home.id, NavigationVisibility(home = false, music = false), musicOnly = false).tabs

        assertThat(tabs).containsNoneOf(FlowTab.Home, FlowTab.Music)
    }

    @Test
    fun `opens on the user's default tab, or the first tab when TV leaves it off`() {
        assertThat(tvNavigation(emptyList(), FlowTab.Library.id, allOn, musicOnly = false).startTab).isEqualTo(FlowTab.Library)

        val fromShorts = tvNavigation(emptyList(), FlowTab.Shorts.id, allOn, musicOnly = false)
        assertThat(fromShorts.startTab).isEqualTo(fromShorts.tabs.first())
    }

    @Test
    fun `roots are the tabs plus Settings`() {
        val navigation = tvNavigation(emptyList(), FlowTab.Home.id, allOn, musicOnly = false)

        assertThat(navigation.rootRoutes).containsExactlyElementsIn(navigation.tabs.map(FlowTab::route) + TV_SETTINGS_ROUTE)
    }
}
