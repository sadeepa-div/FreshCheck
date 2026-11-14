package com.freshcheck.app.ui.myfood
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.result.*
import java.time.Duration

@Composable
fun FreshnessJourneyScreen(item: FoodItemUi, onBack: () -> Unit, onRescan: () -> Unit) {
    val scans = item.scans.sortedBy { it.at }
    Page {
        TextButton(onBack) { Text("← Food Details") }
        SectionTitle("${item.name}'s journey", if (item.isDemo) "DEMONSTRATION HISTORY · SAMPLE SCANS" else "Recorded external condition")
        ProduceImage(item.latest?.imageUri, "Latest scan of ${item.name}", Modifier.fillMaxWidth().height(140.dp))
        if (scans.size < 2) EmptyPanel("A journey takes more than one moment",
            if (scans.isEmpty()) "No scans recorded yet." else "One scan recorded. Add another to compare recorded conditions.", "Rescan Food", onRescan)
        else Panel {
            val previous = scans[scans.lastIndex - 1]
            val latest = scans.last()
            SectionTitle("Two recorded moments")
            Text("Previous · ${previous.condition.label}\nLatest · ${latest.condition.label}")
            val gap = Duration.between(previous.at, latest.at)
            Text("Time between scans · ${gap.toDays()} days, ${gap.toHours() % 24} hours")
            Row {
                Column(Modifier.weight(1f)) { ProduceImage(previous.imageUri, "Previous scan", Modifier.fillMaxWidth().height(90.dp)); Text("Previous scan · image or placeholder") }
                Column(Modifier.weight(1f)) { ProduceImage(latest.imageUri, "Latest scan", Modifier.fillMaxWidth().height(90.dp)); Text("Latest scan · image or placeholder") }
            }
            Text("Unavailable images use illustrations, not scan-image evidence. Confidence changes do not measure freshness deterioration. Two images cannot establish spoilage progression.")
        }
        scans.reversed().forEach { scan -> key(scan.id) { FreshnessTimelineItem(scan, item.isDemo) } }
        Text(VisualAssessmentNotice, style = MaterialTheme.typography.bodySmall)
        ActionButton("Rescan Food", onRescan, Modifier.fillMaxWidth())
    }
}
@Composable
fun FreshnessTimelineItem(scan: FoodScanUi, isDemo: Boolean) {
    var expanded by rememberSaveable(scan.id) { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.height(IntrinsicSize.Min)) {
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            drawLine(color.copy(alpha = .4f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
            drawCircle(color, 5.dp.toPx(), Offset(size.width / 2, 20.dp.toPx()))
        }
        Panel(Modifier.weight(1f).animateContentSize()) {
            Text(scan.at.toString().replace('T', ' '), style = MaterialTheme.typography.labelLarge)
            Text("${scan.condition.symbol}  ${scan.condition.label}", style = MaterialTheme.typography.titleMedium)
            Text(scan.confidence?.let { "${if (isDemo) "Sample " else ""}Model confidence · ${(it * 100).toInt()}%" } ?: "Model confidence unavailable")
            TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide image placeholder" else "Expand image placeholder") }
            if (expanded) { ProduceImage(scan.imageUri, "Scan image", Modifier.fillMaxWidth().height(150.dp)); Text("Scan image or illustration placeholder") }
        }
    }
}
