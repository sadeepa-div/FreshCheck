package com.freshcheck.app.ai

import com.freshcheck.app.ui.result.FreshnessCondition
import java.util.Collections
import kotlin.math.abs

/**
 * Model output handling only; does not execute inference or create a UI/persistence result.
 * Scores are already softmax outputs. Never normalize or apply softmax here again.
 * A score is not calibrated accuracy, produce identification, or evidence of food safety.
 */
sealed interface PredictionResult {
    /** A unique argmax with valid scores, using the provisional labels.txt order. */
    class Success private constructor(values: FloatArray, val predictedIndex: Int) : PredictionResult {
        /** Immutable snapshot in model output order: Fresh, Use Soon, Visibly Spoiled. */
        val scores: List<Float> = Collections.unmodifiableList(values.toList())
        val predictedClass: FreshnessCondition = provisionalClasses[predictedIndex]

        /** The winning score, unchanged, on the same 0–1 scale as AnalysisResult.confidence. */
        val confidence: Float = scores[predictedIndex]

        /** Training/export indices and reference predictions have NOT verified this mapping. */
        val isTrainingClassMappingVerified: Boolean = false

        companion object {
            internal fun fromScores(outputScores: FloatArray?, labels: List<String>?): PredictionResult {
                // Require the sidecar labels explicitly; never substitute labels for a missing asset.
                if (labels?.toList() != provisionalClasses.map { it.label }) {
                    return Failure(FailureReason.INVALID_LABELS)
                }
                val values = outputScores?.copyOf()
                    ?: return Failure(FailureReason.INVALID_SCORES)
                if (values.size != 3 || values.any { !it.isFinite() || it !in 0f..1f } ||
                    abs(values.sumOf { it.toDouble() } - 1.0) > SCORE_SUM_TOLERANCE
                ) {
                    return Failure(FailureReason.INVALID_SCORES)
                }
                val winner = values.indices.maxBy { values[it] }
                // An exact tie has no unique prediction; never silently prefer the Fresh index.
                if (values.count { it == values[winner] } != 1) {
                    return Failure(FailureReason.TIED_SCORES)
                }
                return Success(values, winner)
            }
        }
    }

    /** Technical failures carry no fabricated class or confidence. No exception is required. */
    data class Failure(val reason: FailureReason, val cause: Throwable? = null) : PredictionResult

    enum class FailureReason {
        INVALID_SCORES,
        INVALID_LABELS,
        TIED_SCORES,
        INVALID_INPUT,
        MODEL_LOADING_FAILED,
        INFERENCE_FAILED
    }

    companion object {
        /** Absolute tolerance on the score sum for FLOAT32 rounding, not a confidence threshold. */
        const val SCORE_SUM_TOLERANCE: Double = 0.0001

        private val provisionalClasses = listOf(
            FreshnessCondition.Fresh,
            FreshnessCondition.UseSoon,
            FreshnessCondition.VisiblySpoiled
        )

        /**
         * [labels] must be the three non-empty lines from labels.txt, in their existing order.
         * Missing, blank, reordered or unexpected labels fail explicitly. Class-index meaning
         * remains UNVERIFIED even when this sidecar check passes. No threshold is applied.
         * Invalid outputs and exact ties return Failure; valid inputs are copied, not altered.
         */
        fun fromScores(outputScores: FloatArray?, labels: List<String>?): PredictionResult =
            Success.fromScores(outputScores, labels)
    }
}
