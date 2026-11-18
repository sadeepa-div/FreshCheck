package com.freshcheck.app.data

import com.freshcheck.app.data.local.converter.DatabaseConverters
import com.freshcheck.app.data.local.entity.*
import com.freshcheck.app.data.mapper.*
import com.freshcheck.app.ui.insights.FoodOutcome
import com.freshcheck.app.ui.myfood.*
import com.freshcheck.app.ui.result.*
import com.freshcheck.app.ui.scan.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

/** Synthetic fixtures in tests only; never production database seed data. */
class PersistenceMappersTest {
    private val at = Instant.parse("2026-11-18T08:00:00Z")
    private fun item() = FoodItemUi("test-food", "Apple", ProduceCategory.Fruit, 2.0, "pieces",
        FoodStorage.Refrigerated, LocalDate.of(2026, 11, 18), null, emptyList(), produceId = "apple")
    private fun submission() = ScanSubmission(ScanRequest("test-request", ScanMode.BeforeBuying),
        AnalysisResult("Apple", ProduceCategory.Fruit, FreshnessCondition.Uncertain))

    @Test fun convertersRoundTripDatesInstantsNullsAndEveryEnumValue() {
        val c = DatabaseConverters()
        val day = LocalDate.of(2026, 11, 18)
        assertEquals(day, c.longToDate(c.dateToLong(day)))
        assertEquals(at, c.longToInstant(c.instantToLong(at)))
        assertNull(c.longToDate(c.dateToLong(null)))
        assertNull(c.longToInstant(c.instantToLong(null)))
        ProduceCategory.entries.forEach { assertEquals(it, c.stringToCategory(c.categoryToString(it))) }
        FoodStorage.entries.forEach { assertEquals(it, c.stringToStorage(c.storageToString(it))) }
        FreshnessCondition.entries.forEach { assertEquals(it, c.stringToCondition(c.conditionToString(it))) }
        ScanMode.entries.forEach { assertEquals(it, c.stringToMode(c.modeToString(it))) }
        FoodStatus.entries.forEach { assertEquals(it, c.stringToStatus(c.statusToString(it))) }
        FoodOutcome.entries.forEach { assertEquals(it, c.stringToOutcome(c.outcomeToString(it))) }
        ReminderState.entries.forEach { assertEquals(it, c.stringToReminderState(c.reminderStateToString(it))) }
        assertThrows(IllegalArgumentException::class.java) { c.stringToCondition("Safe") }
    }

    @Test fun productionMappersRejectDemoFlagsAndReservedIds() {
        assertThrows(IllegalArgumentException::class.java) { item().copy(isDemo = true).toEntity(at) }
        assertThrows(IllegalArgumentException::class.java) { item().copy(id = "demo-food").toEntity(at) }
        assertThrows(IllegalArgumentException::class.java) { item().copy(produceId = "demo-apple").toEntity(at) }
        assertThrows(IllegalArgumentException::class.java) {
            submission().copy(result = DemoResults.create(FreshnessCondition.Fresh)).toEntity("scan", at)
        }
        assertThrows(IllegalArgumentException::class.java) {
            submission().copy(request = ScanRequest("demo-request", ScanMode.BeforeBuying)).toEntity("scan", at)
        }
    }

    @Test fun scanMappingKeepsNullableFieldsIdentityAndExplicitEventTime() {
        val request = ScanRequest("request", ScanMode.AfterBuying, "food")
        val analysis = AnalysisResult("Apple", ProduceCategory.Fruit, FreshnessCondition.UseSoon,
            confidence = null, imageUri = "content://test/apple", analyzedAtLabel = "Unparseable display text",
            guidance = StorageGuidance("Fridge", "Check manually", "Wash before preparation"), produceId = "apple")
        val stored = ScanSubmission(request, analysis).toEntity("scan", at)
        assertEquals("food", stored.foodId)
        assertEquals("food", stored.originalFoodId)
        assertEquals("request", stored.requestId)
        assertEquals("apple", stored.produceId)
        val ui = stored.toHistory(ZoneId.of("Asia/Colombo"))
        assertEquals(LocalDateTime.of(2026, 11, 18, 13, 30), ui.at)
        assertNull(ui.confidence)
        assertEquals(analysis.guidance, ui.guidance)
        assertEquals(analysis.imageUri, ui.imageUri)
        assertFalse(ui.isDemo)
    }

    @Test fun inventoryMappingIncludesOnlySuppliedScansAndPreservesUnknownState() {
        val food = item().toEntity(at)
        val empty = FoodWithScans(food, emptyList()).toUi(ZoneOffset.UTC)
        assertEquals(FreshnessCondition.Uncertain, empty.condition)
        assertEquals("apple", empty.produceId)
        val first = submission().toEntity("scan-1", at).copy(foodId = food.id)
        val latest = first.copy(id = "scan-2", scannedAt = at.plusSeconds(60), condition = FreshnessCondition.UseSoon)
        val ui = FoodWithScans(food, listOf(latest, first)).toUi(ZoneOffset.UTC)
        assertEquals(listOf("scan-1", "scan-2"), ui.scans.map { it.id })
        assertEquals(FreshnessCondition.UseSoon, ui.condition)
    }
}
