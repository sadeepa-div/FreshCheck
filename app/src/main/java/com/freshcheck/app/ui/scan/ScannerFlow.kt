package com.freshcheck.app.ui.scan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.freshcheck.app.ui.result.*
import com.freshcheck.app.ui.theme.FreshCheckTheme
import kotlinx.coroutines.delay
import com.freshcheck.app.navigation.UiOperationResult

/**
 * Explicit demo-only coordinator. Replace this adapter with Member 2's lifecycle-aware state owner.
 * Keep ScanScreen and AnalysisResultScreen stateless for real state and asynchronous callbacks.
 */
@Composable
fun ScannerFlow(modeLabel: String, onMode: (String) -> Unit, onClose: () -> Unit, onNutrition: () -> Unit,
    onHome: () -> Unit = onClose,
    request: ScanRequest = ScanRequest("demo-request", ScanMode.entries.firstOrNull { it.label == modeLabel } ?: ScanMode.BeforeBuying),
    providedResult: AnalysisResult? = null,
    onNutritionForProduce: (String?) -> Unit = { onNutrition() },
    onNewRequest: () -> Unit = {},
    onSubmit: (ScanSubmission, (UiOperationResult) -> Unit) -> Unit = { _, done -> done(UiOperationResult.Failed("Scan persistence integration pending. Nothing saved.")) }) {
    val mode = ScanMode.entries.firstOrNull { it.label == modeLabel } ?: ScanMode.BeforeBuying
    val cameraPermission = rememberCameraPermissionHandler()
    var stage by rememberSaveable { mutableStateOf("scanner") }
    var conditionName by rememberSaveable { mutableStateOf(FreshnessCondition.Fresh.name) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val condition = FreshnessCondition.valueOf(conditionName)
    val result = providedResult ?: DemoResults.create(condition)
    val submission = ScanSubmission(request.copy(mode = mode), result)
    var saving by remember { mutableStateOf(false) }
    // Cancellation on exit prevents stale completion after closing; rotation safely restarts demo preparation.
    LaunchedEffect(stage) {
        if (stage == "loading") { delay(1400); stage = "result" }
    }
    BackHandler {
        if (message != null) message = null
        else if (stage != "scanner") stage = "scanner"
        else onClose()
    }
    AnimatedContent(providedResult != null || stage == "result", label = "Demo analysis result") { showResult ->
        if (showResult) Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AnalysisResultScreen(result, mode, ResultActions(
                onScanAgain = { stage = "scanner"; onNewRequest() }, onHome = onHome,
                onSave = {
                    if (result.isDemo) message = "Inventory integration pending. This sample has not been saved."
                    else if (!saving) {
                        saving = true
                        onSubmit(submission) { outcome ->
                            saving = false
                            message = when (outcome) {
                                UiOperationResult.Confirmed -> "Repository confirmed scan persistence."
                                is UiOperationResult.Failed -> outcome.message
                            }
                        }
                    }
                },
                onNutrition = { onNutritionForProduce(result.produceId) },
                onReminder = { message = "Reminder integration pending. No reminder has been scheduled." }
            ), rescanFoodId = submission.rescanFoodId)
        } else ScanScreen(mode, { onMode(it.label) },
            ScannerUiState(isAnalyzing = stage == "loading"),
            ScannerActions({}, { message = "Gallery selection is not connected yet. No image was selected." }, onClose),
            condition, { conditionName = it.name },
            onDemo = { stage = "loading" },
            permissionContent = { CameraPermissionPanel(cameraPermission, enabled = stage != "loading") })
    }
    message?.let {
        AlertDialog(onDismissRequest = { message = null }, title = { Text("Coming soon") },
            text = { Text(it) }, confirmButton = { TextButton({ message = null }) { Text("Got it") } })
    }
}
@Preview(name = "Scanner · small portrait", widthDp = 320, heightDp = 720)
@Composable
private fun ScannerPreview() {
    FreshCheckTheme {
        ScanScreen(ScanMode.BeforeBuying, {}, ScannerUiState(), ScannerActions({}, {}, {}),
            FreshnessCondition.Fresh, {}, {})
    }
}
