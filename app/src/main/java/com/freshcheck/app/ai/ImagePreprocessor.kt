package com.freshcheck.app.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min


class ImagePreprocessor(
    val resizePolicy: ResizePolicy,
    val filterBitmap: Boolean = true
) {
    enum class ResizePolicy {
        /** Fill the square, allowing different horizontal and vertical scaling. */
        STRETCH,

        /** Preserve aspect ratio and fill the square, cropping equally at opposite edges. */
        CENTER_CROP,

        /** Preserve the whole image and aspect ratio; center on black padding. */
        FIT_CENTER
    }

    /**
     * Returns a new direct, native-order FLOAT32 buffer for [1, 224, 224, 3] in RGB/NHWC order.
     * Channels retain their 0-255 intensities: the model already contains its own rescaling.
     * Position is zero and limit/capacity are [INPUT_BYTES], ready for the inference adapter.
     *
     * Null/recycled/unsupported inputs and Android pixel/drawing failures return Result.failure;
     * no sample image or tensor is substituted. Fatal VM errors such as OOM are not swallowed.
     */
    fun preprocess(bitmap: Bitmap?): Result<ByteBuffer> {
        var temporary: Bitmap? = null
        return try {
            val source = requireNotNull(bitmap) { "A bitmap is required for preprocessing." }
            require(!source.isRecycled) { "The source bitmap has been recycled." }
            require(source.config == Bitmap.Config.ARGB_8888 || source.config == Bitmap.Config.RGB_565) {
                "Use a software ARGB_8888 or RGB_565 bitmap; hardware, alpha-only and HDR inputs are unsupported."
            }
            require(source.colorSpace?.isSrgb == true) { "Convert the source bitmap to sRGB before preprocessing." }
            require(!source.hasAlpha() || source.isPremultiplied) { "The source bitmap must use premultiplied alpha." }
            val transform = resizeTransform(source.width, source.height, resizePolicy)
            val target = Bitmap.createBitmap(INPUT_WIDTH, INPUT_HEIGHT, Bitmap.Config.ARGB_8888)
            temporary = target
            val matrix = Matrix().apply {
                setScale(transform.scaleX, transform.scaleY)
                postTranslate(transform.translateX, transform.translateY)
            }
            val paint = Paint().apply { isFilterBitmap = filterBitmap }
            Canvas(target).apply {
                drawColor(Color.BLACK)
                drawBitmap(source, matrix, paint)
            }
            val pixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
            target.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
            Result.success(packRgbPixels(pixels))
        } catch (failure: RuntimeException) {
            Result.failure(failure)
        } finally {
            temporary?.recycle()
        }
    }

    companion object {
        const val INPUT_WIDTH = 224
        const val INPUT_HEIGHT = 224
        const val INPUT_CHANNELS = 3
        const val INPUT_BYTES = INPUT_WIDTH * INPUT_HEIGHT * INPUT_CHANNELS * Float.SIZE_BYTES
    }
}

/** Pure geometry and packing are kept independent of Android graphics for local JVM tests. */
internal data class ResizeTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float
)

internal fun resizeTransform(
    width: Int,
    height: Int,
    policy: ImagePreprocessor.ResizePolicy
): ResizeTransform {
    require(width > 0 && height > 0) { "Bitmap dimensions must be positive." }
    val widthScale = ImagePreprocessor.INPUT_WIDTH.toFloat() / width
    val heightScale = ImagePreprocessor.INPUT_HEIGHT.toFloat() / height
    val scale = when (policy) {
        ImagePreprocessor.ResizePolicy.STRETCH -> return ResizeTransform(widthScale, heightScale, 0f, 0f)
        ImagePreprocessor.ResizePolicy.CENTER_CROP -> max(widthScale, heightScale)
        ImagePreprocessor.ResizePolicy.FIT_CENTER -> min(widthScale, heightScale)
    }
    return ResizeTransform(
        scale, scale,
        (ImagePreprocessor.INPUT_WIDTH - width * scale) / 2f,
        (ImagePreprocessor.INPUT_HEIGHT - height * scale) / 2f
    )
}

/** Input pixels must already be the opaque, row-major result of drawing onto the target bitmap. */
internal fun packRgbPixels(pixels: IntArray): ByteBuffer {
    require(pixels.size == ImagePreprocessor.INPUT_WIDTH * ImagePreprocessor.INPUT_HEIGHT) {
        "Expected exactly 224 x 224 pixels."
    }
    val buffer = ByteBuffer.allocateDirect(ImagePreprocessor.INPUT_BYTES).order(ByteOrder.nativeOrder())
    for (pixel in pixels) {
        buffer.putFloat(((pixel ushr 16) and 0xff).toFloat())
        buffer.putFloat(((pixel ushr 8) and 0xff).toFloat())
        buffer.putFloat((pixel and 0xff).toFloat())
    }
    buffer.rewind()
    return buffer
}
