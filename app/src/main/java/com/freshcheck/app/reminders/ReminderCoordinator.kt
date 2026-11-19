package com.freshcheck.app.reminders

import com.freshcheck.app.data.local.entity.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** Room commits and WorkManager operations are deliberately separate, with a recoverable pending state. */
class ReminderCoordinator(
    val store: ReminderStore,
    private val settings: ReminderSettingsStore,
    private val scheduler: ReminderScheduler,
    private val notifications: ReminderNotifications,
    private val clock: Clock = Clock.systemUTC()
) {
    private val mutex = Mutex()
    val changes = combine(store.observe(), settings.changes) { _, _ -> Unit }

    suspend fun snapshot(): ReminderUiData {
        val rows = store.all()
        val preferences = settings.current()
        val availability = notifications.availability()
        val statuses = rows.associate { row ->
            val status = when {
                row.state == ReminderState.Posted -> "Submitted to Android; visible presentation is controlled by the system."
                row.state == ReminderState.Claimed -> if (scheduler.enqueued(ReminderVersion.of(row)))
                    "Delivery is being processed; notification posting is not confirmed."
                    else "Delivery uncertain after interruption. Update explicitly to try again."
                !row.enabled || row.state == ReminderState.Disabled -> "Disabled"
                row.state == ReminderState.Failed -> "Processing failed. Update the reminder to retry."
                !ReminderRules.valid(row, store.food(row.foodId)) -> "Inactive or invalid food; not scheduled."
                !preferences.enabled -> "Saved only; reminder notifications are disabled in FreshCheck."
                !availability.allowed -> "Saved only; " + availability.explanation
                scheduler.enqueued(ReminderVersion.of(row)) -> "Work enqueued. Android may delay this reminder."
                else -> "Saved definition; work is not currently enqueued."
            }
            row.id to status
        }
        return ReminderUiData(rows.toList(), preferences, availability, statuses, ready = true)
    }

    suspend fun save(draft: ReminderDraft): ReminderResult = mutex.withLock {
        var saved = false
        try {
            val (date, due) = ReminderRules.resolve(draft, clock.instant())
            val row = store.save(draft, date, due, clock.instant())
            saved = true
            notifications.cancel(row.id) // An update clears the previous version's visible notification.
            val errors = reconcileLocked()
            val allowed = notifications.availability()
            val scheduled = settings.current().enabled && allowed.allowed &&
                scheduler.enqueued(ReminderVersion.of(row))
            ReminderResult(true, scheduled,
                if (scheduled) "Reminder saved and work enqueued. Android may delay delivery."
                else "Reminder definition saved; notification work is not active. " +
                    (errors ?: if (!settings.current().enabled) "Enable reminder notifications in FreshCheck."
                    else allowed.explanation),
                permissionNeeded = !allowed.permission, reminderId = row.id, error = errors)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ReminderResult(saved, false,
                if (saved) "Definition saved, but scheduling could not be confirmed. Retry reconciliation."
                else if (failure is IllegalArgumentException) failure.message ?: "Invalid reminder input."
                else "Unable to save reminder. Refresh saved state and try again.", error = "Reminder saving or scheduling was not fully confirmed.")
        }
    }

    suspend fun disable(id: String): ReminderResult = mutex.withLock {
        try {
            store.disable(id, clock.instant())
            val error = reconcileLocked()
            ReminderResult(true, false, error?.let { "Disabled in the database; cancellation needs attention: $it" }
                ?: "Reminder disabled and work cancellation confirmed.", error = error)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { ReminderResult(false, false, failure.message ?: "Unable to disable reminder.") }
    }
    suspend fun delete(id: String): ReminderResult = mutex.withLock {
        var deleted = false
        try {
            store.delete(id)
            deleted = true
            scheduler.cancel(id)
            notifications.cancel(id)
            ReminderResult(true, false, "Reminder canceled, deleted and work cancellation confirmed.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { ReminderResult(deleted, false,
            if (deleted) "Definition deleted. Work cancellation was not confirmed; refresh reminders."
            else "Reminder cancellation was not confirmed. Refresh saved reminders.", error = "Reminder cancellation needs reconciliation.") }
    }

    suspend fun setPreferences(value: ReminderPreferences): String? = mutex.withLock {
        settings.set(value)
        reconcileLocked()
    }
    suspend fun reconcile(force: Boolean = false): String? = mutex.withLock { reconcileLocked(force) }

    private suspend fun reconcileLocked(force: Boolean = false): String? {
        val rows = store.all()
        val prefs = settings.current()
        val availability = notifications.availability()
        val scheduled = mutableSetOf<String>()
        val visible = mutableSetOf<String>()
        val errors = mutableListOf<String>()
        for (row in rows) {
            try {
                val valid = ReminderRules.valid(row, store.food(row.foodId))
                if (valid && prefs.enabled && availability.allowed &&
                    (ReminderRules.pending(row) || row.state == ReminderState.Posted || row.state == ReminderState.Claimed))
                    visible += row.id
                if (valid && prefs.enabled && availability.allowed && ReminderRules.pending(row)) {
                    scheduled += row.id // Do not cancel desired work if replacement temporarily fails.
                    val version = ReminderVersion.of(row)
                    scheduler.ensure(version, clock.instant(), force)
                    store.markPending(version, ReminderState.Enqueued)
                } else if (valid && prefs.enabled && availability.allowed &&
                    (row.state == ReminderState.Claimed || row.state == ReminderState.Posted) &&
                    scheduler.enqueued(ReminderVersion.of(row))) {
                    scheduled += row.id // Let a currently posting worker finish; never enqueue a consumed version.
                } else {
                    scheduler.cancel(row.id)
                    if (ReminderRules.pending(row)) store.markPending(ReminderVersion.of(row), ReminderState.Blocked)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { errors += "Work for a saved reminder could not be reconciled." }
        }
        try {
            scheduler.cancelExcept(scheduled)
            notifications.retainOnly(visible)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { errors += "Some outdated reminder work or notifications could not be canceled." }
        return errors.distinct().joinToString(" ").ifBlank { null }
    }
}
