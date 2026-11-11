package io.github.aedev.flow.data.music.smart

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmartDownloadRotationTest {
    private val now = 100L * 24 * 60 * 60 * 1000

    @Test fun `rotation requires old completed automatic files and available replacements`() {
        val old = SmartDownloadRotation.Entry("automatic", 1, true, true)
        val rows =
            listOf(
                old,
                old.copy(id = "manual", automatic = false),
                old.copy(id = "protected"),
                old.copy(id = "new", createdAt = now),
                old.copy(id = "unknown", createdAt = 0),
                old.copy(id = "pending", complete = false),
            )
        assertThat(SmartDownloadRotation.select(rows, setOf("protected"), now, 3)).containsExactly("automatic")
        assertThat(SmartDownloadRotation.select(rows, emptySet(), now, 0)).isEmpty()
    }

    @Test fun `rotation removes no more than five files oldest first`() {
        val rows = (1..10).map { SmartDownloadRotation.Entry("$it", it.toLong(), true, true) }
        assertThat(SmartDownloadRotation.select(rows.reversed(), emptySet(), now, 20)).containsExactly("1", "2", "3", "4", "5").inOrder()
    }
}
