package com.freshcheck.app.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.result.*

/** Stateless integration surface. No permission, camera, gallery or inference work occurs here. */
@Composable
fun ScanScreen(
    mode: ScanMode, onMode: (ScanMode) -> Unit,
    state: ScannerUiState, actions: ScannerActions,
    demoCondition: FreshnessCondition, onDemoCondition: (FreshnessCondition) -> Unit,
    onDemo: () -> Unit,
    preview: @Composable BoxScope.() -> Unit = {}
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding().verticalScroll(rememberScrollState())
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Produce scanner", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(actions.onClose) { Text("Close") }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ScanMode.entries.forEachIndexed { index, item ->
                SegmentedButton(selected = mode == item, onClick = { onMode(item) },
                    enabled = !state.isAnalyzing,
                    shape = SegmentedButtonDefaults.itemShape(index, ScanMode.entries.size)) { Text(item.label) }
            }
        }
        AnimatedContent(mode, label = "Scan mode") { Text(it.explanation, style = MaterialTheme.typography.bodyMedium) }
        Box(Modifier.fillMaxWidth().heightIn(min = 300.dp).height(340.dp)
            .background(Color(0xFF10291E), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            preview()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (state.cameraReady) "Position one item inside the frame" else "Camera integration pending",
                    color = Color.White, style = MaterialTheme.typography.labelLarge)
                Box(Modifier.size(210.dp)) {
                    if (!state.cameraReady) ProduceArt(Modifier.fillMaxSize().padding(24.dp))
                    ScanningOverlay(Modifier.fillMaxSize(), animate = !state.isAnalyzing)
                }
                Text(if (state.cameraReady) "Keep the whole item in view" else "Illustration only · no live camera",
                    color = Color(0xFFD9EEDC), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Use even lighting, a plain background and one unobstructed item. Avoid glare and hold steady.",
            style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(actions.onSelectImage, enabled = state.cameraReady && !state.isAnalyzing) { Text("Gallery") }
            FilledIconButton(actions.onCapture, enabled = state.cameraReady && !state.isAnalyzing,
                modifier = Modifier.size(72.dp).semantics { contentDescription = "Capture produce image · camera integration pending" }, shape = CircleShape) { Text("●", style = MaterialTheme.typography.headlineLarge) }
            OutlinedButton({}, enabled = false) { Text("Flash off") }
        }
        Text("Capture and gallery integration pending. Flash is disabled until a real camera controller exists.",
            style = MaterialTheme.typography.bodySmall)
        state.selectedImageLabel?.let { Text("Selected image: $it") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Panel {
            Text("DEMO LAB · SAMPLE DATA", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("Choose a presentation state. No image will be captured or analysed.")
            FreshnessCondition.entries.forEach {
                FilterChip(demoCondition == it, { onDemoCondition(it) }, enabled = !state.isAnalyzing,
                    label = { Text(it.label) })
            }
            if (state.isAnalyzing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Preparing sample analysis… No AI inference is running.")
            } else ActionButton("Run demo analysis", onDemo, Modifier.fillMaxWidth())
        }
        Text(VisualAssessmentNotice, style = MaterialTheme.typography.bodySmall)
    }
    }
}
