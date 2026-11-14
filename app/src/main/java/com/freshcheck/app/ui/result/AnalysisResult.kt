package com.freshcheck.app.ui.result

/** These are visual condition labels, never safety or shelf-life classifications. */
enum class FreshnessCondition(val label: String, val symbol: String) {
    Fresh("Fresh", "✓"), UseSoon("Use Soon", "◷"),
    VisiblySpoiled("Visibly Spoiled", "!"), Uncertain("Uncertain / Unable to Assess", "?")
}
enum class ProduceCategory(val label: String) { Fruit("Fruit"), Vegetable("Vegetable"), Unknown("Unknown") }
data class StorageGuidance(
    val location: String, val recheck: String, val handling: String, val reminder: String? = null
)
/**
 * Member 2 maps inference output to this immutable UI contract.
 * Null confidence means unavailable; values must be finite and between 0 and 1.
 * imageUri and analyzedAtLabel are optional; no date or confidence is fabricated.
 * Guidance must come from reviewed produce guidance, never an expiry inferred from a photo.
 */
data class AnalysisResult(
    val produceName: String,
    val category: ProduceCategory,
    val condition: FreshnessCondition,
    val confidence: Float? = null,
    val imageUri: String? = null,
    val analyzedAtLabel: String? = null,
    val guidance: StorageGuidance? = null,
    val isDemo: Boolean = false
) {
    init { require(confidence == null || (confidence.isFinite() && confidence in 0f..1f)) }
}
const val VisualAssessmentNotice = "AI estimates visible external condition only. It cannot confirm food safety, internal spoilage, pathogens or chemical contamination."

/** Dedicated fixtures: must never be used as inference output or stored inventory. */
object DemoResults {
    fun create(condition: FreshnessCondition) = AnalysisResult(
        produceName = if (condition == FreshnessCondition.Uncertain) "Unidentified produce" else "Avocado",
        category = if (condition == FreshnessCondition.Uncertain) ProduceCategory.Unknown else ProduceCategory.Fruit,
        condition = condition,
        confidence = if (condition == FreshnessCondition.Uncertain) null else 0.92f,
        isDemo = true,
        guidance = if (condition == FreshnessCondition.Uncertain || condition == FreshnessCondition.VisiblySpoiled) null
        else StorageGuidance("Sample · Ripen whole avocados at room temperature",
            "Sample · Re-check its condition before preparing; no expiry estimate.",
            "Sample · Once ripe, refrigerate. Keep cut avocado covered and refrigerated.",
            "Optional check-in reminder · scheduling integration pending")
    )
}
