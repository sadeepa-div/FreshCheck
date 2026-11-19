package com.freshcheck.app.reminders

import com.freshcheck.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.*

interface ReminderStore {
    fun observe(): Flow<List<ReminderEntity>>
    suspend fun all(): List<ReminderEntity>
    suspend fun get(id: String): ReminderEntity?
    suspend fun food(id: String): FoodItemEntity?
    suspend fun save(draft: ReminderDraft, date: LocalDate, due: Instant, now: Instant): ReminderEntity
    suspend fun disable(id: String, now: Instant)
    suspend fun delete(id: String)
    suspend fun markPending(version: ReminderVersion, state: ReminderState, enabled: Boolean = true): Boolean
    suspend fun claim(version: ReminderVersion, now: Instant): Boolean
    /** Rechecks database state and posts while holding the short Room transaction. */
    suspend fun postClaimed(version: ReminderVersion, post: (FoodItemEntity) -> PostResult): PostResult
}
interface ReminderSettingsStore {
    val changes: Flow<ReminderPreferences>
    fun current(): ReminderPreferences
    suspend fun set(value: ReminderPreferences)
}
interface ReminderScheduler {
    suspend fun ensure(version: ReminderVersion, now: Instant, force: Boolean = false)
    suspend fun enqueued(version: ReminderVersion): Boolean
    suspend fun cancel(id: String)
    suspend fun cancelExcept(ids: Set<String>)
}
enum class PostResult { Posted, Blocked, Stale, Uncertain }
interface ReminderNotifications {
    fun availability(): NotificationAvailability
    fun post(row: ReminderEntity, food: FoodItemEntity): PostResult
    fun cancel(id: String)
    fun retainOnly(ids: Set<String>)
}
enum class DeliveryResult { Finished, Retry }
