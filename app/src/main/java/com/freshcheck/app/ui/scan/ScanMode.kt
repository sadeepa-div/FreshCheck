package com.freshcheck.app.ui.scan
import com.freshcheck.app.ui.result.AnalysisResult

enum class ScanMode(val label: String, val explanation: String) {
    BeforeBuying("Before Buying", "Explore visible external condition before purchase. A photograph cannot reveal internal quality or food safety."),
    AfterBuying("After Buying", "Explore purchased produce and plan a later check-in. Saving to My Food will require inventory integration.")
}
/**
 * Member 2 owns camera lifecycle, permissions, image selection and inference.
 * Supply state and event callbacks to ScanScreen; render the actual preview in its preview slot.
 * Keep cameraReady false until a real controller is bound. Loading and errors are explicit.
 */
data class ScannerUiState(
    val cameraReady: Boolean = false,
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val selectedImageLabel: String? = null,
    val result: AnalysisResult? = null
)
data class ScannerActions(
    val onCapture: () -> Unit,
    val onSelectImage: () -> Unit,
    val onClose: () -> Unit
)

/** Request identity belongs to the coordinator. Member 2 must echo it in completed work. */
data class ScanRequest(val requestId: String, val mode: ScanMode, val rescanFoodId: String? = null)
/** scannedAt is Member 2's actual event instant; null keeps legacy/demo callers display-only. */
data class ScanSubmission(val request: ScanRequest, val result: AnalysisResult,
    val scannedAt: java.time.Instant? = null) {
    val rescanFoodId get() = request.rescanFoodId
}

/** Reject late completions, changed modes and changed food associations. */
fun analysisFor(request: ScanRequest, completed: ScanSubmission?): AnalysisResult? =
    completed?.takeIf { it.request == request }?.result
