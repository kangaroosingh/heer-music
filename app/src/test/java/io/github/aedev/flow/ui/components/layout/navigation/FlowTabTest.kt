package io.github.aedev.flow.ui.components.layout.navigation

import io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowTabTest {
    private fun originalTabs(
        order: List<Int>,
        visibility: NavigationVisibility,
    ) = visibleFlowTabs(order, visibility, musicOnly = false)

    private fun originalDefaultTab(
        preferredId: Int,
        order: List<Int>,
        visibility: NavigationVisibility,
    ) = resolveDefaultFlowTab(preferredId, order, visibility, musicOnly = false)

    @Test fun musicOnlyKeepsFourMusicDestinations() {
        assertEquals(
            listOf(FlowTab.Music, FlowTab.Search, FlowTab.Library, FlowTab.Downloads, FlowTab.Listen),
            visibleFlowTabs(emptyList(), NavigationVisibility(), musicOnly = true),
        )
        assertEquals(FlowTab.Music, resolveDefaultFlowTab(FlowTab.Home.id, emptyList(), NavigationVisibility(), musicOnly = true))
    }

    @Test
    fun hiddenHomeFallsBackToFirstVisibleDestination() {
        val visibility = NavigationVisibility(home = false, shorts = false, music = false)

        val resolved =
            originalDefaultTab(
                preferredId = FlowTab.Home.id,
                order = listOf(0, 4, 3, 1, 2, 5, 6),
                visibility = visibility,
            )

        assertEquals(FlowTab.Library, resolved)
        assertFalse(originalTabs(listOf(0, 4, 3), visibility).contains(FlowTab.Home))
    }

    @Test
    fun reEnabledHomeRestoresAHomeDefault() {
        val resolved =
            originalDefaultTab(
                preferredId = FlowTab.Home.id,
                order = listOf(3, 0, 4),
                visibility = NavigationVisibility(home = true),
            )

        assertEquals(FlowTab.Home, resolved)
    }

    @Test
    fun tabsFollowTheSavedOrder() {
        val tabs =
            originalTabs(
                order = listOf(4, 3, 2, 1, 0),
                visibility = NavigationVisibility(),
            )

        assertEquals(
            listOf(FlowTab.Library, FlowTab.Subscriptions, FlowTab.Music, FlowTab.Shorts, FlowTab.Home),
            tabs,
        )
    }

    @Test
    fun aSavedOrderMissingNewerTabsAppendsThemInDefaultOrder() {
        val tabs =
            originalTabs(
                order = listOf(3, 0),
                visibility = NavigationVisibility(search = true, categories = true),
            )

        assertEquals(
            listOf(
                FlowTab.Subscriptions,
                FlowTab.Home,
                FlowTab.Shorts,
                FlowTab.Music,
                FlowTab.Library,
                FlowTab.Search,
                FlowTab.Explore,
            ),
            tabs,
        )
    }

    @Test
    fun unknownPersistedIdsAreIgnored() {
        val tabs = originalTabs(order = listOf(42, 4, -1, 3), visibility = NavigationVisibility())

        assertEquals(FlowTab.Library, tabs.first())
        assertEquals(tabs.distinct(), tabs)
    }

    @Test
    fun persistedIdsNeverChange() {
        assertEquals(DEFAULT_NAV_TAB_ORDER + 8, FlowTab.entries.map(FlowTab::id))
        FlowTab.entries.forEach { tab -> assertEquals(tab, FlowTab.fromId(tab.id)) }
    }

    /**
     * Settings and Notifications live in the top bar of every root destination, which only works if
     * a root destination always exists. Subscriptions and Library are unconditional in
     * [visibleFlowTabs]; this pins that down so hiding tabs can never orphan those screens.
     */
    @Test
    fun everyVisibilityCombinationKeepsAnUnhideableRootDestination() {
        val orders =
            listOf(
                DEFAULT_NAV_TAB_ORDER,
                listOf(6, 5, 4, 3, 2, 1, 0),
                listOf(4, 3),
                emptyList(),
            )

        for (bits in 0 until 32) {
            val visibility =
                NavigationVisibility(
                    home = bits and 1 != 0,
                    shorts = bits and 2 != 0,
                    music = bits and 4 != 0,
                    search = bits and 8 != 0,
                    categories = bits and 16 != 0,
                )

            for (order in orders) {
                val visible = originalTabs(order, visibility)
                assertTrue(
                    "no unhideable root destination for $visibility / $order",
                    visible.contains(FlowTab.Subscriptions) || visible.contains(FlowTab.Library),
                )

                val resolved = originalDefaultTab(FlowTab.Home.id, order, visibility)
                assertTrue(
                    "resolved default $resolved is not visible for $visibility / $order",
                    visible.contains(resolved),
                )
            }
        }
    }

    @Test
    fun theDownloadsTabIsOffUntilTurnedOn() {
        assertFalse(FlowTab.Downloads in originalTabs(DEFAULT_NAV_TAB_ORDER, NavigationVisibility()))
        assertEquals(
            FlowTab.Downloads,
            originalTabs(listOf(3, 0), NavigationVisibility(downloads = true)).last(),
        )
    }
}
