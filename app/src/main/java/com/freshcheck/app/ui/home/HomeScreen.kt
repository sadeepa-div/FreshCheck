package com.freshcheck.app.ui.home
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import com.freshcheck.app.ui.theme.Forest
import java.time.LocalTime

@Composable
fun HomeScreen(name: String, samples: Boolean, onSamples: (Boolean) -> Unit,
    onScan: (String) -> Unit, onFood: () -> Unit, onInsight: (String) -> Unit) {
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    var entered by remember { mutableStateOf(preview) }
    LaunchedEffect(Unit) { entered = true }
    Page {
        Text("FRESHCHECK  /  YOUR DAILY HARVEST", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary)
        val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        SectionTitle("$greeting, ${name.ifBlank { "food lover" }}.", "A little care. A fresher everyday.")
        AnimatedVisibility(entered, enter = fadeIn() + slideInVertically { it / 8 }) {
            Column(Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Forest, Color(0xFF0C3D2A))), RoundedCornerShape(28.dp))
                .padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("FRESHER CHOICES,\nLESS WASTE.", color = Color(0xFFBFDFB5), style = MaterialTheme.typography.labelLarge)
                Text("Good food starts\nwith a closer look.", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                ProduceArt(Modifier.fillMaxWidth().height(170.dp))
                Text("Explore the appearance of your produce, before and after shopping.",
                    color = Color(0xFFE1EBDF), style = MaterialTheme.typography.bodyMedium)
                ActionButton("Scan Food   →", { onScan("Before Buying") }, Modifier.fillMaxWidth())
            }
        }
        SectionTitle("Choose your moment")
        ActionButton("Before Buying · Shop thoughtfully", { onScan("Before Buying") }, Modifier.fillMaxWidth())
        OutlinedButton({ onScan("After Buying") }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("After Buying · Keep track at home")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Explore sample dashboard", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(samples, onSamples, Modifier.semantics { contentDescription = "Explore sample dashboard" })
        }
        if (samples) Text("SAMPLE DATA · Illustrative items only. Nothing is saved or assessed.",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        SectionTitle("Your kitchen at a glance")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)) {
                Text(if (samples) "08" else "00", style = MaterialTheme.typography.headlineLarge)
                Text("Food items", style = MaterialTheme.typography.bodyMedium)
            }
            Panel(Modifier.weight(1f)) {
                Text(if (samples) "02" else "00", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.tertiary)
                Text("Use soon", style = MaterialTheme.typography.bodyMedium)
            }
        }
        TextButton(onFood) { Text("Open My Food →") }
        SectionTitle("Use soon", "Give these a little attention first.")
        if (samples) Panel {
            Text("Sample · Avocado & spinach", style = MaterialTheme.typography.titleMedium)
            Text("Example priority list for planning your next meal. No expiry prediction.")
            TextButton({ onInsight("Meals") }) { Text("Explore meal ideas →") }
        } else EmptyPanel("A fresh start", "Your priority list will appear here when food tracking is available.", "Explore My Food", onFood)
        SectionTitle("Recent scan")
        if (samples) Panel {
            Text("Sample · Tomatoes", style = MaterialTheme.typography.titleMedium)
            Text("After Buying · Example scan preview")
            Text("No AI result has been generated.", style = MaterialTheme.typography.bodyMedium)
            TextButton({ onScan("After Buying") }) { Text("Explore scanning →") }
        } else EmptyPanel("No scans yet", "Start with a look at the two scanning modes.", "Explore Scan", { onScan("Before Buying") })
        SectionTitle("Feel good, eat well")
        Panel {
            Text("More colour on your plate", style = MaterialTheme.typography.titleMedium)
            Text("Explore nutrition and simple ways to use your produce.")
            TextButton({ onInsight("Nutrition") }) { Text("Nutrition guide →") }
            TextButton({ onInsight("Meals") }) { Text("Healthy meal inspiration →") }
        }
        Panel {
            Text("THE LITTLE LESS WASTE CLUB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("Shop your kitchen first.", style = MaterialTheme.typography.titleLarge)
            Text("Check what you already have before writing your shopping list.")
        }
        Text("Appearance alone cannot establish food safety. FreshCheck is not a food safety test.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
