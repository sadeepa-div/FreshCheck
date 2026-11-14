package com.freshcheck.app.ui.result

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.Panel

@Composable
fun FreshnessStatusCard(condition: FreshnessCondition) {
    val colors = MaterialTheme.colorScheme
    val tone = when (condition) {
        FreshnessCondition.Fresh -> colors.primary
        FreshnessCondition.UseSoon -> colors.tertiary
        FreshnessCondition.VisiblySpoiled -> colors.error
        FreshnessCondition.Uncertain -> colors.onSurfaceVariant
    }
    Surface(color = tone.copy(alpha = .12f), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${condition.symbol}  ${condition.label}", color = tone, style = MaterialTheme.typography.titleLarge)
            Text(when (condition) {
                FreshnessCondition.Fresh -> "External appearance is classified as fresh. This does not establish food safety."
                FreshnessCondition.UseSoon -> "Visible changes may be present. Re-check manually before handling or preparing."
                FreshnessCondition.VisiblySpoiled -> "Visible signs of spoilage. Do not consume visibly spoiled produce."
                FreshnessCondition.Uncertain -> "Take another clear image in even lighting. Use appropriate manual judgement; the image could not be reliably assessed."
            }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable
fun ConfidenceIndicator(confidence: Float?, isDemo: Boolean) {
    if (confidence == null) {
        Text("Model confidence unavailable", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    var entered by remember(confidence) { mutableStateOf(preview) }
    LaunchedEffect(confidence) { entered = true }
    val progress by animateFloatAsState(if (entered) confidence else 0f, label = "Model confidence")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(88.dp).clearAndSetSemantics {
            contentDescription = "${if (isDemo) "Sample " else ""}Model confidence ${(confidence * 100).toInt()} percent"
        }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceContainer)
            Text("${(confidence * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f)) {
            Text(if (isDemo) "Sample model confidence" else "Model confidence", style = MaterialTheme.typography.titleMedium)
            Text("Confidence in the visual classification. Not food safety or remaining shelf life.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
fun StorageGuidanceCard(result: AnalysisResult, expanded: Boolean) {
    Panel {
        Text("Storage & care", style = MaterialTheme.typography.titleLarge)
        when (result.condition) {
            FreshnessCondition.VisiblySpoiled -> Text("Do not consume visibly spoiled produce. Storage guidance and use reminders are not applicable.")
            FreshnessCondition.Uncertain -> Text("Storage guidance unavailable until the produce can be identified. Take another clear image and check manually.")
            else -> {
                val guidance = result.guidance
                if (guidance == null) Text("Produce-specific guidance integration pending.")
                else {
                    Text(guidance.location, style = MaterialTheme.typography.titleMedium)
                    Text(guidance.recheck)
                    if (expanded) {
                        Text(guidance.handling)
                        guidance.reminder?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        Text("No expiry date is inferred from this image.", style = MaterialTheme.typography.bodySmall)
    }
}
