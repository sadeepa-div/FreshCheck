package com.freshcheck.app.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.freshcheck.app.ai.ImagePreprocessor.ResizePolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real Android bitmap tests using synthetic colors; no model, camera, or database is involved. */
@RunWith(AndroidJUnit4::class)
class ImagePreprocessorAndroidTest {
    @Test fun preservesRgbValuesAndDoesNotModifyTheSource() {
        val source = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.rgb(1, 128, 255))
            source.setPixel(0, 0, Color.RED)
            source.setPixel(223, 223, Color.GREEN)
            val buffer = ImagePreprocessor(ResizePolicy.STRETCH).preprocess(source).getOrThrow()

            assertTrue(buffer.isDirect)
            assertEquals(ByteOrder.nativeOrder(), buffer.order())
            assertEquals(602112, buffer.remaining())
            assertEquals(0, buffer.position())
            assertPixel(buffer, 0, 0, 255f, 0f, 0f)
            assertPixel(buffer, 1, 0, 1f, 128f, 255f)
            assertPixel(buffer, 223, 223, 0f, 255f, 0f)
            assertFalse(source.isRecycled)
            assertEquals(224, source.width)
            assertEquals(Color.RED, source.getPixel(0, 0))
        } finally {
            source.recycle()
        }
    }

    @Test fun resizesOnePixelWithoutNormalizingItsChannels() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.rgb(17, 129, 254))
            ResizePolicy.entries.forEach { policy ->
                val buffer = ImagePreprocessor(policy).preprocess(source).getOrThrow()
                repeat(224 * 224) {
                    assertEquals(17f, buffer.float, 0f)
                    assertEquals(129f, buffer.float, 0f)
                    assertEquals(254f, buffer.float, 0f)
                }
            }
        } finally {
            source.recycle()
        }
    }

    @Test fun centerCropRemovesBothOuterEdgesOfLandscapeImage() {
        val source = Bitmap.createBitmap(448, 224, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.GREEN)
            val red = IntArray(112 * 224) { Color.RED }
            val blue = IntArray(112 * 224) { Color.BLUE }
            source.setPixels(red, 0, 112, 0, 0, 112, 224)
            source.setPixels(blue, 0, 112, 336, 0, 112, 224)

            val cropped = ImagePreprocessor(ResizePolicy.CENTER_CROP, filterBitmap = false)
                .preprocess(source).getOrThrow()
            assertPixel(cropped, 0, 112, 0f, 255f, 0f)
            assertPixel(cropped, 223, 112, 0f, 255f, 0f)
            val stretched = ImagePreprocessor(ResizePolicy.STRETCH, filterBitmap = false)
                .preprocess(source).getOrThrow()
            assertPixel(stretched, 0, 112, 255f, 0f, 0f)
            assertPixel(stretched, 223, 112, 0f, 0f, 255f)
        } finally {
            source.recycle()
        }
    }

    @Test fun fitCenterAddsBlackPaddingWithoutCropping() {
        val source = Bitmap.createBitmap(448, 224, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.WHITE)
            val buffer = ImagePreprocessor(ResizePolicy.FIT_CENTER, filterBitmap = false)
                .preprocess(source).getOrThrow()
            assertPixel(buffer, 112, 0, 0f, 0f, 0f)
            assertPixel(buffer, 112, 55, 0f, 0f, 0f)
            assertPixel(buffer, 112, 56, 255f, 255f, 255f)
            assertPixel(buffer, 112, 167, 255f, 255f, 255f)
            assertPixel(buffer, 112, 168, 0f, 0f, 0f)
            assertPixel(buffer, 112, 223, 0f, 0f, 0f)
        } finally {
            source.recycle()
        }
    }

    @Test fun compositesTransparentPixelsOverBlack() {
        val source = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        try {
            source.eraseColor(Color.TRANSPARENT)
            source.setPixel(1, 0, Color.argb(128, 255, 0, 0))
            val buffer = ImagePreprocessor(ResizePolicy.STRETCH, filterBitmap = false)
                .preprocess(source).getOrThrow()
            assertPixel(buffer, 0, 0, 0f, 0f, 0f)
            assertPixel(buffer, 1, 0, 128f, 0f, 0f)
        } finally {
            source.recycle()
        }
    }

    @Test fun filteringCanBeConfigured() {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        try {
            source.setPixel(0, 0, Color.BLACK)
            source.setPixel(1, 0, Color.WHITE)
            val nearest = ImagePreprocessor(ResizePolicy.STRETCH, filterBitmap = false)
                .preprocess(source).getOrThrow()
            val filtered = ImagePreprocessor(ResizePolicy.STRETCH, filterBitmap = true)
                .preprocess(source).getOrThrow()
            val centerOffset = (112 * 224 + 112) * 12
            assertEquals(255f, nearest.getFloat(centerOffset), 0f)
            assertTrue(filtered.getFloat(centerOffset) in 1f..254f)
        } finally {
            source.recycle()
        }
    }

    @Test fun acceptsRgb565SoftwareImages() {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.RGB_565)
        try {
            source.eraseColor(Color.RED)
            val buffer = ImagePreprocessor(ResizePolicy.STRETCH).preprocess(source).getOrThrow()
            assertPixel(buffer, 112, 112, 255f, 0f, 0f)
        } finally {
            source.recycle()
        }
    }

    @Test fun invalidInputsReturnFailureInsteadOfATensor() {
        val preprocessor = ImagePreprocessor(ResizePolicy.STRETCH)
        assertTrue(preprocessor.preprocess(null).isFailure)
        val recycled = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        recycled.recycle()
        assertTrue(preprocessor.preprocess(recycled).isFailure)

        val alphaOnly = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
        val wideGamut = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888, true,
            ColorSpace.get(ColorSpace.Named.DISPLAY_P3))
        val unpremultiplied = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val floatBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGBA_F16)
        try {
            unpremultiplied.setPremultiplied(false)
            listOf(alphaOnly, wideGamut, unpremultiplied, floatBitmap).forEach {
                assertTrue(preprocessor.preprocess(it).isFailure)
                assertFalse(it.isRecycled)
            }
        } finally {
            listOf(alphaOnly, wideGamut, unpremultiplied, floatBitmap).forEach { it.recycle() }
        }
    }

    @Test fun rejectsHardwareBitmapsWithoutRecyclingThem() {
        val software = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val hardware = software.copy(Bitmap.Config.HARDWARE, false)
        try {
            assertNotNull(hardware)
            assertTrue(ImagePreprocessor(ResizePolicy.STRETCH).preprocess(hardware).isFailure)
            assertFalse(hardware!!.isRecycled)
        } finally {
            hardware?.recycle()
            software.recycle()
        }
    }

    private fun assertPixel(buffer: ByteBuffer, x: Int, y: Int, red: Float, green: Float, blue: Float) {
        val offset = (y * 224 + x) * 12
        assertEquals(red, buffer.getFloat(offset), 0f)
        assertEquals(green, buffer.getFloat(offset + 4), 0f)
        assertEquals(blue, buffer.getFloat(offset + 8), 0f)
    }
}
