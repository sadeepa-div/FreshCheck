package com.freshcheck.app.ui.myfood
import androidx.compose.runtime.Composable
import com.freshcheck.app.ui.components.*
@Composable
fun MyFoodScreen(onScan: () -> Unit) {
    Page {
        SectionTitle("My Food", "A little organisation. A lot less waste.")
        EmptyPanel("Your kitchen collection starts here", "No food is stored. Inventory, sorting and reminders will arrive in a later phase.", "Explore scanning", onScan)
        Panel { SectionTitle("Make room for fresher habits", "Keep a shopping list and check your kitchen before your next trip.") }
    }
}
