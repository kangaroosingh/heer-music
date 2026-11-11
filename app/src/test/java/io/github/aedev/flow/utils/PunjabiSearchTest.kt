package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PunjabiSearchTest {
    @Test fun `Gurmukhi and romanized text share a search key`() {
        val gurmukhi = "\u0a2c\u0a71\u0a2c\u0a42 \u0a2e\u0a3e\u0a28"
        assertThat(PunjabiSearch.query(gurmukhi)).isEqualTo("babbu maan")
        assertThat(PunjabiSearch.matches(gurmukhi, "Babbu Maan")).isTrue()
        assertThat(PunjabiSearch.matches("Babbu Maan", gurmukhi)).isTrue()
    }

    @Test fun `common Moosewala spelling and punctuation match without rewriting metadata`() {
        assertThat(PunjabiSearch.matches("Sidhu Musewala", "SIDHU MOOSEWALA - Live")).isTrue()
        assertThat(PunjabiSearch.matches("", "Any song")).isFalse()
    }
}
