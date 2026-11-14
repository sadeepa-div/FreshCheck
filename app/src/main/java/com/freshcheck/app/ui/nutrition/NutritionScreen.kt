package com.freshcheck.app.ui.nutrition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.freshcheck.app.ui.components.*
import java.util.Locale

@Composable
fun NutritionScreen(profile: NutritionProfile = DemoNutrition, onBack: () -> Unit) {
    var grams by rememberSaveable(profile.name) { mutableStateOf(100) }
    Page {
        TextButton(onBack) { Text("← Insights") }
        SectionTitle("A closer look at nutrition", "${profile.name} · ${profile.category}")
        if (profile.isDemo) Panel {
            Text("DEMONSTRATION VALUES ONLY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("Synthetic numbers for testing this interface. These are not verified nutrition values for this produce.")
        }
        ProduceArt(Modifier.fillMaxWidth().height(160.dp))
        Text("Illustration · sample produce, not identified from your scan", style = MaterialTheme.typography.bodySmall)
        SectionTitle("Serving basis · $grams g")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 150, 200, 250).forEach { value ->
                FilterChip(grams == value, { grams = value }, label = { Text("$value g") })
            }
        }
        Slider(grams.toFloat(), { grams = it.toInt() }, valueRange = 25f..500f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Serving size in grams" }, steps = 18)
        Panel {
            Text(String.format(Locale.getDefault(), "%.1f kcal", scaledNutrient(profile.energyKcalPer100g, grams)),
                style = MaterialTheme.typography.headlineLarge)
            Text("Energy per $grams g · ${if (profile.isDemo) "synthetic sample" else "reference estimate"}")
        }
        profile.nutrients.forEach { nutrient ->
            NutrientCard(nutrient, grams, profile.isDemo)
        }
        Panel {
            SectionTitle("About these values")
            Text("Source: ${profile.source}")
            Text("Reference basis: 100 g; displayed values are scaled to $grams g. Preparation and variety can change composition. Camera images do not measure nutrient content.")
            Text("Bars compare this serving with the same nutrient in 500 g. They are not daily intake targets, safety scores or freshness scores.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
fun NutrientCard(nutrient: NutrientValue, grams: Int, isDemo: Boolean) {
    var expanded by rememberSaveable(nutrient.name) { mutableStateOf(false) }
    val progress by animateFloatAsState(grams / 500f, label = "Serving nutrient chart")
    Panel {
        Text(nutrient.name, style = MaterialTheme.typography.titleMedium)
        Text(String.format(Locale.getDefault(), "%.1f %s per %d g", scaledNutrient(nutrient.per100g, grams), nutrient.unit, grams),
            style = MaterialTheme.typography.titleLarge)
        if (isDemo) Text("Synthetic demo value", style = MaterialTheme.typography.labelSmall)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide nutrient information" else "About ${nutrient.name}") }
        if (expanded) Text(nutrient.explanation, style = MaterialTheme.typography.bodyMedium)
    }
}
