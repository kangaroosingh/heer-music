package io.github.aedev.flow.data.music

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.listening.ListeningStore
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.recommendation.music.MusicPersonalization
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DislikeDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun downloads(): DownloadManager

        fun listening(): ListeningStore

        fun taste(): MusicPersonalization
    }

    override suspend fun doWork(): Result {
        if (inputData.getString("profile") != ActiveProfile.get(applicationContext).id) return Result.success()
        val id = inputData.getString("id") ?: return Result.failure()
        val generation = inputData.getLong("generation", -1)
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        if (deps
                .listening()
                .state
                .first()
                .pendingDislikes[id] != generation
        ) {
            return Result.success()
        }
        if (id in
            deps
                .taste()
                .profile
                .first()
                .disliked
        ) {
            deps.downloads().deleteDownload(id)
        }
        deps.listening().update { it.copy(pendingDislikes = it.pendingDislikes - id) }
        return Result.success()
    }

    companion object {
        fun schedule(
            context: Context,
            id: String,
            generation: Long,
        ) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "heer_dislike_${ActiveProfile.get(context).id}_$id",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DislikeDownloadWorker>()
                    .setInitialDelay(10, TimeUnit.SECONDS)
                    .setInputData(workDataOf("id" to id, "generation" to generation, "profile" to ActiveProfile.get(context).id))
                    .build(),
            )
        }
    }
}
