package com.freshcheck.app.reminders

import com.freshcheck.app.data.local.entity.*
import com.freshcheck.app.ui.myfood.FoodStorage
import com.freshcheck.app.ui.result.ProduceCategory
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.*

/** Synthetic fixtures only. No Android services or production database access. */
class FakeReminderStore : ReminderStore {
    val rows = MutableStateFlow<List<ReminderEntity>>(emptyList())
    val foods = mutableMapOf<String, FoodItemEntity>()
    var failRead = false
    var nextId = 0
    override fun observe() = rows
    override suspend fun all() = rows.value.toList().also { if (failRead) error("read failure") }
    override suspend fun get(id: String) = rows.value.firstOrNull { it.id == id }.also { if (failRead) error("read failure") }
    override suspend fun food(id: String) = foods[id]
    override suspend fun save(draft: ReminderDraft, date: LocalDate, due: Instant, now: Instant): ReminderEntity {
        val food = requireNotNull(foods[draft.foodId])
        require(food.status == FoodStatus.Active)
        val old = draft.reminderId?.let { requireNotNull(rows.value.firstOrNull { r -> r.id == it }) }
        require(old == null || old.foodId == draft.foodId)
        val row = ReminderEntity(old?.id ?: "reminder-${++nextId}", draft.foodId, due, true,
            ReminderState.DefinitionOnly, old?.createdAt ?: now, maxOf(now, old?.updatedAt?.plusMillis(1) ?: Instant.EPOCH))
        foods[food.id] = food.copy(recheck = date)
        rows.value = rows.value.filterNot { it.id == row.id } + row
        return row
    }
    override suspend fun disable(id: String, now: Instant) {
        require(ReminderRules.realId(id))
        val row = requireNotNull(get(id))
        put(row.copy(enabled = false, state = ReminderState.Disabled, updatedAt = row.updatedAt.plusMillis(1)))
    }
    override suspend fun delete(id: String) { requireNotNull(get(id)); rows.value = rows.value.filterNot { it.id == id } }
    fun put(row: ReminderEntity) { rows.value = rows.value.filterNot { it.id == row.id } + row }
    override suspend fun markPending(version: ReminderVersion, state: ReminderState, enabled: Boolean): Boolean {
        val row = get(version.id) ?: return false
        if (!ReminderRules.matches(row, version) || !ReminderRules.pending(row)) return false
        put(row.copy(state = state, enabled = enabled))
        return true
    }
    override suspend fun claim(version: ReminderVersion, now: Instant): Boolean {
        val row = get(version.id) ?: return false
        return ReminderRules.matches(row, version) && ReminderRules.valid(row, food(row.foodId)) &&
            row.dueAt <= now && markPending(version, ReminderState.Claimed, false)
    }
    override suspend fun postClaimed(version: ReminderVersion, post: (FoodItemEntity) -> PostResult): PostResult {
        val row = get(version.id) ?: return PostResult.Stale
        val food = food(row.foodId)
        if (!ReminderRules.matches(row, version) || row.state != ReminderState.Claimed ||
            !ReminderRules.valid(row, food)) return PostResult.Stale
        val result = post(requireNotNull(food))
        when (result) {
            PostResult.Posted -> put(row.copy(state = ReminderState.Posted, enabled = false))
            PostResult.Blocked -> put(row.copy(state = ReminderState.Blocked, enabled = true))
            else -> Unit
        }
        return result
    }
}
class FakeReminderSettings : ReminderSettingsStore {
    val state = MutableStateFlow(ReminderPreferences(true, true))
    override val changes = state
    override fun current() = state.value
    override suspend fun set(value: ReminderPreferences) { state.value = value }
}
class FakeReminderScheduler : ReminderScheduler {
    val work = mutableMapOf<String, ReminderVersion>()
    var enqueues = 0
    var fail = false
    override suspend fun ensure(version: ReminderVersion, now: Instant, force: Boolean) {
        if (fail) error("enqueue failure")
        if (force || work[version.id] != version) { work[version.id] = version; enqueues++ }
    }
    override suspend fun enqueued(version: ReminderVersion) = work[version.id] == version
    override suspend fun cancel(id: String) { work.remove(id) }
    override suspend fun cancelExcept(ids: Set<String>) { work.keys.retainAll(ids) }
}
class FakeReminderNotifications : ReminderNotifications {
    var capability = NotificationAvailability(true, true, true, "Available")
    var outcome = PostResult.Posted
    var attempts = 0
    val visible = mutableSetOf<String>()
    override fun availability() = capability
    override fun post(row: ReminderEntity, food: FoodItemEntity): PostResult {
        attempts++
        if (outcome == PostResult.Posted) visible += row.id
        return outcome
    }
    override fun cancel(id: String) { visible.remove(id) }
    override fun retainOnly(ids: Set<String>) { visible.retainAll(ids) }
}
fun fixtureFood() = FoodItemEntity("food", "Apples", "apple", ProduceCategory.Fruit, 2.0, "pieces",
    FoodStorage.Refrigerated, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19), "",
    FoodStatus.Active, Instant.parse("2026-11-18T00:00:00Z"), Instant.parse("2026-11-18T00:00:00Z"))
