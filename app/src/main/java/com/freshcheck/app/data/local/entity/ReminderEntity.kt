package com.freshcheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/** Neither value means that an OS job or notification has been scheduled. */
enum class ReminderState { DefinitionOnly, Disabled }

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = FoodItemEntity::class, parentColumns = ["id"], childColumns = ["foodId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["foodId"]), Index(value = ["enabled", "state", "dueAt"])]
)
data class ReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val foodId: String,
    val dueAt: Instant,
    val enabled: Boolean = true,
    val state: ReminderState = ReminderState.DefinitionOnly,
    val createdAt: Instant,
    val updatedAt: Instant
)
