package com.freshcheck.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.freshcheck.app.ui.home.HomeScreen
import com.freshcheck.app.ui.scan.ScannerFlow
import com.freshcheck.app.ui.myfood.MyFoodFlow
import com.freshcheck.app.ui.myfood.DemoInventory
import com.freshcheck.app.ui.insights.InsightsScreen
import com.freshcheck.app.ui.profile.ProfileScreen
import com.freshcheck.app.ui.theme.FreshCheckTheme

private enum class Destination(val title: String) { Home("Home"), MyFood("My Food"), Scan("Scan"), Insights("Insights"), Profile("Profile") }

/** UI-only destination state. Feature screens receive callbacks, with no persistence or inference dependencies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshCheckApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.Home.name) }
    var name by rememberSaveable { mutableStateOf("") }
    var samples by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf("Before Buying") }
    var topic by rememberSaveable { mutableStateOf("Overview") }
    val inventory = rememberSaveable(saver = DemoInventory.StateSaver) { DemoInventory() }
    var scanReturn by rememberSaveable { mutableStateOf(Destination.Home.name) }
    val holder = rememberSaveableStateHolder()
    val scan: (String) -> Unit = { mode = it; scanReturn = Destination.Home.name; selected = Destination.Scan.name }
    val insights: (String) -> Unit = { topic = it; selected = Destination.Insights.name }
    if (selected == Destination.Scan.name) {
        ScannerFlow(mode, { mode = it },
            onClose = { selected = scanReturn },
            onHome = { selected = Destination.Home.name },
            onNutrition = { insights("Nutrition") })
        return
    }
    BackHandler(selected != Destination.Home.name) { selected = Destination.Home.name }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = {
                Text("FreshCheck", style = MaterialTheme.typography.titleLarge)
            }) },
            bottomBar = {
                if (!wide) NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(selected == item.name, { selected = item.name },
                            icon = { DestinationIcon(item) }, label = { Text(item.title, maxLines = 1) })
                    }
                }
            }
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (wide) NavigationRail {
                    Destination.entries.forEach { item ->
                        NavigationRailItem(selected == item.name, { selected = item.name },
                            icon = { DestinationIcon(item) }, label = { Text(item.title) })
                    }
                }
                AnimatedContent(selected, Modifier.weight(1f).fillMaxHeight(),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "Destination transition") { destination ->
                    holder.SaveableStateProvider(destination) {
                        when (Destination.valueOf(destination)) {
                            Destination.Home -> HomeScreen(name, samples, { samples = it }, scan,
                                { selected = Destination.MyFood.name }, insights)
                            Destination.MyFood -> MyFoodFlow(inventory) { _ ->
                                mode = "After Buying"; scanReturn = Destination.MyFood.name; selected = Destination.Scan.name
                            }
                            Destination.Scan -> Unit // Full-screen scanner is hosted above the dashboard scaffold.
                            Destination.Insights -> InsightsScreen(topic) { topic = it }
                            Destination.Profile -> ProfileScreen(name) { name = it }
                        }
                    }
                }
            }
        }
    }
}

/** Decorative icons: the enclosing navigation item supplies the accessible label and selected state. */
@Composable
private fun DestinationIcon(destination: Destination) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val s = Stroke(1.8.dp.toPx())
        when (destination) {
            Destination.Home -> {
                val path = Path().apply {
                    moveTo(w * .12f, w * .45f); lineTo(w * .5f, w * .12f)
                    lineTo(w * .88f, w * .45f); lineTo(w * .88f, w * .88f)
                    lineTo(w * .12f, w * .88f); close()
                }
                drawPath(path, color, style = s)
            }
            Destination.MyFood -> {
                drawRoundRect(color, Offset(w * .16f, w * .28f), Size(w * .68f, w * .58f), style = s)
                drawLine(color, Offset(w * .3f, w * .14f), Offset(w * .7f, w * .14f), s.width)
            }
            Destination.Scan -> {
                drawRoundRect(color, Offset(w * .1f, w * .2f), Size(w * .8f, w * .65f), style = s)
                drawCircle(color, w * .19f, center = Offset(w * .5f, w * .52f), style = s)
            }
            Destination.Insights -> repeat(3) { i ->
                drawLine(color, Offset(w * (.25f + .25f * i), w * .85f),
                    Offset(w * (.25f + .25f * i), w * (.55f - .18f * i)), s.width * 2)
            }
            Destination.Profile -> {
                drawCircle(color, w * .17f, Offset(w * .5f, w * .28f), style = s)
                drawArc(color, 180f, 180f, false, Offset(w * .15f, w * .53f), Size(w * .7f, w * .6f), style = s)
            }
        }
    }
}

@Preview(name = "Phone · empty state", showBackground = true)
@Preview(name = "Tablet", widthDp = 1000, heightDp = 900)
@Composable
private fun AppPreview() { FreshCheckTheme { FreshCheckApp() } }

@Preview(name = "Dark dashboard", showBackground = true)
@Composable
private fun DarkPreview() { FreshCheckTheme(darkTheme = true) { FreshCheckApp() } }

@Preview(name = "Sample dashboard", showBackground = true, heightDp = 1600)
@Composable
private fun SamplePreview() {
    FreshCheckTheme { HomeScreen("Alex", true, {}, {}, {}, {}) }
}
