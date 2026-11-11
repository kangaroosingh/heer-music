package io.github.aedev.flow.data.music.smart

internal object SmartDownloadRotation {
    data class Entry(
        val id: String,
        val createdAt: Long,
        val automatic: Boolean,
        val complete: Boolean,
    )

    fun select(
        entries: List<Entry>,
        protected: Set<String>,
        now: Long,
        replacementCount: Int,
    ): List<String> =
        entries
            .filter {
                it.automatic && it.complete && it.id !in protected && it.createdAt > 0 && now - it.createdAt >= 30L * 24 * 60 * 60 * 1000
            }.sortedBy { it.createdAt }
            .take(replacementCount.coerceIn(0, 5))
            .map { it.id }
}
