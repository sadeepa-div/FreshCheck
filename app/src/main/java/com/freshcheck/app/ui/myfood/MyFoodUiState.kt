package com.freshcheck.app.ui.myfood

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import com.freshcheck.app.ui.result.*
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

enum class FoodStorage(val label: String) { Refrigerated("Refrigerated"), RoomTemperature("Room Temperature") }
data class FoodScanUi(
    val id: String, val at: LocalDateTime, val condition: FreshnessCondition,
    val confidence: Float? = null, val imageUri: String? = null
) : Serializable
/** UI projection only, not a Room entity. Member 3 maps persisted records into this shape. */
data class FoodItemUi(
    val id: String, val name: String, val category: ProduceCategory,
    val quantity: Double, val unit: String, val storage: FoodStorage,
    val added: LocalDate, val recheck: LocalDate?, val scans: List<FoodScanUi>,
    val notes: String = "", val isDemo: Boolean = false, val produceId: String? = null
) : Serializable {
    val latest get() = scans.maxByOrNull { it.at }
    val condition get() = latest?.condition ?: FreshnessCondition.Uncertain
}
enum class FoodFilter(val label: String) {
    All("All items"), Fruits("Fruits"), Vegetables("Vegetables"), Fresh("Fresh"),
    UseSoon("Use Soon"), Spoiled("Visibly Spoiled"), Refrigerated("Refrigerated"),
    RoomTemperature("Room Temperature")
}
enum class FoodSort(val label: String) {
    Recheck("Nearest re-check"), Recent("Recently added"), Name("Produce name"), Priority("Freshness priority")
}
fun priorityLabel(item: FoodItemUi, today: LocalDate): String = when {
    item.recheck == null -> "No reminder set"
    item.recheck < today -> "Re-check overdue"
    item.recheck == today -> "Re-check today"
    item.recheck <= today.plusDays(2) -> "Check soon"
    else -> "Re-check ${item.recheck}"
}
private fun priorityRank(item: FoodItemUi, today: LocalDate) = when {
    item.condition == FreshnessCondition.VisiblySpoiled -> 0
    item.recheck != null && item.recheck < today -> 1
    item.recheck == today -> 2
    item.condition == FreshnessCondition.UseSoon -> 3
    item.recheck != null && item.recheck <= today.plusDays(2) -> 4
    item.condition == FreshnessCondition.Uncertain -> 5
    else -> 6
}
fun visibleFood(items: List<FoodItemUi>, query: String, filter: FoodFilter, sort: FoodSort, today: LocalDate): List<FoodItemUi> {
    val matching = items.filter {
        (it.name.contains(query.trim(), true) || it.category.label.contains(query.trim(), true)) &&
        when (filter) {
            FoodFilter.All -> true
            FoodFilter.Fruits -> it.category == ProduceCategory.Fruit
            FoodFilter.Vegetables -> it.category == ProduceCategory.Vegetable
            FoodFilter.Fresh -> it.condition == FreshnessCondition.Fresh
            FoodFilter.UseSoon -> it.condition == FreshnessCondition.UseSoon
            FoodFilter.Spoiled -> it.condition == FreshnessCondition.VisiblySpoiled
            FoodFilter.Refrigerated -> it.storage == FoodStorage.Refrigerated
            FoodFilter.RoomTemperature -> it.storage == FoodStorage.RoomTemperature
        }
    }
    val comparator = when (sort) {
        FoodSort.Recheck -> compareBy<FoodItemUi> { it.recheck ?: LocalDate.MAX }
        FoodSort.Recent -> compareByDescending { it.added }
        FoodSort.Name -> compareBy { it.name.lowercase(java.util.Locale.ROOT) }
        FoodSort.Priority -> compareBy<FoodItemUi> { priorityRank(it, today) }.thenBy { it.recheck ?: LocalDate.MAX }
    }
    return matching.sortedWith(comparator.thenBy { it.id })
}
data class FoodEditValues(val quantity: String, val unit: String, val added: String, val recheck: String, val notes: String)
fun validateFoodEdit(values: FoodEditValues, today: LocalDate): Map<String, String> {
    val errors = mutableMapOf<String, String>()
    val quantity = values.quantity.toDoubleOrNull()
    if (quantity == null || !quantity.isFinite() || quantity <= 0 || quantity > 100000) errors["quantity"] = "Enter a quantity greater than 0, up to 100,000."
    if (values.unit.isBlank() || values.unit.length > 24) errors["unit"] = "Enter a unit up to 24 characters."
    val added = runCatching { LocalDate.parse(values.added) }.getOrNull()
    val check = if (values.recheck.isBlank()) null else runCatching { LocalDate.parse(values.recheck) }.getOrNull()
    if (added == null || added > today || added.year < 1900) errors["added"] = "Use YYYY-MM-DD, from 1900 through today."
    if (values.recheck.isNotBlank() && (check == null || check.year !in 1900..9999 || (added != null && check < added)))
        errors["recheck"] = "Use YYYY-MM-DD on or after the added date; blank removes it."
    if (values.notes.length > 500) errors["notes"] = "Keep notes within 500 characters."
    return errors
}
/** Integration boundary: callbacks emit intents, never imply successful persistence. */
data class FoodActions(
    val onOpen: (String) -> Unit, val onEdit: (String) -> Unit,
    val onJourney: (String) -> Unit, val onRescan: (String) -> Unit,
    val onMarkUsed: (String) -> Unit,
    val onMarkDiscarded: (String) -> Unit,
    val onDelete: (String) -> Unit
)
/** Isolated, opt-in UI samples. Saveable only for session restoration, no database or notification effects. */
class DemoInventory(initial: List<FoodItemUi> = emptyList(),
    initialHistory: List<com.freshcheck.app.ui.insights.ScanHistoryRecord> = com.freshcheck.app.ui.insights.inventoryHistory(initial),
    initialOutcomes: List<com.freshcheck.app.ui.insights.OutcomeRecord> = emptyList()) {
    var history by mutableStateOf(initialHistory)
        private set
    var outcomes by mutableStateOf(initialOutcomes)
        private set
    fun markUsed(id: String) = recordOutcome(id, com.freshcheck.app.ui.insights.FoodOutcome.Used)
    fun markDiscarded(id: String) = recordOutcome(id, com.freshcheck.app.ui.insights.FoodOutcome.Discarded)
    private fun recordOutcome(id: String, outcome: com.freshcheck.app.ui.insights.FoodOutcome) {
        val item = items.firstOrNull { it.id == id && it.isDemo } ?: return
        outcomes = outcomes + com.freshcheck.app.ui.insights.OutcomeRecord("demo-outcome-$id", id, item.name,
            item.category, outcome, LocalDateTime.now(), true)
        remove(id)
    }
    var items by mutableStateOf(initial)
        private set
    fun load(today: LocalDate) { items = sampleFood(today); history = com.freshcheck.app.ui.insights.inventoryHistory(items); outcomes = emptyList() }
    fun clear() { items = emptyList(); history = emptyList(); outcomes = emptyList() }
    fun update(item: FoodItemUi) { require(item.isDemo); items = items.map { if (it.id == item.id && it.isDemo) item else it } }
    fun remove(id: String) { items = items.filterNot { it.id == id && it.isDemo } }
    companion object {
        val StateSaver = Saver<DemoInventory, DemoInventorySnapshot>(
            save = { DemoInventorySnapshot(it.items, it.history, it.outcomes) },
            restore = { DemoInventory(it.items, it.history, it.outcomes) })
    }
}
fun sampleFood(today: LocalDate): List<FoodItemUi> {
    fun item(id: String, name: String, cat: ProduceCategory, status: FreshnessCondition, check: LocalDate?, count: Int) =
        FoodItemUi("demo-$id", name, cat, if (cat == ProduceCategory.Fruit) 2.0 else 250.0,
            if (cat == ProduceCategory.Fruit) "pieces" else "g", if (id == "avocado") FoodStorage.RoomTemperature else FoodStorage.Refrigerated,
            today.minusDays(3), check, (0 until count).map { n ->
                FoodScanUi("demo-$id-scan-$n", today.minusDays((2 - n).toLong()).atTime(10, 30),
                    if (n == count - 1) status else FreshnessCondition.Fresh, .91f - n * .03f)
            }, isDemo = true)
    return listOf(
        item("avocado", "Avocado", ProduceCategory.Fruit, FreshnessCondition.UseSoon, today.minusDays(1), 2),
        item("spinach", "Spinach", ProduceCategory.Vegetable, FreshnessCondition.Fresh, today, 1),
        item("apple", "Apple", ProduceCategory.Fruit, FreshnessCondition.Fresh, today.plusDays(2), 2),
        item("tomato", "Tomato", ProduceCategory.Fruit, FreshnessCondition.VisiblySpoiled, null, 2),
        item("carrot", "Carrot", ProduceCategory.Vegetable, FreshnessCondition.Uncertain, null, 0))
}

data class DemoInventorySnapshot(
    val items: List<FoodItemUi>,
    val history: List<com.freshcheck.app.ui.insights.ScanHistoryRecord>,
    val outcomes: List<com.freshcheck.app.ui.insights.OutcomeRecord>
) : Serializable
