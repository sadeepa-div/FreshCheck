package com.freshcheck.app.reminders

import androidx.room.withTransaction
import com.freshcheck.app.data.local.database.FreshCheckDatabase
import com.freshcheck.app.data.local.entity.*
import java.time.*
import java.util.UUID

/** Reuses the existing database and table; status-only writes preserve the configuration revision. */
class RoomReminderStore(private val db: FreshCheckDatabase) : ReminderStore {
    private val dao get() = db.reminderDao()
    override fun observe() = dao.observeAll()
    override suspend fun all() = dao.all()
    override suspend fun get(id: String) = dao.findById(id)
    override suspend fun food(id: String) = db.foodItemDao().findById(id)
    override suspend fun save(draft: ReminderDraft, date: LocalDate, due: Instant, now: Instant) =
        db.withTransaction {
            require(ReminderRules.realId(draft.foodId)) { "Invalid saved food ID." }
            val food = requireNotNull(food(draft.foodId)) { "This food item no longer exists." }
            require(food.status == FoodStatus.Active && ReminderRules.realId(food.id) &&
                (food.produceId == null || ReminderRules.realId(food.produceId))) { "Only active, real food can have reminders." }
            require(date >= food.added) { "A re-check cannot be before the date added." }
            require(due > now) { "Choose a future reminder time." }
            val previous = draft.reminderId?.let {
                require(ReminderRules.realId(it)) { "Invalid reminder ID." }
                requireNotNull(get(it)) { "Reminder no longer exists." }
            }
            require(previous == null || previous.foodId == food.id) { "Reminder cannot be reassigned to different food." }
            val revision = maxOf(Instant.ofEpochMilli(now.toEpochMilli()),
                previous?.updatedAt?.plusMillis(1) ?: Instant.EPOCH)
            // Setting this date must not overwrite quantity, storage, notes or scan history.
            if (food.recheck != date) {
                db.foodItemDao().update(food.copy(recheck = date, updatedAt = now))
                dao.disableForFood(food.id, now)
            }
            val row = ReminderEntity(previous?.id ?: UUID.randomUUID().toString(), food.id, due,
                enabled = true, state = ReminderState.DefinitionOnly, createdAt = previous?.createdAt ?: now,
                updatedAt = revision)
            if (previous == null) dao.insert(row) else check(dao.update(row) == 1)
            row
        }

    override suspend fun disable(id: String, now: Instant) = db.withTransaction {
        require(ReminderRules.realId(id)) { "Invalid reminder ID." }
        val row = requireNotNull(get(id)) { "Reminder no longer exists." }
        dao.update(row.copy(enabled = false, state = ReminderState.Disabled,
            updatedAt = maxOf(Instant.ofEpochMilli(now.toEpochMilli()), row.updatedAt.plusMillis(1))))
        Unit
    }
    override suspend fun delete(id: String) {
        require(ReminderRules.realId(id)) { "Invalid reminder ID." }
        require(dao.delete(id) == 1) { "Reminder no longer exists." }
    }
    override suspend fun markPending(version: ReminderVersion, state: ReminderState, enabled: Boolean) =
        dao.transitionPending(version.id, Instant.ofEpochMilli(version.dueMillis),
            Instant.ofEpochMilli(version.revisionMillis), state, enabled) == 1

    override suspend fun claim(version: ReminderVersion, now: Instant) = db.withTransaction {
        val row = get(version.id)
        row != null && ReminderRules.matches(row, version) && row.dueAt <= now &&
            ReminderRules.pending(row) && ReminderRules.valid(row, food(row.foodId)) &&
            markPending(version, ReminderState.Claimed, enabled = false)
    }

    override suspend fun postClaimed(version: ReminderVersion, post: (FoodItemEntity) -> PostResult) =
        db.withTransaction {
            val row = get(version.id) ?: return@withTransaction PostResult.Stale
            val food = food(row.foodId)
            if (!ReminderRules.matches(row, version) || row.state != ReminderState.Claimed ||
                !ReminderRules.valid(row, food)) return@withTransaction PostResult.Stale
            val result = post(requireNotNull(food))
            when (result) {
                PostResult.Posted -> dao.update(row.copy(state = ReminderState.Posted, enabled = false))
                PostResult.Blocked -> dao.update(row.copy(state = ReminderState.Blocked, enabled = true))
                else -> Unit // Uncertain remains claimed, so process restart will not duplicate delivery.
            }
            result
        }
}
