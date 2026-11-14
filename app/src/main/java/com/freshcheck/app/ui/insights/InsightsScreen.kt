package com.freshcheck.app.ui.insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.freshcheck.app.ui.components.*
@Composable
fun InsightsScreen(topic: String, onTopic: (String) -> Unit) {
    Page {
        SectionTitle("Small choices, better habits", "Your space for food inspiration.")
        listOf("Overview", "Nutrition", "Meals").forEach {
            FilterChip(topic == it, { onTopic(it) }, label = { Text(it) })
        }
        Panel {
            SectionTitle(when (topic) { "Nutrition" -> "Explore a colourful plate"; "Meals" -> "Make the most of your produce"; else -> "Your impact starts small" })
            Text(when (topic) {
                "Nutrition" -> "Nutrition information by produce type is planned. No personalised nutrition analysis is available."
                "Meals" -> "Sample inspiration · A vegetable bowl, a leafy salad or a simple soup. Recipe recommendations are not connected."
                else -> "Waste and usage insights will appear when real food tracking becomes available. No impact metrics have been calculated."
            })
        }
        Panel { SectionTitle("One useful habit", "Plan a flexible meal around the vegetables you already have.") }
    }
}
