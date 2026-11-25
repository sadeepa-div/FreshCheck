package com.freshcheck.app.ai

import com.freshcheck.app.ai.PredictionResult.Failure
import com.freshcheck.app.ai.PredictionResult.FailureReason
import org.junit.Assert.*
import org.junit.Test
import org.tensorflow.lite.DataType
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Fake engines and synthetic tensors only. JVM tests do not execute the bundled native model. */
class FreshnessClassifierTest {
    @Test fun snapshotsReadyInputWithoutChangingTheCallerBuffer() {
        val source = input(127.5f)
        val snapshot = snapshotClassifierInput(source)
        source.putFloat(0, 255f)
        assertEquals(127.5f, snapshot.getFloat(0), 0f)
        assertEquals(0, source.position())
        assertEquals(ImagePreprocessor.INPUT_BYTES, source.limit())
        assertTrue(snapshot.isDirect)
        assertEquals(ByteOrder.nativeOrder(), snapshot.order())
        assertEquals(0, snapshot.position())
        assertEquals(ImagePreprocessor.INPUT_BYTES, snapshot.remaining())
    }

    @Test fun rejectsInvalidBufferMetadataWithoutLoadingTheModel() {
        val engine = FakeEngine()
        val loads = AtomicInteger()
        val classifier = FreshnessClassifier { loads.incrementAndGet(); engine }
        try {
            val wrongOrder = if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
                ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
            val invalid = listOf(null,
                ByteBuffer.allocate(ImagePreprocessor.INPUT_BYTES).order(ByteOrder.nativeOrder()),
                input().order(wrongOrder),
                ByteBuffer.allocateDirect(ImagePreprocessor.INPUT_BYTES - 4).order(ByteOrder.nativeOrder()),
                ByteBuffer.allocateDirect(ImagePreprocessor.INPUT_BYTES + 4).order(ByteOrder.nativeOrder()),
                input().apply { limit(capacity() - 4) },
                input().apply { position(4) })
            for (buffer in invalid) assertReason(FailureReason.INVALID_INPUT, classifier.classify(buffer).await())
            assertEquals(0, loads.get())
            assertEquals(0, engine.runs)
        } finally { classifier.closeAsync().await() }
    }

    @Test fun rejectsNonFiniteOrOutOfRangePixelValuesBeforeLoading() {
        val loads = AtomicInteger()
        val classifier = FreshnessClassifier { loads.incrementAndGet(); FakeEngine() }
        try {
            for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 256f)) {
                val buffer = input().apply { putFloat(capacity() - 4, invalid) }
                assertReason(FailureReason.INVALID_INPUT, classifier.classify(buffer).await())
            }
            assertEquals(0, loads.get())
        } finally { classifier.closeAsync().await() }
    }

    @Test fun runsLoadInferenceAndCleanupOnTheSameBackgroundThread() {
        val caller = Thread.currentThread()
        val engine = FakeEngine()
        var loadThread: Thread? = null
        val classifier = FreshnessClassifier { loadThread = Thread.currentThread(); engine }
        try {
            val result = classifier.classify(input(255f)).await() as PredictionResult.Success
            assertEquals(listOf(0.2f, 0.3f, 0.5f), result.scores)
            assertEquals(0.5f, result.confidence, 0f)
            assertFalse(result.isTrainingClassMappingVerified)
            assertNotSame(caller, loadThread)
            assertSame(loadThread, engine.runThread)
            assertEquals(255f, engine.firstPixel, 0f)
        } finally { classifier.closeAsync().await() }
        assertSame(loadThread, engine.closeThread)
        assertEquals(1, engine.closes)
    }

    @Test fun acceptsRawIntensityEndpointsAndKeepsInputsReady() {
        val engine = FakeEngine()
        val classifier = FreshnessClassifier { engine }
        try {
            for (value in listOf(0f, 127.5f, 255f)) {
                val buffer = input(value)
                assertTrue(classifier.classify(buffer).await() is PredictionResult.Success)
                assertEquals(value, engine.firstPixel, 0f)
                assertEquals(0, buffer.position())
                assertEquals(value, buffer.getFloat(0), 0f)
            }
        } finally { classifier.closeAsync().await() }
    }

    @Test fun loadsOnceAcrossRepeatedRequests() {
        val loads = AtomicInteger()
        val engine = FakeEngine()
        val classifier = FreshnessClassifier { loads.incrementAndGet(); engine }
        try {
            repeat(3) { assertTrue(classifier.classify(input()).await() is PredictionResult.Success) }
            assertEquals(1, loads.get())
            assertEquals(3, engine.runs)
        } finally { classifier.closeAsync().await() }
    }

    @Test fun initializationIoAndRuntimeErrorsAreCachedAsExplicitFailures() {
        for (cause in listOf(IOException("Synthetic missing asset"), IllegalArgumentException("Synthetic corrupt model"),
            UnsatisfiedLinkError("Synthetic missing native runtime"))) {
            val loads = AtomicInteger()
            val classifier = FreshnessClassifier { loads.incrementAndGet(); throw cause }
            try {
                repeat(2) {
                    val result = classifier.classify(input()).await()
                    assertReason(FailureReason.MODEL_LOADING_FAILED, result)
                    assertSame(cause, (result as Failure).cause)
                }
                assertEquals(1, loads.get())
            } finally { classifier.closeAsync().await() }
        }
    }

    @Test fun invalidLabelsCloseTheCandidateBeforeAnyInference() {
        for (labels in listOf(emptyList(), listOf("Fresh", "Use Soon"),
            listOf("Fresh", "", "Visibly Spoiled"), listOf("Use Soon", "Fresh", "Visibly Spoiled"),
            listOf("Fresh", "Use Soon", "Visibly Spoiled", "Extra"))) {
            val engine = FakeEngine().apply { this.labels = labels }
            val classifier = FreshnessClassifier { engine }
            try {
                assertReason(FailureReason.INVALID_LABELS, classifier.classify(input()).await())
                assertEquals(0, engine.runs)
                assertEquals(1, engine.closes)
            } finally { classifier.closeAsync().await() }
            assertEquals(1, engine.closes)
        }
    }

    @Test fun rejectsWrongInputCountsShapesAndDataTypesAndClosesTheCandidate() {
        val invalid = listOf(emptyList(), validInputs + validInputs,
            listOf(ClassifierTensor(listOf(1, 224, 224, 4), DataType.FLOAT32)),
            listOf(ClassifierTensor(listOf(1, 3, 224, 224), DataType.FLOAT32)),
            listOf(ClassifierTensor(listOf(2, 224, 224, 3), DataType.FLOAT32)),
            listOf(ClassifierTensor(listOf(1, 224, 224, 3), DataType.UINT8)))
        for (tensors in invalid) assertBadTensorContract(tensors, validOutputs)
    }

    @Test fun rejectsWrongOutputCountsShapesAndDataTypesAndClosesTheCandidate() {
        val invalid = listOf(emptyList(), validOutputs + validOutputs,
            listOf(ClassifierTensor(listOf(3), DataType.FLOAT32)),
            listOf(ClassifierTensor(listOf(1, 4), DataType.FLOAT32)),
            listOf(ClassifierTensor(listOf(1, 3), DataType.INT8)))
        for (tensors in invalid) assertBadTensorContract(validInputs, tensors)
    }

    @Test fun passesInvalidAndTiedOutputsToPredictionResultWithoutSoftmax() {
        val engine = FakeEngine()
        val classifier = FreshnessClassifier { engine }
        try {
            for (values in listOf(floatArrayOf(0.2f, 0.3f, 0.5f), floatArrayOf(0.5f, 0.5f, 0f),
                floatArrayOf(Float.NaN, 0f, 1f), floatArrayOf(0.1f, 0.2f, 0.3f), floatArrayOf(1f))) {
                engine.scores = values
                val actual = classifier.classify(input()).await()
                val expected = PredictionResult.fromScores(values, engine.labels)
                if (expected is PredictionResult.Success) {
                    assertEquals(expected.scores, (actual as PredictionResult.Success).scores)
                    assertEquals(expected.confidence, actual.confidence, 0f)
                } else assertEquals(expected, actual)
            }
        } finally { classifier.closeAsync().await() }
    }

    @Test fun inferenceErrorsRetireTheEngineAndDoNotRetryAPossiblyBrokenInterpreter() {
        val cause = IllegalStateException("Synthetic inference failure")
        val cleanup = IOException("Synthetic cleanup failure")
        val engine = FakeEngine().apply { runFailure = cause; closeFailure = cleanup }
        val classifier = FreshnessClassifier { engine }
        try {
            repeat(2) {
                val result = classifier.classify(input()).await()
                assertReason(FailureReason.INFERENCE_FAILED, result)
                assertSame(cause, (result as Failure).cause)
            }
            assertEquals(1, engine.runs)
            assertEquals(1, engine.closes)
            assertArrayEquals(arrayOf(cleanup), cause.suppressed)
        } finally { classifier.closeAsync().await() }
    }

    @Test fun validationErrorPreservesCleanupFailureAndDoesNotLeakOrDoubleClose() {
        val cleanup = IOException("Synthetic cleanup failure")
        val engine = FakeEngine().apply { inputs = emptyList(); closeFailure = cleanup }
        val classifier = FreshnessClassifier { engine }
        try {
            val result = classifier.classify(input()).await() as Failure
            assertEquals(FailureReason.MODEL_LOADING_FAILED, result.reason)
            assertArrayEquals(arrayOf(cleanup), result.cause!!.suppressed)
        } finally { classifier.closeAsync().await() }
        assertEquals(1, engine.closes)
    }

    @Test fun closeBeforeFirstUseDoesNotLoadAndLaterCallsFailSafely() {
        val loads = AtomicInteger()
        val classifier = FreshnessClassifier { loads.incrementAndGet(); FakeEngine() }
        classifier.close()
        classifier.closeAsync().await()
        val result = classifier.classify(input()).await()
        assertReason(FailureReason.INFERENCE_FAILED, result)
        assertTrue((result as Failure).cause is IllegalStateException)
        assertEquals(0, loads.get())
    }

    @Test fun closeIsIdempotentAndReportsCleanupErrorsAsynchronously() {
        val cleanup = IOException("Synthetic close failure")
        val engine = FakeEngine().apply { closeFailure = cleanup }
        val classifier = FreshnessClassifier { engine }
        classifier.classify(input()).await()
        val first = classifier.closeAsync()
        assertSame(first, classifier.closeAsync())
        val failure = assertThrows(ExecutionException::class.java) { first.await() }
        assertSame(cleanup, failure.cause)
        assertEquals(1, engine.closes)
        assertReason(FailureReason.INFERENCE_FAILED, classifier.classify(input()).await())
    }

    @Test fun serializesQueuedInferenceAndCloseAndSnapshotsBeforeReturning() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val engine = FakeEngine().apply {
            onRun = { call -> if (call == 1) { started.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) } }
        }
        val classifier = FreshnessClassifier { engine }
        try {
            val first = classifier.classify(input(10f))
            assertTrue(started.await(10, TimeUnit.SECONDS))
            val secondInput = input(20f)
            val second = classifier.classify(secondInput)
            secondInput.putFloat(0, 99f)
            val closed = classifier.closeAsync()
            assertFalse(closed.isDone)
            assertEquals(0, engine.closes)
            assertReason(FailureReason.INFERENCE_FAILED, classifier.classify(input()).await())
            release.countDown()
            assertTrue(first.await() is PredictionResult.Success)
            assertTrue(second.await() is PredictionResult.Success)
            closed.await()
            assertEquals(listOf(10f, 20f), engine.observedPixels)
            assertEquals(1, engine.maxActive.get())
            assertEquals(1, engine.closes)
        } finally { release.countDown(); classifier.closeAsync().await() }
    }

    @Test fun cancelledQueuedWorkDoesNotExecuteOrBlockCleanup() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val engine = FakeEngine().apply {
            onRun = { call -> if (call == 1) { started.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) } }
        }
        val classifier = FreshnessClassifier { engine }
        try {
            val first = classifier.classify(input())
            assertTrue(started.await(10, TimeUnit.SECONDS))
            val cancelled = classifier.classify(input())
            assertTrue(cancelled.cancel(false))
            release.countDown()
            first.await()
            classifier.closeAsync().await()
            assertEquals(1, engine.runs)
            assertEquals(1, engine.closes)
            assertTrue(cancelled.isCancelled)
        } finally { release.countDown(); classifier.closeAsync().await() }
    }

    @Test fun cancellingRunningInferenceDoesNotCloseResourcesUntilNativeWorkReturns() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val engine = FakeEngine().apply {
            onRun = { started.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)) }
        }
        val classifier = FreshnessClassifier { engine }
        try {
            val pending = classifier.classify(input())
            assertTrue(started.await(10, TimeUnit.SECONDS))
            assertTrue(pending.cancel(true))
            val closed = classifier.closeAsync()
            assertEquals(0, engine.closes)
            release.countDown()
            closed.await()
            assertEquals(1, engine.closes)
            assertTrue(pending.isCancelled)
        } finally { release.countDown(); classifier.closeAsync().await() }
    }

    private fun assertBadTensorContract(inputs: List<ClassifierTensor>, outputs: List<ClassifierTensor>) {
        val engine = FakeEngine().apply { this.inputs = inputs; this.outputs = outputs }
        val classifier = FreshnessClassifier { engine }
        try {
            assertReason(FailureReason.MODEL_LOADING_FAILED, classifier.classify(input()).await())
            assertEquals(0, engine.runs)
        } finally { classifier.closeAsync().await() }
        assertEquals(1, engine.closes)
    }

    private class FakeEngine : ClassifierEngine {
        override var labels = listOf("Fresh", "Use Soon", "Visibly Spoiled")
        override var inputs = validInputs
        override var outputs = validOutputs
        var scores = floatArrayOf(0.2f, 0.3f, 0.5f)
        var runs = 0
        var closes = 0
        var runThread: Thread? = null
        var closeThread: Thread? = null
        var firstPixel = Float.NaN
        val observedPixels = mutableListOf<Float>()
        val maxActive = AtomicInteger()
        private val active = AtomicInteger()
        var onRun: (Int) -> Unit = {}
        var runFailure: RuntimeException? = null
        var closeFailure: Exception? = null

        override fun run(input: ByteBuffer): FloatArray {
            val concurrent = active.incrementAndGet()
            maxActive.updateAndGet { maxOf(it, concurrent) }
            try {
                runs++
                runThread = Thread.currentThread()
                firstPixel = input.getFloat(0)
                observedPixels.add(firstPixel)
                assertEquals(0, input.position())
                assertEquals(ByteOrder.nativeOrder(), input.order())
                onRun(runs)
                runFailure?.let { throw it }
                return scores.copyOf()
            } finally { active.decrementAndGet() }
        }

        override fun close() {
            assertEquals(0, active.get())
            closes++
            closeThread = Thread.currentThread()
            closeFailure?.let { throw it }
        }
    }

    companion object {
        private val validInputs = listOf(ClassifierTensor(listOf(1, 224, 224, 3), DataType.FLOAT32))
        private val validOutputs = listOf(ClassifierTensor(listOf(1, 3), DataType.FLOAT32))

        private fun input(value: Float = 0f): ByteBuffer =
            ByteBuffer.allocateDirect(ImagePreprocessor.INPUT_BYTES).order(ByteOrder.nativeOrder()).apply {
                repeat(ImagePreprocessor.INPUT_BYTES / Float.SIZE_BYTES) { putFloat(value) }
                rewind()
            }

        private fun assertReason(reason: FailureReason, result: PredictionResult) {
            assertTrue("Expected $reason but received $result", result is Failure)
            assertEquals(reason, (result as Failure).reason)
        }

        private fun <T> CompletableFuture<T>.await(): T = get(15, TimeUnit.SECONDS)
    }
}
