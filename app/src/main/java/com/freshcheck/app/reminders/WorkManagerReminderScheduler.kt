package com.freshcheck.app.reminders

import android.content.Context
import androidx.work.*
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> ListenableFuture<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    addListener({
        try { continuation.resume(get()) }
        catch (failure: Exception) { continuation.resumeWithException(failure.cause ?: failure) }
    }, Executor { it.run() })
    continuation.invokeOnCancellation { cancel(false) }
}

class WorkManagerReminderScheduler(context: Context) : ReminderScheduler {
    private val work = WorkManager.getInstance(context.applicationContext)
    override suspend fun ensure(version: ReminderVersion, now: Instant, force: Boolean) {
        require(ReminderRules.realId(version.id))
        if (!force && enqueued(version)) return
        val delay = Duration.between(now, Instant.ofEpochMilli(version.dueMillis)).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<FoodReminderWorker>()
            .setInputData(workDataOf(ID to version.id, DUE to version.dueMillis, REVISION to version.revisionMillis))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG).addTag(idTag(version.id)).addTag(versionTag(version))
            .build()
        work.enqueueUniqueWork(name(version.id), ExistingWorkPolicy.REPLACE, request).result.awaitValue()
    }
    override suspend fun enqueued(version: ReminderVersion) =
        work.getWorkInfosForUniqueWork(name(version.id)).awaitValue().any {
            !it.state.isFinished && versionTag(version) in it.tags
        }
    override suspend fun cancel(id: String) {
        work.cancelUniqueWork(name(id)).result.awaitValue()
    }
    override suspend fun cancelExcept(ids: Set<String>) {
        work.getWorkInfosByTag(TAG).awaitValue().filter { !it.state.isFinished }.forEach { info ->
            val id = info.tags.firstOrNull { it.startsWith(ID_TAG) }?.removePrefix(ID_TAG)
            if (id == null || id !in ids) work.cancelWorkById(info.id).result.awaitValue()
        }
    }
    companion object {
        const val TAG = "freshcheck-recheck-work"
        const val ID = "reminder_id"
        const val DUE = "due_millis"
        const val REVISION = "revision_millis"
        private const val ID_TAG = "reminder-id:"
        fun name(id: String) = "freshcheck-recheck:$id"
        fun idTag(id: String) = ID_TAG + id
        fun versionTag(v: ReminderVersion) = "reminder-version:${v.dueMillis}:${v.revisionMillis}"
    }
}
