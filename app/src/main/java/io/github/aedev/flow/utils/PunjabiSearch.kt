package io.github.aedev.flow.utils

import com.ibm.icu.text.Transliterator
import java.text.Normalizer
import java.util.Locale

object PunjabiSearch {
    private val romanizer = ThreadLocal.withInitial { Transliterator.getInstance("Any-Latin; Latin-ASCII") }
    private val aliases =
        mapOf(
            "babu mana" to "babbu maan",
            "babbu mana" to "babbu maan",
            "arajana dhilom" to "arjan dhillon",
            "arjan dhilon" to "arjan dhillon",
            "amaridara gila" to "amrinder gill",
            "amrinder gil" to "amrinder gill",
            "moose wala" to "moosewala",
            "muse wala" to "moosewala",
            "musevala" to "moosewala",
            "musewala" to "moosewala",
        ).map { (alias, canonical) -> Regex("\\b${Regex.escape(alias)}\\b") to canonical }

    fun query(text: String): String = if (text.any { it in '\u0a00'..'\u0a7f' }) key(text) else text.trim()

    fun key(text: String): String {
        val plain =
            Normalizer
                .normalize(checkNotNull(romanizer.get()).transliterate(text), Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
        return aliases.fold(plain) { value, (alias, canonical) -> value.replace(alias, canonical) }
    }

    fun matches(
        query: String,
        text: String,
    ): Boolean {
        val words = key(query).split(' ').filter { it.isNotEmpty() }
        val candidate = key(text)
        return words.isNotEmpty() && words.all { it in candidate }
    }
}
