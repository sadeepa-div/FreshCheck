package com.freshcheck.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.freshcheck.app.data.local.database.FreshCheckDatabase
import com.freshcheck.app.data.local.entity.*
import com.freshcheck.app.data.mapper.*
import com.freshcheck.app.ui.insights.FoodOutcome
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.scan.ScanSubmission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class RoomFreshCheckRepository(
    private val database: FreshCheckDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val displayZone: ZoneId = clock.zone
) : FreshCheckRepository {
    private val food = database.foodItemDao()
    private val scans = database.scanRecordDao()
    private val outcomes = database.foodOutcomeDao()
    private val reminders = database.reminderDao()

    override fun observeActiveFood() = food.observeActiveWithScans().map { rows -> rows.map { it.toUi(displayZone) } }
    override fun observeAllFood() = food.observeAll()
    override suspend fun findFood(id: String) = operation {
        requireProductionId(id)
        food.findWithScans(id)?.toUi(displayZone)
    }

    override suspend fun insertFood(item: FoodItemUi) = operation {
        validateFood(item)
        require(item.scans.isEmpty()) { "Save scan submissions separately; food insertion does not import scan history." }
        food.insert(item.toEntity(clock.instant()))
    }

    override suspend fun updateFood(item: FoodItemUi) = operation {
        validateFood(item)
        database.withTransaction {
            val existing = activeFood(item.id)
            // Food editing does not rewrite scan history, creation time or terminal status.
            checkChanged(food.update(item.toEntity(clock.instant()).copy(
                createdAt = existing.createdAt, status = existing.status
            )))
            // A date-only edit cannot safely choose a reminder time. Disable stale definitions atomically.
            if (existing.recheck != item.recheck) reminders.disableForFood(item.id, clock.instant())
        }
    }

    override suspend fun deleteFood(id: String) = operation {
        requireProductionId(id)
        database.withTransaction {
            if (food.findById(id) == null) fail(DatabaseError.MissingRecord, "Food item does not exist.")
            checkChanged(food.delete(id))
            // SET NULL preserves scan snapshots; CASCADE removes reminders. Outcomes have no FK.
        }
    }

    override suspend fun markUsed(id: String) = recordOutcome(id, FoodOutcome.Used)
    override suspend fun markDiscarded(id: String) = recordOutcome(id, FoodOutcome.Discarded)

    private suspend fun recordOutcome(id: String, outcome: FoodOutcome): DatabaseResult<Unit> = operation {
        requireProductionId(id)
        database.withTransaction {
            val item = activeFood(id)
            val at = clock.instant()
            val status = if (outcome == FoodOutcome.Used) FoodStatus.Used else FoodStatus.Discarded
            checkChanged(food.finish(id, status, at))
            outcomes.insert(FoodOutcomeEntity(UUID.randomUUID().toString(), id, item.name, item.category, outcome, at))
            reminders.disableForFood(id, at)
        }
    }

    override fun observeScanHistory() = scans.observeAll().map { rows -> rows.map { it.toHistory(displayZone) } }
    override fun observeScansForFood(foodId: String) = scans.observeForFood(foodId).map { rows -> rows.map { it.toHistory(displayZone) } }
    override suspend fun findScan(id: String) = operation {
        requireProductionId(id)
        scans.findById(id)
    }
    override suspend fun saveScan(submission: ScanSubmission, scannedAt: Instant) = operation {
        persistScan(submission, scannedAt)
    }
    override suspend fun addScanToFood(foodId: String, submission: ScanSubmission, scannedAt: Instant) = operation {
        requireProductionId(foodId)
        require(submission.rescanFoodId == foodId) { "Submission must preserve the requested food ID." }
        persistScan(submission, scannedAt)
    }
    private suspend fun persistScan(submission: ScanSubmission, scannedAt: Instant): String {
        val scan = submission.toEntity(UUID.randomUUID().toString(), scannedAt)
        return database.withTransaction {
            scan.foodId?.let { activeFood(it) }
            scans.insert(scan)
            scan.id
        }
    }
    override suspend fun deleteScan(id: String) = operation {
        requireProductionId(id)
        checkChanged(scans.delete(id))
    }
    override suspend fun clearScanHistory() = operation { scans.clear(); Unit }

    override fun observeUsedOutcomes() = outcomes.observeUsed().map { rows -> rows.map { it.toUi(displayZone) } }
    override fun observeDiscardedOutcomes() = outcomes.observeDiscarded().map { rows -> rows.map { it.toUi(displayZone) } }
    override fun observeAllOutcomes() = outcomes.observeAll().map { rows -> rows.map { it.toUi(displayZone) } }

    override suspend fun insertReminder(reminder: ReminderEntity) = operation {
        validateReminder(reminder)
        database.withTransaction {
            activeFood(reminder.foodId)
            val now = clock.instant()
            reminders.insert(reminder.copy(createdAt = now, updatedAt = now))
        }
    }
    override suspend fun updateReminder(reminder: ReminderEntity) = operation {
        validateReminder(reminder)
        database.withTransaction {
            val existing = reminders.findById(reminder.id)
                ?: fail(DatabaseError.MissingRecord, "Reminder does not exist.")
            require(existing.foodId == reminder.foodId) { "Reminder cannot be reassigned to another food item." }
            activeFood(reminder.foodId)
            checkChanged(reminders.update(reminder.copy(createdAt = existing.createdAt, updatedAt = clock.instant())))
        }
    }
    override suspend fun deleteReminder(id: String) = operation {
        requireProductionId(id)
        checkChanged(reminders.delete(id))
    }
    override fun observeReminders(foodId: String) = reminders.observeForFood(foodId)
    override suspend fun findDueReminders(at: Instant) = operation { reminders.findDue(at) }

    private fun validateFood(item: FoodItemUi) {
        item.toEntity(clock.instant())
        require(item.name.isNotBlank()) { "Produce name is required." }
        val errors = validateFoodEdit(
            FoodEditValues(item.quantity.toString(), item.unit, item.added.toString(), item.recheck?.toString() ?: "", item.notes),
            LocalDate.now(clock)
        )
        require(errors.isEmpty()) { errors.values.joinToString(" ") }
    }
    private fun validateReminder(reminder: ReminderEntity) {
        requireProductionId(reminder.id)
        requireProductionId(reminder.foodId)
        require(reminder.enabled == (reminder.state == ReminderState.DefinitionOnly)) {
            "Enabled definitions must be DefinitionOnly; disabled definitions must be Disabled."
        }
    }
    private suspend fun activeFood(id: String): FoodItemEntity {
        val item = food.findById(id) ?: fail(DatabaseError.MissingRecord, "Food item does not exist.")
        if (item.status != FoodStatus.Active) fail(DatabaseError.InactiveFood, "Food item is no longer active.")
        return item
    }
    private fun checkChanged(count: Int) {
        if (count != 1) fail(DatabaseError.MissingRecord, "Record does not exist or was changed.")
    }
    private class OperationFailure(val reason: DatabaseError, message: String) : Exception(message)
    private fun fail(reason: DatabaseError, message: String): Nothing = throw OperationFailure(reason, message)

    private suspend fun <T> operation(block: suspend () -> T): DatabaseResult<T> = try {
        DatabaseResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: OperationFailure) {
        DatabaseResult.Failure(failure.reason, failure.message ?: "Operation failed.")
    } catch (invalid: IllegalArgumentException) {
        DatabaseResult.Failure(DatabaseError.InvalidInput, invalid.message ?: "Invalid input.")
    } catch (conflict: SQLiteConstraintException) {
        DatabaseResult.Failure(DatabaseError.Conflict, "Record already exists or violates a database relationship.")
    } catch (failure: Exception) {
        DatabaseResult.Failure(DatabaseError.Storage, "Database operation failed; no success was confirmed.")
    }
}
