package com.freshcheck.app.data.local.converter

import androidx.room.TypeConverter
import com.freshcheck.app.data.local.entity.FoodStatus
import com.freshcheck.app.data.local.entity.ReminderState
import com.freshcheck.app.ui.insights.FoodOutcome
import com.freshcheck.app.ui.myfood.FoodStorage
import com.freshcheck.app.ui.result.FreshnessCondition
import com.freshcheck.app.ui.result.ProduceCategory
import com.freshcheck.app.ui.scan.ScanMode
import java.time.Instant
import java.time.LocalDate

/** Epoch days for dates, UTC epoch milliseconds for instants, stable enum names (never ordinals). */
class DatabaseConverters {
    @TypeConverter fun dateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun categoryToString(value: ProduceCategory): String = value.name
    @TypeConverter fun stringToCategory(value: String): ProduceCategory = ProduceCategory.valueOf(value)
    @TypeConverter fun storageToString(value: FoodStorage): String = value.name
    @TypeConverter fun stringToStorage(value: String): FoodStorage = FoodStorage.valueOf(value)
    @TypeConverter fun conditionToString(value: FreshnessCondition): String = value.name
    @TypeConverter fun stringToCondition(value: String): FreshnessCondition = FreshnessCondition.valueOf(value)
    @TypeConverter fun modeToString(value: ScanMode): String = value.name
    @TypeConverter fun stringToMode(value: String): ScanMode = ScanMode.valueOf(value)
    @TypeConverter fun statusToString(value: FoodStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): FoodStatus = FoodStatus.valueOf(value)
    @TypeConverter fun outcomeToString(value: FoodOutcome): String = value.name
    @TypeConverter fun stringToOutcome(value: String): FoodOutcome = FoodOutcome.valueOf(value)
    @TypeConverter fun reminderStateToString(value: ReminderState): String = value.name
    @TypeConverter fun stringToReminderState(value: String): ReminderState = ReminderState.valueOf(value)
}
