package com.freshcheck.app.reminders

import android.content.Context
import androidx.work.*
import com.freshcheck.app.data.local.entity.ReminderState
import kotlinx.coroutines.*

class FoodReminderWorker @JvmOverloads constructor(context: Context, params: WorkerParameters,
    private val deliveryOverride: ReminderDelivery? = null) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(WorkManagerReminderScheduler.ID) ?: return@withContext Result.success()
        if (!ReminderRules.realId(id) || !inputData.keyValueMap.containsKey(WorkManagerReminderScheduler.DUE) ||
            !inputData.keyValueMap.containsKey(WorkManagerReminderScheduler.REVISION)) return@withContext Result.success()
        val version = ReminderVersion(id, inputData.getLong(WorkManagerReminderScheduler.DUE, 0),
            inputData.getLong(WorkManagerReminderScheduler.REVISION, 0))
        var delivery = deliveryOverride
        try {
            delivery = delivery ?: ReminderGraph.get(applicationContext).delivery
            when (delivery.run(version)) {
                DeliveryResult.Finished -> Result.success()
                DeliveryResult.Retry -> Result.retry() // Early due to clock movement; never post early.
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (runAttemptCount < 4) Result.retry() else {
                try { delivery?.markFailed(version) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (storageFailure: Exception) { /* Pending state can be reconciled on next startup. */ }
                Result.failure()
            }
        }
    }
}
