package com.freshcheck.app.reminders

import com.freshcheck.app.data.local.entity.*
import java.time.*

data class ReminderDraft(
    val foodId: String, val reminderId: String? = null,
    val date: String, val time: String, val zoneId: String,
    val overlapOffsetSeconds: Int? = null
)
data class ReminderPreferences(val notifications: Boolean = false, val rechecks: Boolean = true) {
    val enabled get() = notifications && rechecks
}
data class NotificationAvailability(
    val permission: Boolean = false, val appEnabled: Boolean = false, val channelEnabled: Boolean = false,
    val explanation: String = "Notification capability is loading."
) { val allowed get() = permission && appEnabled && channelEnabled }
data class ReminderVersion(val id: String, val dueMillis: Long, val revisionMillis: Long) {
    companion object {
        fun of(row: ReminderEntity) = ReminderVersion(row.id, row.dueAt.toEpochMilli(), row.updatedAt.toEpochMilli())
    }
}
data class ReminderUiData(
    val rows: List<ReminderEntity> = emptyList(),
    val preferences: ReminderPreferences = ReminderPreferences(),
    val availability: NotificationAvailability = NotificationAvailability(),
    val status: Map<String, String> = emptyMap(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)
data class ReminderResult(val saved: Boolean, val scheduled: Boolean, val message: String,
    val permissionNeeded: Boolean = false, val reminderId: String? = null, val error: String? = null)
data class ReminderActions(
    val save: (ReminderDraft, (ReminderResult) -> Unit) -> Unit = { _, done -> done(ReminderResult(false, false, "Reminder service is unavailable.")) },
    val disable: (String, (ReminderResult) -> Unit) -> Unit = { _, done -> done(ReminderResult(false, false, "Reminder service is unavailable.")) },
    val delete: (String, (ReminderResult) -> Unit) -> Unit = { _, done -> done(ReminderResult(false, false, "Reminder service is unavailable.")) },
    val setPreferences: (ReminderPreferences) -> Unit = {},
    val requestPermission: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val refresh: () -> Unit = {}
)
data class NotificationFoodTarget(val foodId: String, val token: String)

object ReminderRules {
    val pending = setOf(ReminderState.DefinitionOnly, ReminderState.Enqueued, ReminderState.Blocked)
    fun realId(id: String) = id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && !id.startsWith("demo-", true)
    fun valid(row: ReminderEntity, food: FoodItemEntity?): Boolean =
        realId(row.id) && realId(row.foodId) && food != null && realId(food.id) &&
            (food.produceId == null || realId(food.produceId)) &&
            food.status == FoodStatus.Active && food.recheck != null
    fun pending(row: ReminderEntity) = row.enabled && row.state in pending
    fun matches(row: ReminderEntity, version: ReminderVersion) = ReminderVersion.of(row) == version

    /** Fixed instant policy: never silently normalize DST gaps or choose an overlap offset. */
    fun resolve(draft: ReminderDraft, now: Instant): Pair<LocalDate, Instant> {
        require(realId(draft.foodId)) { "Select an existing saved food item. Samples cannot have reminders." }
        draft.reminderId?.let { require(realId(it)) { "Invalid reminder ID." } }
        val date = runCatching { LocalDate.parse(draft.date) }.getOrNull()
        require(date != null && date.year in 1900..9999) { "Select a valid re-check date." }
        require(draft.time.matches(Regex("\\d{2}:\\d{2}"))) { "Select a notification time in HH:mm format." }
        val time = runCatching { LocalTime.parse(draft.time) }.getOrNull()
        require(time != null) { "Select a valid notification time." }
        val zone = runCatching { ZoneId.of(draft.zoneId) }.getOrNull()
        require(zone != null) { "Select a valid time zone, for example Asia/Colombo." }
        val local = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "This local time does not exist because clocks move forward. Choose another time." }
        val offset = if (offsets.size == 1) offsets.single() else {
            offsets.firstOrNull { it.totalSeconds == draft.overlapOffsetSeconds }
                ?: throw IllegalArgumentException("This time occurs twice. Select the intended UTC offset.")
        }
        val at = local.toInstant(offset)
        require(at > now) { "Choose a reminder date and time in the future." }
        return date to at
    }
}
