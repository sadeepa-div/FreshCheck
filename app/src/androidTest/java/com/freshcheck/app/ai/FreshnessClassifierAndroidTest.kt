package com.freshcheck.app.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.DataType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Actual bundled-model execution with SYNTHETIC inputs; no accuracy/produce/safety claims. */
@RunWith(AndroidJUnit4::class)
class FreshnessClassifierAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun loadsBundledModelValidatesRuntimeTensorsAndExecutesControlledInputs() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            worker.submit {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                loadBundledClassifierEngine(context.assets).use { engine ->
                    assertEquals(listOf("Fresh", "Use Soon", "Visibly Spoiled"), engine.labels)
                    assertEquals(listOf(ClassifierTensor(listOf(1, 224, 224, 3), DataType.FLOAT32)), engine.inputs)
                    assertEquals(listOf(ClassifierTensor(listOf(1, 3), DataType.FLOAT32)), engine.outputs)
                    validateClassifierTensors(engine.inputs, engine.outputs)
                    for (intensity in listOf(0f, 127.5f, 255f)) {
                        val scores = engine.run(controlledInput(intensity))
                        assertEquals(3, scores.size)
                        assertTrue(scores.all { it.isFinite() && it in 0f..1f })
                        assertTrue(abs(scores.sumOf { it.toDouble() } - 1.0) <= PredictionResult.SCORE_SUM_TOLERANCE)
                        assertValidPrediction(PredictionResult.fromScores(scores, engine.labels))
                    }
                }
            }.get(90, TimeUnit.SECONDS)
        } finally { worker.shutdownNow() }
    }

    @Test fun classifiesPreprocessorOutputFromMainThreadAndClosesSafely() {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val classifier = FreshnessClassifier(context)
        try {
            bitmap.eraseColor(Color.rgb(32, 128, 224))
            val buffer = ImagePreprocessor(ImagePreprocessor.ResizePolicy.STRETCH).preprocess(bitmap).getOrThrow()
            lateinit var prediction: CompletableFuture<PredictionResult>
            instrumentation.runOnMainSync { prediction = classifier.classify(buffer) }
            assertValidPrediction(prediction.get(90, TimeUnit.SECONDS))
            assertEquals(0, buffer.position())
            assertEquals(32f, buffer.getFloat(0), 0f)

            lateinit var cleanup: CompletableFuture<Unit>
            instrumentation.runOnMainSync { cleanup = classifier.closeAsync() }
            cleanup.get(30, TimeUnit.SECONDS)
            val closed = classifier.classify(buffer).get(5, TimeUnit.SECONDS) as PredictionResult.Failure
            assertEquals(PredictionResult.FailureReason.INFERENCE_FAILED, closed.reason)
            assertTrue(closed.cause is IllegalStateException)
        } finally {
            classifier.closeAsync().get(30, TimeUnit.SECONDS)
            bitmap.recycle()
        }
    }

    @Test fun rejectsInvalidBufferThenStillRunsTheRealModel() {
        val classifier = FreshnessClassifier(context)
        try {
            val result = classifier.classify(ByteBuffer.allocate(12)).get(5, TimeUnit.SECONDS)
            assertEquals(PredictionResult.FailureReason.INVALID_INPUT, (result as PredictionResult.Failure).reason)
            assertValidPrediction(classifier.classify(controlledInput(127.5f)).get(90, TimeUnit.SECONDS))
        } finally { classifier.closeAsync().get(30, TimeUnit.SECONDS) }
    }

    private fun controlledInput(value: Float): ByteBuffer =
        ByteBuffer.allocateDirect(ImagePreprocessor.INPUT_BYTES).order(ByteOrder.nativeOrder()).apply {
            repeat(ImagePreprocessor.INPUT_BYTES / Float.SIZE_BYTES) { putFloat(value) }
            rewind()
        }

    private fun assertValidPrediction(result: PredictionResult) {
        // A synthetic image has no expected real-world class. A valid exact tie is permitted.
        when (result) {
            is PredictionResult.Success -> {
                assertEquals(3, result.scores.size)
                assertTrue(result.confidence.isFinite() && result.confidence in 0f..1f)
                assertEquals(result.scores.max(), result.confidence, 0f)
                assertFalse(result.isTrainingClassMappingVerified)
            }
            is PredictionResult.Failure -> assertEquals(PredictionResult.FailureReason.TIED_SCORES, result.reason)
        }
    }
}
