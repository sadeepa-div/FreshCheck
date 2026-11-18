package com.freshcheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshcheck.app.ui.myfood.FoodStorage
import com.freshcheck.app.ui.result.ProduceCategory
import java.time.Instant
import java.time.LocalDate

enum class FoodStatus { Active, Used, Discarded }

/** Production inventory; visible condition is derived from scans, never the re-check date. */
@Entity(tableName = "food_items", indices = [Index(value = ["status", "added"])])
data class FoodItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val produceId: String?,
    val category: ProduceCategory,
    val quantity: Double,
    val unit: String,
    val storage: FoodStorage,
    val added: LocalDate,
    val recheck: LocalDate?,
    val notes: String,
    val status: FoodStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)
