package io.github.aedev.flow.data.music.smart

import io.github.aedev.flow.data.local.dao.DownloadDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartDownloadBudget
    @Inject
    constructor(
        private val store: SmartDownloadStore,
        private val dao: DownloadDao,
    ) {
        private val slot = Mutex()

        suspend fun <T> withSlot(block: suspend () -> T): T = slot.withLock { block() }

        suspend fun canCommit(
            videoId: String,
            fileBytes: Long,
        ): Boolean {
            val limit = store.state.first().maxStorageBytes ?: return true
            val rows = dao.getAllDownloadsWithItemsOnce()
            val used = SmartDownloadStorage.reservedBytes(rows, exceptId = videoId)
            return fileBytes >= 0 && used + fileBytes <= limit
        }
    }
