package com.freshcheck.app.reminders

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*

class ReminderReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val error = ReminderGraph.get(applicationContext).coordinator.reconcile(inputData.getBoolean("force", false))
            if (error == null) Result.success() else if (runAttemptCount < 4) Result.retry() else Result.failure()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
    }
    companion object {
        fun enqueue(context: Context, force: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<ReminderReconcileWorker>()
                .setInputData(workDataOf("force" to force)).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "freshcheck-reminder-reconciliation",
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }
    }
}
