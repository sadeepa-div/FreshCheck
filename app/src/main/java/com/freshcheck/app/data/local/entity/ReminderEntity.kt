package com.freshcheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/** Status is not proof of current OS permission or on-screen presentation. Claimed is terminal after a crash. */
enum class ReminderState { DefinitionOnly, Disabled, Enqueued, Blocked, Claimed, Posted, Failed }

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
