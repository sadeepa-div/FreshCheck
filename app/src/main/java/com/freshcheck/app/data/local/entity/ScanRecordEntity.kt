package com.freshcheck.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshcheck.app.ui.result.FreshnessCondition
import com.freshcheck.app.ui.result.ProduceCategory
import com.freshcheck.app.ui.scan.ScanMode
import java.time.Instant

data class StoredGuidance(
    val location: String,
    val recheck: String,
    val handling: String,
    val reminder: String?
)

/** Immutable scan snapshot. Deleting food detaches its live link without deleting history. */
@Entity(
    tableName = "scan_records",
    foreignKeys = [ForeignKey(
        entity = FoodItemEntity::class, parentColumns = ["id"], childColumns = ["foodId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index(value = ["foodId", "scannedAt"]),
        Index(value = ["scannedAt"]),
        Index(value = ["requestId"], unique = true),
        Index(value = ["originalFoodId"])
    ]
)
data class ScanRecordEntity(
    @PrimaryKey val id: String,
    val foodId: String?,
    val originalFoodId: String?,
    val requestId: String,
    val name: String,
    val produceId: String?,
    val category: ProduceCategory,
    val mode: ScanMode,
    val condition: FreshnessCondition,
    val confidence: Float?,
    val imageUri: String?,
    val scannedAt: Instant,
    @Embedded(prefix = "guidance_") val guidance: StoredGuidance?
)
