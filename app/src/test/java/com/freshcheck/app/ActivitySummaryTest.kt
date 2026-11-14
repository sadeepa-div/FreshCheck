package com.freshcheck.app
import com.freshcheck.app.ui.insights.*
import com.freshcheck.app.ui.myfood.sampleFood
import com.freshcheck.app.ui.result.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ActivitySummaryTest {
    private val today = LocalDate.of(2026, 11, 14)
    @Test fun calculatesUsageOnlyWithRecordedOutcomes() {
        val empty = activitySummary(emptyList(), emptyList(), emptyList(), today, InsightPeriod.All)
        assertNull(empty.usagePercentage)
        val summary = activitySummary(emptyList(), emptyList(), sampleOutcomes(today), today, InsightPeriod.Week)
        assertEquals(2, summary.used); assertEquals(1, summary.discarded)
        assertEquals(66.666f, summary.usagePercentage!!, .01f)
        assertEquals(3, summary.tracked)
    }
    @Test fun periodBoundariesIncludeSevenCalendarDaysAndExcludeFuture() {
        assertTrue(periodIncludes(today.minusDays(6), today, InsightPeriod.Week))
        assertFalse(periodIncludes(today.minusDays(7), today, InsightPeriod.Week))
        assertFalse(periodIncludes(today.plusDays(1), today, InsightPeriod.All))
        assertEquals(4, recordedOutcomes(sampleOutcomes(today), today, InsightPeriod.Month).size)
    }
    @Test fun latestOutcomePerFoodAvoidsDoubleCounting() {
        val used = sampleOutcomes(today).first()
        val corrected = used.copy(id = "corrected", outcome = FoodOutcome.Discarded, at = today.atTime(12, 0))
        val summary = activitySummary(emptyList(), emptyList(), listOf(used, corrected), today, InsightPeriod.All)
        assertEquals(0, summary.used); assertEquals(1, summary.discarded)
    }
    @Test fun currentInventoryAndUniqueHistoryAreKeptSeparateFromPeriod() {
        val food = sampleFood(today)
        val scans = inventoryHistory(food)
        val summary = activitySummary(food, scans + scans, emptyList(), today, InsightPeriod.Week)
        assertEquals(5, summary.activeNow); assertEquals(scans.size, summary.scans)
        assertEquals(4, summary.attentionNow)
        assertTrue(scans.all { it.isDemo && it.foodId != null })
        assertTrue(scans.all { it.mode == null }) // no fabricated mode for older records
    }
    @Test fun historyFiltersAndSortWorkTogether() {
        val scans = sampleHistory(today)
        assertEquals(1, filterHistory(scans, "avocado", "Fruit", "Use Soon", "After Buying", true).size)
        assertTrue(filterHistory(scans, "", "Vegetable", "Fresh", "All modes", true).isEmpty())
        assertEquals("Carrot", filterHistory(scans, "", "All categories", "All conditions", "All modes", false).first().name)
        assertEquals("Avocado", filterHistory(scans, "", "All categories", "All conditions", "All modes", true).first().name)
        assertTrue(filterHistory(emptyList(), "", "All categories", "All conditions", "All modes", true).isEmpty())
    }
}
