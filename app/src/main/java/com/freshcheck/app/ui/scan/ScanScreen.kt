package com.freshcheck.app.ui.scan
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*

@Composable
fun ScanScreen(mode: String, onMode: (String) -> Unit) {
    Page {
        SectionTitle("A closer look", "Choose when you want to check your produce.")
        listOf("Before Buying", "After Buying").forEach {
            FilterChip(selected = mode == it, onClick = { onMode(it) }, label = { Text(it) })
        }
        ProduceArt(Modifier.fillMaxWidth().height(220.dp))
        Panel {
            Text(mode, style = MaterialTheme.typography.headlineMedium)
            Text(if (mode == "Before Buying") "Build a more thoughtful shopping basket." else "Plan how to use the produce already in your kitchen.")
            Text("Scanning is coming in a future phase. Camera capture and AI assessment are not connected yet.")
        }
        SectionTitle("When scanning becomes available")
        Text("1. Place one fruit or vegetable in good, even lighting.\n\n2. Keep the whole item in view.\n\n3. Review the visual assessment and its limitations.")
        Text("Visual freshness does not establish food safety.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
