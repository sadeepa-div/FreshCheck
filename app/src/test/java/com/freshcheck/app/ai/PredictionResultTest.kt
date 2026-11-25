package com.freshcheck.app.ai

import com.freshcheck.app.ai.PredictionResult.FailureReason
import com.freshcheck.app.ui.result.FreshnessCondition
import org.junit.Assert.*
import org.junit.Test

/** Synthetic output scores only; no inference, accuracy evaluation, or saved scans. */
class PredictionResultTest {
    private val labels = listOf("Fresh", "Use Soon", "Visibly Spoiled")

    @Test fun selectsFreshAtIndexZero() {
        val result = success(floatArrayOf(0.8f, 0.15f, 0.05f))
        assertEquals(0, result.predictedIndex)
        assertEquals(FreshnessCondition.Fresh, result.predictedClass)
        assertEquals(0.8f, result.confidence, 0f)
    }

    @Test fun selectsUseSoonAtIndexOne() {
        val result = success(floatArrayOf(0.15f, 0.8f, 0.05f))
        assertEquals(1, result.predictedIndex)
        assertEquals(FreshnessCondition.UseSoon, result.predictedClass)
        assertEquals(0.8f, result.confidence, 0f)
    }

    @Test fun selectsVisiblySpoiledAtIndexTwo() {
        val result = success(floatArrayOf(0.05f, 0.15f, 0.8f))
        assertEquals(2, result.predictedIndex)
        assertEquals(FreshnessCondition.VisiblySpoiled, result.predictedClass)
        assertEquals(0.8f, result.confidence, 0f)
    }

    @Test fun retainsAllScoresAndConfidenceWithoutAnotherSoftmax() {
        val values = floatArrayOf(0.2f, 0.3f, 0.5f)
        val result = success(values)
        assertEquals(values.toList(), result.scores)
        assertEquals(0.5f, result.confidence, 0f)
        assertTrue(result.confidence in 0f..1f)
        assertFalse(result.isTrainingClassMappingVerified)
    }

    @Test fun acceptsZeroAndOneEndpoints() {
        repeat(3) { index ->
            val result = success(FloatArray(3) { if (it == index) 1f else 0f })
            assertEquals(index, result.predictedIndex)
            assertEquals(1f, result.confidence, 0f)
        }
    }

    @Test fun doesNotInventAConfidenceThresholdOrRoundCloseScoresIntoATie() {
        val result = success(floatArrayOf(0.333333f, 0.333334f, 0.333333f))
        assertEquals(FreshnessCondition.UseSoon, result.predictedClass)
        assertEquals(0.333334f, result.confidence, 0f)
    }

    @Test fun snapshotsScoresAndExposesAnUnmodifiableList() {
        val values = floatArrayOf(0.2f, 0.3f, 0.5f)
        val result = success(values)
        values[2] = Float.NaN
        assertEquals(listOf(0.2f, 0.3f, 0.5f), result.scores)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.scores as MutableList<Float>)[2] = 0f
        }
        assertEquals(0.5f, result.confidence, 0f)
    }

    @Test fun rejectsEveryPairOfTiedMaximumScores() {
        for (values in listOf(
            floatArrayOf(0.5f, 0.5f, 0f),
            floatArrayOf(0.5f, 0f, 0.5f),
            floatArrayOf(0f, 0.5f, 0.5f)
        )) {
            assertFailure(FailureReason.TIED_SCORES, values)
        }
    }

    @Test fun rejectsThreeWayTie() {
        assertFailure(FailureReason.TIED_SCORES, FloatArray(3) { 1f / 3f })
    }

    @Test fun allowsTiesBelowTheUniqueMaximum() {
        val result = success(floatArrayOf(0.2f, 0.2f, 0.6f))
        assertEquals(FreshnessCondition.VisiblySpoiled, result.predictedClass)
    }

    @Test fun rejectsMissingOrWrongNumberOfScores() {
        for (values in listOf(null, floatArrayOf(), floatArrayOf(1f),
            floatArrayOf(0.5f, 0.5f), floatArrayOf(1f, 0f, 0f, 0f))) {
            assertFailure(FailureReason.INVALID_SCORES, values)
        }
    }

    @Test fun rejectsNanAndBothInfinitiesAtEveryIndex() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            repeat(3) { index ->
                val values = floatArrayOf(0.2f, 0.3f, 0.5f)
                values[index] = invalid
                assertFailure(FailureReason.INVALID_SCORES, values)
            }
        }
    }

    @Test fun rejectsOutOfRangeScoresAtEveryIndex() {
        for (invalid in listOf(-0.000001f, 1.000001f)) {
            repeat(3) { index ->
                val values = floatArrayOf(0.2f, 0.3f, 0.5f)
                values[index] = invalid
                assertFailure(FailureReason.INVALID_SCORES, values)
            }
        }
    }

    @Test fun rejectsOutOfRangeScoresEvenWhenTheirTotalIsOne() {
        assertFailure(FailureReason.INVALID_SCORES, floatArrayOf(-0.1f, 0.5f, 0.6f))
        assertFailure(FailureReason.INVALID_SCORES, floatArrayOf(1.1f, 0f, -0.1f))
    }

    @Test fun rejectsTotalsFarFromOneWithoutNormalizing() {
        for (values in listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0.1f, 0.2f, 0.3f),
            floatArrayOf(0.5f, 0.6f, 0.7f))) {
            assertFailure(FailureReason.INVALID_SCORES, values)
        }
    }

    @Test fun toleratesSmallFloatSumDifferencesWithoutChangingScores() {
        for (last in listOf(0.69995f, 0.70005f)) {
            val values = floatArrayOf(0.1f, 0.2f, last)
            val result = success(values)
            assertEquals(values.toList(), result.scores)
            assertEquals(last, result.confidence, 0f)
        }
    }

    @Test fun rejectsTotalsBeyondTheDocumentedToleranceInBothDirections() {
        for (last in listOf(0.6998f, 0.7002f)) {
            assertFailure(FailureReason.INVALID_SCORES, floatArrayOf(0.1f, 0.2f, last))
        }
    }

    @Test fun rejectsMissingEmptyBlankOrWrongNumberOfLabels() {
        for (invalid in listOf(null, emptyList(), listOf("Fresh"), listOf("Fresh", "Use Soon"),
            labels + "Uncertain", listOf("Fresh", "", "Visibly Spoiled"),
            listOf("Fresh", "   ", "Visibly Spoiled"))) {
            assertFailure(FailureReason.INVALID_LABELS, floatArrayOf(0.2f, 0.3f, 0.5f), invalid)
        }
    }

    @Test fun rejectsReorderedDuplicateOrUnexpectedLabels() {
        for (invalid in listOf(labels.reversed(), listOf("Use Soon", "Fresh", "Visibly Spoiled"),
            listOf("Fresh", "Fresh", "Fresh"),
            listOf("Fresh", "Use Soon", "Other"))) {
            assertFailure(FailureReason.INVALID_LABELS, floatArrayOf(0.2f, 0.3f, 0.5f), invalid)
        }
    }

    @Test fun representsInferenceFailureWithoutFabricatingAPrediction() {
        val cause = IllegalStateException("Synthetic runtime failure")
        val result: PredictionResult = PredictionResult.Failure(FailureReason.INFERENCE_FAILED, cause)
        assertTrue(result is PredictionResult.Failure)
        assertEquals(FailureReason.INFERENCE_FAILED, (result as PredictionResult.Failure).reason)
        assertSame(cause, result.cause)
    }

    private fun success(values: FloatArray): PredictionResult.Success {
        val result = PredictionResult.fromScores(values, labels)
        assertTrue("Expected success, received $result", result is PredictionResult.Success)
        return result as PredictionResult.Success
    }

    private fun assertFailure(
        reason: FailureReason,
        values: FloatArray?,
        inputLabels: List<String>? = labels
    ) {
        assertEquals(PredictionResult.Failure(reason), PredictionResult.fromScores(values, inputLabels))
    }
}
