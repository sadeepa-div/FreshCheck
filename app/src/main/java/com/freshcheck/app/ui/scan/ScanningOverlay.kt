package com.freshcheck.app.ui.scan

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode

/** Infinite motion exists only while the demo scanner is composed and system animations are enabled. */
@Composable
fun ScanningOverlay(modifier: Modifier = Modifier, animate: Boolean = true) {
    val enabled = animate && ValueAnimator.areAnimatorsEnabled() && !LocalInspectionMode.current
    val position = if (enabled) {
        val transition = rememberInfiniteTransition(label = "Scanner demonstration")
        transition.animateFloat(.15f, .85f,
            infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
            label = "Scanning line").value
    } else .5f
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val green = Color(0xFF99E2B0)
        drawRoundRect(green.copy(alpha = .22f), size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f), style = Stroke(2f))
        val arm = w * .14f
        for (x in listOf(0f, w)) for (y in listOf(0f, h)) {
            val dx = if (x == 0f) arm else -arm
            val dy = if (y == 0f) arm else -arm
            drawLine(green, Offset(x, y + dy), Offset(x, y), 6f)
            drawLine(green, Offset(x, y), Offset(x + dx, y), 6f)
        }
        val y = h * position
        drawLine(green.copy(alpha = .10f), Offset(12f, y), Offset(w - 12f, y), 22f)
        drawLine(green, Offset(12f, y), Offset(w - 12f, y), 3f)
    }
}
