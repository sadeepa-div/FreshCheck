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
