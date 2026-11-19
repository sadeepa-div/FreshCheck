package com.freshcheck.app.reminders

import com.freshcheck.app.data.local.entity.ReminderState
import java.time.Clock

/** Pure delivery policy with Android/Room hidden behind testable ports. */
class ReminderDelivery(
    private val store: ReminderStore,
    private val settings: ReminderSettingsStore,
    private val notifications: ReminderNotifications,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun markFailed(version: ReminderVersion) { store.markPending(version, ReminderState.Failed) }

    suspend fun run(version: ReminderVersion): DeliveryResult {
        if (!ReminderRules.realId(version.id)) return DeliveryResult.Finished
        val row = store.get(version.id) ?: return DeliveryResult.Finished
        if (!ReminderRules.matches(row, version) || !ReminderRules.pending(row) ||
            !ReminderRules.valid(row, store.food(row.foodId))) return DeliveryResult.Finished
        if (clock.instant() < row.dueAt) return DeliveryResult.Retry // A backward clock change must never cause early posting.
        if (!settings.current().enabled || !notifications.availability().allowed) {
            store.markPending(version, ReminderState.Blocked)
            return DeliveryResult.Finished
        }
        // Commit the claim before crossing the Android notification boundary: prefer a possible miss to duplicates.
        if (!store.claim(version, clock.instant())) return DeliveryResult.Finished
        store.postClaimed(version) { food ->
            if (!settings.current().enabled || !notifications.availability().allowed) PostResult.Blocked
            else notifications.post(row, food)
        }
        return DeliveryResult.Finished
    }
}
