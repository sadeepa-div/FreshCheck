package com.freshcheck.app.data.mapper

import com.freshcheck.app.data.local.entity.*
import com.freshcheck.app.ui.insights.OutcomeRecord
import com.freshcheck.app.ui.insights.ScanHistoryRecord
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.result.StorageGuidance
import com.freshcheck.app.ui.scan.ScanSubmission
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Production boundaries reject both demo flags and reserved demo IDs. */
fun requireProductionId(id: String) {
    require(id.isNotBlank() && !id.startsWith("demo-", ignoreCase = true)) { "A non-demo stable ID is required." }
}

fun FoodItemUi.toEntity(at: Instant): FoodItemEntity {
    require(!isDemo) { "Demonstration food cannot be persisted." }
    requireProductionId(id)
    produceId?.let(::requireProductionId)
    return FoodItemEntity(id, name, produceId, category, quantity, unit, storage, added,
        recheck, notes, FoodStatus.Active, at, at)
}

fun ScanSubmission.toEntity(id: String, scannedAt: Instant): ScanRecordEntity {
    require(!result.isDemo) { "Demonstration analysis cannot be persisted." }
    requireProductionId(id)
    requireProductionId(request.requestId)
    request.rescanFoodId?.let(::requireProductionId)
    result.produceId?.let(::requireProductionId)
    require(result.produceName.isNotBlank()) { "Produce name is required." }
    return ScanRecordEntity(
        id, request.rescanFoodId, request.rescanFoodId, request.requestId,
        result.produceName, result.produceId, result.category, request.mode, result.condition,
        result.confidence, result.imageUri, scannedAt,
        result.guidance?.let { StoredGuidance(it.location, it.recheck, it.handling, it.reminder) }
    )
}

fun ScanRecordEntity.toHistory(zone: ZoneId): ScanHistoryRecord = ScanHistoryRecord(
    id, name, category, LocalDateTime.ofInstant(scannedAt, zone), condition, confidence,
    mode, foodId, imageUri, guidance?.let { StorageGuidance(it.location, it.recheck, it.handling, it.reminder) },
    isDemo = false
)

fun FoodWithScans.toUi(zone: ZoneId): FoodItemUi = with(food) {
    FoodItemUi(id, name, category, quantity, unit, storage, added, recheck,
        scans.sortedWith(compareBy<ScanRecordEntity> { it.scannedAt }.thenBy { it.id }).map {
            FoodScanUi(it.id, LocalDateTime.ofInstant(it.scannedAt, zone), it.condition, it.confidence, it.imageUri)
        }, notes, isDemo = false, produceId = produceId)
}

fun FoodOutcomeEntity.toUi(zone: ZoneId): OutcomeRecord =
    OutcomeRecord(id, foodId, name, category, outcome, LocalDateTime.ofInstant(occurredAt, zone), isDemo = false)
