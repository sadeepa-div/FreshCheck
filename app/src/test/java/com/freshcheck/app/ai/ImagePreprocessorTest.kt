package com.freshcheck.app.ai

import com.freshcheck.app.ai.ImagePreprocessor.ResizePolicy
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Synthetic pixels/geometry only; these fixtures are never inference results or saved scans. */
class ImagePreprocessorTest {
    @Test fun packsRgbInRowOrderIntoAReadyFloatBuffer() {
        val pixels = IntArray(224 * 224) { 0xff000000.toInt() }
        pixels[0] = 0xffff0000.toInt()
        pixels[1] = 0xff00ff00.toInt()
        pixels[224] = 0xff0000ff.toInt()
        pixels[pixels.lastIndex] = 0xff123456.toInt()

        val buffer = packRgbPixels(pixels)

        assertTrue(buffer.isDirect)
        assertEquals(ByteOrder.nativeOrder(), buffer.order())
        assertEquals(602112, buffer.capacity())
        assertEquals(602112, buffer.limit())
        assertEquals(0, buffer.position())
        assertPixel(buffer, 0, 255f, 0f, 0f)
        assertPixel(buffer, 1, 0f, 255f, 0f)
        assertPixel(buffer, 223, 0f, 0f, 0f)
        assertPixel(buffer, 224, 0f, 0f, 255f)
        assertPixel(buffer, pixels.lastIndex, 18f, 52f, 86f)
    }

    @Test fun keepsRawIntensitiesWithoutExternalNormalization() {
        val buffer = packRgbPixels(IntArray(224 * 224) { 0xff0180ff.toInt() })
        repeat(224 * 224) {
            assertEquals(1f, buffer.float, 0f)
            assertEquals(128f, buffer.float, 0f)
            assertEquals(255f, buffer.float, 0f)
        }
        assertFalse(buffer.hasRemaining())
    }

    @Test fun allocatesIndependentBuffers() {
        val pixels = IntArray(224 * 224) { 0xffffffff.toInt() }
        val first = packRgbPixels(pixels)
        val second = packRgbPixels(pixels)
        first.putFloat(0, 42f)
        assertEquals(255f, second.getFloat(0), 0f)
        assertEquals(0, second.position())
    }

    @Test fun rejectsMissingOrExtraPixels() {
        for (count in listOf(0, 224 * 224 - 1, 224 * 224 + 1)) {
            assertThrows(IllegalArgumentException::class.java) { packRgbPixels(IntArray(count)) }
        }
    }

    @Test fun stretchFillsBothDimensionsWithoutCropping() {
        val transform = resizeTransform(448, 112, ResizePolicy.STRETCH)
        assertEquals(ResizeTransform(0.5f, 2f, 0f, 0f), transform)
    }

    @Test fun centerCropCentersLandscapeAndPortraitInputs() {
        assertEquals(ResizeTransform(1f, 1f, -112f, 0f), resizeTransform(448, 224, ResizePolicy.CENTER_CROP))
        assertEquals(ResizeTransform(1f, 1f, 0f, -112f), resizeTransform(224, 448, ResizePolicy.CENTER_CROP))
    }

    @Test fun fitCenterPreservesTheWholeImageWithSymmetricPadding() {
        assertEquals(ResizeTransform(0.5f, 0.5f, 0f, 56f), resizeTransform(448, 224, ResizePolicy.FIT_CENTER))
        assertEquals(ResizeTransform(0.5f, 0.5f, 56f, 0f), resizeTransform(224, 448, ResizePolicy.FIT_CENTER))
    }

    @Test fun squareInputHasTheSameGeometryForEveryPolicy() {
        ResizePolicy.entries.forEach {
            assertEquals(ResizeTransform(2f, 2f, 0f, 0f), resizeTransform(112, 112, it))
        }
    }

    @Test fun oddSizedCropKeepsTheImageCenter() {
        val transform = resizeTransform(449, 224, ResizePolicy.CENTER_CROP)
        assertEquals(-112.5f, transform.translateX, 0f)
        assertEquals(112f, 449 / 2f * transform.scaleX + transform.translateX, 0.0001f)
    }

    @Test fun rejectsNonpositiveDimensionsForEveryPolicy() {
        ResizePolicy.entries.forEach { policy ->
            for ((width, height) in listOf(0 to 224, 224 to 0, -1 to 224, 224 to -1)) {
                assertThrows(IllegalArgumentException::class.java) { resizeTransform(width, height, policy) }
            }
        }
    }

    private fun assertPixel(buffer: ByteBuffer, index: Int, red: Float, green: Float, blue: Float) {
        val offset = index * 12
        assertEquals(red, buffer.getFloat(offset), 0f)
        assertEquals(green, buffer.getFloat(offset + 4), 0f)
        assertEquals(blue, buffer.getFloat(offset + 8), 0f)
    }
}
