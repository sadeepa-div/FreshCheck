package com.freshcheck.app.ui.insights

import com.freshcheck.app.ui.myfood.FoodItemUi
import com.freshcheck.app.ui.result.*
import com.freshcheck.app.ui.scan.ScanMode
import java.time.LocalDate
import java.time.LocalDateTime

/** UI projections. Member 3 supplies records; these are not Room entities. */
enum class FoodOutcome { Used, Discarded }
data class OutcomeRecord(val id: String, val foodId: String, val name: String, val category: ProduceCategory,
    val outcome: FoodOutcome, val at: LocalDateTime, val isDemo: Boolean = false)
data class ScanHistoryRecord(val id: String, val name: String, val category: ProduceCategory,
    val at: LocalDateTime, val condition: FreshnessCondition, val confidence: Float?,
    val mode: ScanMode?, val foodId: String? = null, val imageUri: String? = null,
    val guidance: StorageGuidance? = null, val isDemo: Boolean = false)
enum class InsightPeriod(val label: String) { Week("7 days"), Month("30 days"), All("All time") }
data class ActivitySummary(val tracked: Int, val scans: Int, val used: Int, val discarded: Int,
    val activeNow: Int, val attentionNow: Int) {
    val usagePercentage: Float? get() = if (used + discarded == 0) null else used * 100f / (used + discarded)
}
fun periodIncludes(date: LocalDate, today: LocalDate, period: InsightPeriod): Boolean =
    date <= today && when (period) {
        InsightPeriod.Week -> date >= today.minusDays(6)
        InsightPeriod.Month -> date >= today.minusDays(29)
        InsightPeriod.All -> true
    }
fun recordedOutcomes(outcomes: List<OutcomeRecord>, today: LocalDate, period: InsightPeriod) =
    outcomes.filter { it.at.toLocalDate() <= today }.groupBy { it.foodId }.values
        .map { events -> events.maxBy { it.at } }.filter { periodIncludes(it.at.toLocalDate(), today, period) }
fun activitySummary(food: List<FoodItemUi>, history: List<ScanHistoryRecord>, outcomes: List<OutcomeRecord>,
    today: LocalDate, period: InsightPeriod): ActivitySummary {
    val events = recordedOutcomes(outcomes, today, period)
    return ActivitySummary(
        (food.filter { periodIncludes(it.added, today, period) }.map { it.id } + events.map { it.foodId }).distinct().size,
        history.distinctBy { it.id }.count { periodIncludes(it.at.toLocalDate(), today, period) },
        events.count { it.outcome == FoodOutcome.Used }, events.count { it.outcome == FoodOutcome.Discarded },
        food.distinctBy { it.id }.size,
        food.distinctBy { it.id }.count { it.condition != FreshnessCondition.Fresh || (it.recheck != null && it.recheck <= today) })
}
fun inventoryHistory(food: List<FoodItemUi>) = food.flatMap { item -> item.scans.map { scan ->
    ScanHistoryRecord(scan.id, item.name, item.category, scan.at, scan.condition, scan.confidence,
        mode = null, foodId = item.id, imageUri = scan.imageUri, isDemo = item.isDemo)
} }.distinctBy { it.id }
fun filterHistory(history: List<ScanHistoryRecord>, query: String, category: String, condition: String,
    mode: String, newest: Boolean): List<ScanHistoryRecord> {
    val result = history.filter {
        it.name.contains(query.trim(), true) && (category == "All categories" || it.category.label == category) &&
        (condition == "All conditions" || it.condition.label == condition) &&
        (mode == "All modes" || (it.mode?.label ?: "Not recorded") == mode)
    }.distinctBy { it.id }
    return if (newest) result.sortedWith(compareByDescending<ScanHistoryRecord> { it.at }.thenBy { it.id })
    else result.sortedWith(compareBy<ScanHistoryRecord> { it.at }.thenBy { it.id })
}
fun sampleOutcomes(today: LocalDate) = listOf(
    OutcomeRecord("demo-used-1", "demo-outcome-1", "Banana", ProduceCategory.Fruit, FoodOutcome.Used, today.minusDays(1).atTime(12, 0), true),
    OutcomeRecord("demo-used-2", "demo-outcome-2", "Spinach", ProduceCategory.Vegetable, FoodOutcome.Used, today.minusDays(3).atTime(12, 0), true),
    OutcomeRecord("demo-discard", "demo-outcome-3", "Tomato", ProduceCategory.Fruit, FoodOutcome.Discarded, today.minusDays(5).atTime(12, 0), true),
    OutcomeRecord("demo-old", "demo-outcome-4", "Apple", ProduceCategory.Fruit, FoodOutcome.Used, today.minusDays(20).atTime(12, 0), true)
)
fun sampleHistory(today: LocalDate) = listOf(
    ScanHistoryRecord("demo-history-avocado", "Avocado", ProduceCategory.Fruit, today.atTime(10, 0),
        FreshnessCondition.UseSoon, .88f, ScanMode.AfterBuying, isDemo = true),
    ScanHistoryRecord("demo-history-carrot", "Carrot", ProduceCategory.Vegetable, today.minusDays(2).atTime(9, 0),
        FreshnessCondition.Uncertain, null, ScanMode.BeforeBuying, isDemo = true)
)
