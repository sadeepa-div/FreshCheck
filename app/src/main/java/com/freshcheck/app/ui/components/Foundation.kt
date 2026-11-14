package com.freshcheck.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    var entered by remember { mutableStateOf(preview) }
    LaunchedEffect(Unit) { entered = true }
    val alpha by animateFloatAsState(if (entered) 1f else 0f, label = "Card entrance")
    Surface(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha; translationY = (1f - alpha) * 12f }, shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "Button press")
    Button(onClick, modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .heightIn(min = 52.dp), interactionSource = interaction,
        shape = RoundedCornerShape(16.dp)) { Text(text) }
}
@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable
fun EmptyPanel(title: String, body: String, action: String, onClick: () -> Unit) {
    Panel {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick) { Text(action) }
    }
}

/** Original vector produce illustration; no network or image permissions needed. */
@Composable
fun ProduceArt(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Illustration of avocado, tomato and leafy greens" }) {
        val w = size.width
        val h = size.height
        drawOval(Color(0xFF0E3826), Offset(w * .1f, h * .82f), Size(w * .8f, h * .12f))
        drawOval(Color(0xFF47A875), Offset(w * .50f, h * .04f), Size(w * .25f, h * .55f))
        drawOval(Color(0xFF83C98B), Offset(w * .69f, h * .15f), Size(w * .23f, h * .42f))
        drawOval(Color(0xFF2C884F), Offset(w * .38f, h * .13f), Size(w * .24f, h * .43f))
        drawOval(Color(0xFFB7D77C), Offset(w * .09f, h * .18f), Size(w * .43f, h * .70f))
        drawOval(Color(0xFFE0EAA8), Offset(w * .13f, h * .23f), Size(w * .35f, h * .60f))
        drawCircle(Color(0xFF97603B), w * .115f, Offset(w * .31f, h * .60f))
        drawCircle(Color(0xFFDB635C), w * .20f, Offset(w * .69f, h * .69f))
        drawLine(Color(0xFF245D39), Offset(w * .69f, h * .47f), Offset(w * .71f, h * .56f), 7.dp.toPx())
    }
}
