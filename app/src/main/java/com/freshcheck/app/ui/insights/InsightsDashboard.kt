package com.freshcheck.app.ui.insights
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.myfood.FoodItemUi
import java.time.LocalDate

@Composable
fun InsightsDashboard(food: List<FoodItemUi>, history: List<ScanHistoryRecord>,
    onTopic: (String) -> Unit, onInventory: () -> Unit, onHistory: () -> Unit,
    suppliedOutcomes: List<OutcomeRecord> = emptyList()) {
    var periodName by rememberSaveable { mutableStateOf(InsightPeriod.Week.name) }
    var demo by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val period = InsightPeriod.valueOf(periodName)
    val outcomes = if (demo) sampleOutcomes(today) else suppliedOutcomes
    val summary = activitySummary(food, history, outcomes, today, period)
    val events = recordedOutcomes(outcomes, today, period)
    Page {
        SectionTitle("FreshCheck Insights", "Notice your habits. Plan a little less waste.")
        Panel {
            Text("Every recorded choice tells a story.", style = MaterialTheme.typography.headlineMedium)
            Text("Tracked item counts describe app records, not measured environmental savings.")
        }
        InsightPeriod.entries.forEach { FilterChip(period == it, { periodName = it.name }, label = { Text(it.label) }) }
        Row {
            Text("Explore sample outcome activity", Modifier.weight(1f))
            Switch(demo, { demo = it }, Modifier.semantics { contentDescription = "Explore sample outcome activity" })
        }
        if (demo || food.any { it.isDemo } || history.any { it.isDemo })
            Text("DEMONSTRATION DATA INCLUDED · Sample records are not real user activity.", color = MaterialTheme.colorScheme.primary)
        Panel {
            Text("Selected period · ${period.label}", style = MaterialTheme.typography.titleMedium)
            Text("Tracked items · ${summary.tracked}\nScans · ${summary.scans}\nUsed · ${summary.used}\nDiscarded · ${summary.discarded}")
            Text("Active now · ${summary.activeNow}\nRe-check / attention now · ${summary.attentionNow}")
            Text("Current inventory counts are snapshots, not restricted by the period. Period tracked count includes available inventory additions and recorded outcome item IDs.",
                style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            SectionTitle("Tracked-item usage")
            val score = summary.usagePercentage
            if (score == null) Text("No recorded outcomes yet. A percentage will appear after Used or Discarded is recorded.")
            else {
                Text("${score.toInt()}%", style = MaterialTheme.typography.headlineLarge)
                Text("Used ÷ (Used + Discarded) × 100. Item counts only; not a verified environmental impact score.")
            }
            CountChart("Used versus Discarded · items", listOf("Used" to summary.used, "Discarded" to summary.discarded))
        }
        Panel {
            SectionTitle("Outcome trends")
            val groups = events.groupBy { event ->
                val date = event.at.toLocalDate()
                if (period == InsightPeriod.All) date.toString().take(7)
                else if (period == InsightPeriod.Month) "Week of ${date.minusDays((date.dayOfWeek.value - 1).toLong())}"
                else date.toString()
            }.toSortedMap()
            if (groups.isEmpty()) Text("No outcome activity in this period.")
            groups.forEach { (label, records) ->
                CountChart(label, listOf("Used" to records.count { it.outcome == FoodOutcome.Used }, "Discarded" to records.count { it.outcome == FoodOutcome.Discarded }))
            }
        }
        Panel {
            CountChart("Produce categories added · items", com.freshcheck.app.ui.result.ProduceCategory.entries.map { category ->
                category.label to food.count { it.category == category && periodIncludes(it.added, today, period) }
            })
            CountChart("Current inventory attention · items", listOf("Needs attention" to summary.attentionNow, "Other" to (summary.activeNow - summary.attentionNow)))
        }
        SectionTitle("Recent recorded activity")
        if (events.isEmpty()) Text("No Used or Discarded activity recorded.")
        events.sortedByDescending { it.at }.take(5).forEach {
            Text("${if (it.isDemo) "Sample · " else ""}${it.name} · ${it.outcome} · ${it.at.toString().replace('T', ' ')}")
        }
        ActionButton("Open My Food", onInventory, Modifier.fillMaxWidth())
        ActionButton("Scan History", onHistory, Modifier.fillMaxWidth())
        ActionButton("Nutrition Dashboard", { onTopic("Nutrition") }, Modifier.fillMaxWidth())
        ActionButton("Smart Meals", { onTopic("Meals") }, Modifier.fillMaxWidth())
        ActionButton("Rescue My Food", { onTopic("Rescue") }, Modifier.fillMaxWidth())
    }
}
@Composable
fun CountChart(title: String, values: List<Pair<String, Int>>) {
    var selected by rememberSaveable(title) { mutableStateOf<String?>(null) }
    val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val color = MaterialTheme.colorScheme.primary
    Text(title, style = MaterialTheme.typography.titleMedium)
    values.forEach { (label, count) ->
        val ratio by animateFloatAsState(count.toFloat() / max, label = "Count chart")
        TextButton({ selected = "$label · $count items" }, Modifier.fillMaxWidth()) { Text("$label · $count items") }
        Canvas(Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics {
            contentDescription = "$label: $count items"
        }) {
            drawRoundRect(color.copy(alpha = .12f), size = size)
            drawRoundRect(color, Offset.Zero, Size(size.width * ratio, size.height))
        }
    }
    selected?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
}
