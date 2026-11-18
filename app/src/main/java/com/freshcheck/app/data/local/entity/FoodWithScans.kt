package com.freshcheck.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class FoodWithScans(
    @Embedded val food: FoodItemEntity,
    @Relation(parentColumn = "id", entityColumn = "foodId")
    val scans: List<ScanRecordEntity>
)
