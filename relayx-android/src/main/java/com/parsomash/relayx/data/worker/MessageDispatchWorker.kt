package com.parsomash.relayx.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.parsomash.relayx.RelayApplication
import com.parsomash.relayx.domain.usecase.DispatchOutboxUseCase
import com.parsomash.relayx.util.RelayLogger
import java.util.concurrent.TimeUnit

class MessageDispatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        RelayLogger.d("Worker", "MessageDispatchWorker executing...")
        val app = applicationContext as RelayApplication
        val dispatchUseCase = DispatchOutboxUseCase(
            outboxDao = app.database.outboxMessageDao(),
            preferencesRepository = app.preferencesRepository
        )

        return try {
            val allDelivered = dispatchUseCase()
            if (allDelivered) {
                RelayLogger.i("Worker", "All pending outbox messages dispatched successfully")
                Result.success()
            } else {
                RelayLogger.w("Worker", "Some messages failed delivery; scheduling exponential backoff retry")
                Result.retry()
            }
        } catch (e: Exception) {
            RelayLogger.e("Worker", "Fatal dispatch worker error", e)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "relayx_message_dispatch"

        @JvmStatic
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MessageDispatchWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            RelayLogger.d("Worker", "Enqueued unique message dispatch work request")
        }
    }
}
