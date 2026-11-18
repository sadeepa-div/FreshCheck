package com.freshcheck.app.ui.result

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.scan.ScanMode
import com.freshcheck.app.ui.theme.FreshCheckTheme

/** Member 3 owns save/reminder side effects. UI callbacks must report actual completion before claiming success. */
data class ResultActions(
    val onScanAgain: () -> Unit,
    val onHome: () -> Unit,
    val onSave: () -> Unit,
    val onNutrition: () -> Unit,
    val onReminder: () -> Unit
)
@Composable
fun AnalysisResultScreen(
    result: AnalysisResult, mode: ScanMode, actions: ResultActions,
    rescanFoodId: String? = null,
    image: @Composable (String?) -> Unit = {
        ProduceImage(it, "Produce scan image", Modifier.fillMaxWidth().height(190.dp))
        Text(if (it == null) "Produce illustration · no photo supplied" else "Selected scan image · local URI",
            style = MaterialTheme.typography.bodySmall)
    }
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Page {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(actions.onScanAgain) { Text("← Scanner") }
            TextButton(actions.onHome) { Text("Home") }
        }
        SectionTitle("A closer look at your produce", mode.label)
        if (result.isDemo) Panel {
            Text("DEMONSTRATION · SAMPLE DATA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("No photo was captured and no AI model was run. All values below are illustrative.")
        }
        image(result.imageUri)
        SectionTitle(result.produceName, result.category.label)
        rescanFoodId?.let { Text("Rescan for food item · $it", style = MaterialTheme.typography.labelLarge) }
        result.analyzedAtLabel?.let { Text("Analysed: $it", style = MaterialTheme.typography.bodySmall) }
        FreshnessStatusCard(result.condition)
        Panel {
            Text("What this assessment means", style = MaterialTheme.typography.titleMedium)
            Text(VisualAssessmentNotice, style = MaterialTheme.typography.bodyMedium)
        }
        ConfidenceIndicator(result.confidence, result.isDemo)
        StorageGuidanceCard(result, expanded)
        if (result.guidance != null && result.condition != FreshnessCondition.VisiblySpoiled && result.condition != FreshnessCondition.Uncertain)
            TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide Storage Tips" else "View Storage Tips") }
        ActionButton("Scan Again", actions.onScanAgain, Modifier.fillMaxWidth())
        if (mode == ScanMode.AfterBuying || !result.isDemo) OutlinedButton(actions.onSave, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(when {
                mode == ScanMode.BeforeBuying -> "Save scan to history"
                rescanFoodId != null -> "Save rescan"
                else -> "Save to My Food · pending"
            })
        } else Text("Before Buying · view this assessment without saving.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(actions.onNutrition, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("View Nutrition") }
        if (result.condition != FreshnessCondition.VisiblySpoiled && result.condition != FreshnessCondition.Uncertain) {
            OutlinedButton(actions.onReminder, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Set Reminder · pending") }
        }
    }
    }
}
@Preview(name = "Fresh sample · small phone", widthDp = 320, heightDp = 700)
@Composable
private fun FreshPreview() { ResultPreview(FreshnessCondition.Fresh) }
@Preview(name = "Use Soon sample", widthDp = 360, heightDp = 800)
@Composable
private fun UseSoonPreview() { ResultPreview(FreshnessCondition.UseSoon) }
@Preview(name = "Visibly Spoiled sample", widthDp = 360, heightDp = 800)
@Composable
private fun SpoiledPreview() { ResultPreview(FreshnessCondition.VisiblySpoiled) }
@Preview(name = "Uncertain sample · dark", widthDp = 360, heightDp = 800)
@Composable
private fun UncertainPreview() { FreshCheckTheme(darkTheme = true) {
    AnalysisResultScreen(DemoResults.create(FreshnessCondition.Uncertain), ScanMode.BeforeBuying, ResultActions({}, {}, {}, {}, {}))
} }
@Composable
private fun ResultPreview(condition: FreshnessCondition) {
    FreshCheckTheme { AnalysisResultScreen(DemoResults.create(condition), ScanMode.AfterBuying, ResultActions({}, {}, {}, {}, {})) }
}
