package com.freshcheck.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshcheck.app.ui.insights.FoodOutcome
import com.freshcheck.app.ui.result.ProduceCategory
import java.time.Instant

/** Original ID and display snapshot survive explicit food deletion; deliberately no cascading FK. */
@Entity(
    tableName = "food_outcomes",
    indices = [Index(value = ["foodId"], unique = true), Index(value = ["outcome", "occurredAt"])]
)
data class FoodOutcomeEntity(
    @PrimaryKey val id: String,
    val foodId: String,
    val name: String,
    val category: ProduceCategory,
    val outcome: FoodOutcome,
    val occurredAt: Instant
)
