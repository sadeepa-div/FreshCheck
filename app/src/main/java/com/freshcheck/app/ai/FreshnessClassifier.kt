package com.freshcheck.app.ai

import android.content.Context
import android.content.res.AssetManager
import com.freshcheck.app.ai.PredictionResult.Failure
import com.freshcheck.app.ai.PredictionResult.FailureReason
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Lazy CPU inference foundation. Construction does no model IO. One owned worker serializes
 * loading, tensor inspection, inference and native cleanup; no Interpreter escapes this class.
 * labels.txt mapping remains provisional: training indices/accuracy are NOT verified.
 * No UI result, produce identity, storage advice or food-safety conclusion is generated here.
 *
 * Own this with a lifecycle and call [close] on disposal. Futures do not switch to the main
 * thread: UI adapters must dispatch delivery and reject stale ScanRequest identities themselves.
 * Never block the Android main thread with Future.get/join.
 */
class FreshnessClassifier internal constructor(
    private val loadEngine: () -> ClassifierEngine
) : AutoCloseable {
    constructor(context: Context) : this(assetLoader(context.applicationContext.assets))

    private val gate = Any()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "FreshCheck-inference").apply { isDaemon = true }
    }
    // Accessed only under gate.
    private var closing: CompletableFuture<Unit>? = null
    // Accessed only by the worker.
    private var loaded: LoadedEngine? = null
    private var terminalFailure: Failure? = null

    /**
     * Accepts ImagePreprocessor's direct native-order FLOAT32 buffer at position zero.
     * Copies bytes before returning, leaving the caller's position/limit/contents untouched.
     * Do not mutate the source during this call; it can be reused once the call returns.
     * The worker checks raw channel values are finite in 0..255; it does NOT normalize them.
     *
     * Cancelling the returned future skips queued work or discards a running result. It does
     * not interrupt native execution or close its Interpreter while that execution is active.
     */
    fun classify(input: ByteBuffer?): CompletableFuture<PredictionResult> = synchronized(gate) {
        if (closing != null) {
            return@synchronized CompletableFuture.completedFuture(
                Failure(FailureReason.INFERENCE_FAILED, IllegalStateException("Classifier is closed."))
            )
        }
        val snapshot = try {
            snapshotClassifierInput(input)
        } catch (failure: IllegalArgumentException) {
            return@synchronized CompletableFuture.completedFuture(Failure(FailureReason.INVALID_INPUT, failure))
        }
        CompletableFuture.supplyAsync({ classifyOnWorker(snapshot) }, worker)
    }

    /**
     * Marks closed immediately and asynchronously releases resources after accepted work drains.
     * Idempotent. Cleanup errors complete the future exceptionally; no native close runs on UI.
     * Cancellation of this future does not cancel the queued cleanup itself.
     */
    fun closeAsync(): CompletableFuture<Unit> = synchronized(gate) {
        closing?.let { return@synchronized it }
        CompletableFuture.runAsync({
            val active = loaded
            loaded = null
            active?.engine?.close()
        }, worker).thenApply { Unit }.also {
            closing = it
            worker.shutdown()
        }
    }

    /** Non-blocking disposal; use closeAsync off main if cleanup completion must be awaited. */
    override fun close() {
        closeAsync()
    }

    private fun classifyOnWorker(input: ByteBuffer): PredictionResult {
        val floats = input.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
        while (floats.hasRemaining()) {
            val value = floats.get()
            if (!value.isFinite() || value !in 0f..255f) return Failure(FailureReason.INVALID_INPUT)
        }
        terminalFailure?.let { return it }
        if (loaded == null) {
            try {
                val candidate = loadEngine()
                try {
                    val labels = candidate.labels.toList()
                    validateClassifierLabels(labels)
                    validateClassifierTensors(candidate.inputs, candidate.outputs)
                    loaded = LoadedEngine(candidate, labels)
                } catch (failure: Throwable) {
                    closeAfterFailure(candidate, failure)
                    throw failure
                }
            } catch (failure: Exception) {
                return rememberInitializationFailure(failure)
            } catch (failure: LinkageError) {
                return rememberInitializationFailure(failure)
            }
        }
        val active = checkNotNull(loaded)
        return try {
            // Input is raw 0..255; the graph includes normalization AND softmax already.
            PredictionResult.fromScores(active.engine.run(input), active.labels)
        } catch (failure: Exception) {
            retireEngine(active, failure)
        } catch (failure: LinkageError) {
            retireEngine(active, failure)
        }
    }

    private fun rememberInitializationFailure(cause: Throwable): Failure = Failure(
        if (cause is InvalidClassifierLabels) FailureReason.INVALID_LABELS else FailureReason.MODEL_LOADING_FAILED,
        cause
    ).also { terminalFailure = it }

    private fun retireEngine(active: LoadedEngine, cause: Throwable): Failure {
        loaded = null
        closeAfterFailure(active.engine, cause)
        return Failure(FailureReason.INFERENCE_FAILED, cause).also { terminalFailure = it }
    }

    private data class LoadedEngine(val engine: ClassifierEngine, val labels: List<String>)
}

/** Internal port permits JVM lifecycle/error tests without loading Android's native runtime. */
internal interface ClassifierEngine : AutoCloseable {
    val labels: List<String>
    val inputs: List<ClassifierTensor>
    val outputs: List<ClassifierTensor>
    fun run(input: ByteBuffer): FloatArray
}

internal data class ClassifierTensor(val shape: List<Int>, val dataType: DataType)

internal fun validateClassifierTensors(inputs: List<ClassifierTensor>, outputs: List<ClassifierTensor>) {
    require(inputs == listOf(ClassifierTensor(listOf(1, 224, 224, 3), DataType.FLOAT32))) {
        "Expected one FLOAT32 input tensor with shape [1, 224, 224, 3]."
    }
    require(outputs == listOf(ClassifierTensor(listOf(1, 3), DataType.FLOAT32))) {
        "Expected one FLOAT32 output tensor with shape [1, 3]."
    }
}

private class InvalidClassifierLabels : IllegalArgumentException("Unexpected or missing labels.txt entries.")

private fun validateClassifierLabels(labels: List<String>) {
    // Sidecar order only, NOT verified training/export class indices. Never sort/filter blanks.
    if (labels != listOf("Fresh", "Use Soon", "Visibly Spoiled")) throw InvalidClassifierLabels()
}

internal fun snapshotClassifierInput(input: ByteBuffer?): ByteBuffer {
    requireNotNull(input) { "A preprocessed tensor is required." }
    require(input.isDirect) { "Input must be a direct ByteBuffer." }
    require(input.order() == ByteOrder.nativeOrder()) { "Input must use native byte order." }
    require(input.capacity() == ImagePreprocessor.INPUT_BYTES && input.limit() == ImagePreprocessor.INPUT_BYTES) {
        "Expected exactly ${ImagePreprocessor.INPUT_BYTES} bytes for [1, 224, 224, 3] FLOAT32."
    }
    require(input.position() == 0) { "Input position must be zero; preprocess or rewind before submitting." }
    return ByteBuffer.allocateDirect(ImagePreprocessor.INPUT_BYTES).order(ByteOrder.nativeOrder()).apply {
        put(input.duplicate())
        rewind()
    }
}

private fun assetLoader(assets: AssetManager): () -> ClassifierEngine = { loadBundledClassifierEngine(assets) }

/** Called only off main. Streaming also works for compressed assets, without Gradle noCompress changes. */
internal fun loadBundledClassifierEngine(assets: AssetManager): ClassifierEngine {
    val labels = assets.open("labels.txt").bufferedReader(Charsets.UTF_8).use { it.readLines() }
    validateClassifierLabels(labels)
    val bytes = assets.open("freshcheck_3class.tflite").use { it.readBytes() }
    require(bytes.isNotEmpty()) { "Model asset is empty." }
    val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
        put(bytes)
        rewind()
    }
    // CPU reference baseline: no hardware delegates, XNNPACK disabled, one execution thread.
    val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(1).setUseXNNPACK(false))
    return LiteRtClassifierEngine(interpreter, model, labels)
}

private class LiteRtClassifierEngine(
    private val interpreter: Interpreter,
    // Keep the model bytes alive and unchanged for the entire native Interpreter lifetime.
    @Suppress("unused") private val model: ByteBuffer,
    override val labels: List<String>
) : ClassifierEngine {
    override val inputs: List<ClassifierTensor>
        get() = (0 until interpreter.inputTensorCount).map {
            interpreter.getInputTensor(it).let { tensor -> ClassifierTensor(tensor.shape().toList(), tensor.dataType()) }
        }
    override val outputs: List<ClassifierTensor>
        get() = (0 until interpreter.outputTensorCount).map {
            interpreter.getOutputTensor(it).let { tensor -> ClassifierTensor(tensor.shape().toList(), tensor.dataType()) }
        }

    override fun run(input: ByteBuffer): FloatArray {
        val output = arrayOf(FloatArray(3))
        interpreter.run(input, output)
        return output[0]
    }

    override fun close() = interpreter.close()
}

private fun closeAfterFailure(engine: ClassifierEngine, cause: Throwable) {
    try {
        engine.close()
    } catch (cleanup: Exception) {
        if (cleanup !== cause) cause.addSuppressed(cleanup)
    } catch (cleanup: LinkageError) {
        if (cleanup !== cause) cause.addSuppressed(cleanup)
    }
}
