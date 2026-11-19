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
import com.freshcheck.app.ui.settings.ThemePreference
import com.freshcheck.app.ui.history.HistoryFlow
import com.freshcheck.app.ui.insights.*
import com.freshcheck.app.ui.home.homeSummary
import com.freshcheck.app.ui.myfood.sampleFood
import com.freshcheck.app.ui.scan.ScanRequest
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private enum class Destination(val title: String) { Home("Home"), MyFood("My Food"), Scan("Scan"), Insights("Insights"), Profile("Profile") }

/** UI-only destination state. Feature screens receive callbacks, with no persistence or inference dependencies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreshCheckApp(initialTheme: ThemePreference = ThemePreference.System,
    data: AppUiData = AppUiData(), actions: AppIntegrationActions = AppIntegrationActions(),
    production: Boolean = false) {
    var preference by rememberSaveable { mutableStateOf(initialTheme.name) }
    val choice = ThemePreference.valueOf(preference)
    val dark = when (choice) {
        ThemePreference.System -> isSystemInDarkTheme()
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    FreshCheckTheme(darkTheme = dark) {
        FreshCheckContent(choice, { preference = it.name }, data, actions, production)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreshCheckContent(theme: ThemePreference, onTheme: (ThemePreference) -> Unit,
    data: AppUiData, actions: AppIntegrationActions, production: Boolean) {
    var selected by rememberSaveable { mutableStateOf(Destination.Home.name) }
    var name by rememberSaveable { mutableStateOf("") }
    var samples by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf("Before Buying") }
    var topic by rememberSaveable { mutableStateOf("Overview") }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var openFoodId by rememberSaveable { mutableStateOf<String?>(null) }
    val inventory = rememberSaveable(saver = DemoInventory.StateSaver) { DemoInventory() }
    // Production remains explicit during loading/failure; never fall back to session samples.
    val usesRepository = production || data.inventoryConnected
    val activeFood = if (usesRepository) data.activeFood else inventory.items
    val history = data.scanHistory + if (usesRepository) emptyList() else inventory.history
    val outcomes = if (usesRepository) emptyList() else inventory.outcomes
    var rescanFoodId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var nutritionProduceId by rememberSaveable { mutableStateOf<String?>(null) }
    var scanReturn by rememberSaveable { mutableStateOf(Destination.Home.name) }
    var insightsReturn by rememberSaveable { mutableStateOf(Destination.Home.name) }
    var insightsEntry by rememberSaveable { mutableStateOf(0) }
    val holder = rememberSaveableStateHolder()
    LaunchedEffect(data.foodToOpen?.token) {
        data.foodToOpen?.let { target ->
            historyOpen = false
            selected = Destination.MyFood.name
            openFoodId = target.foodId
            actions.onFoodOpened()
        }
    }
    val scan: (String) -> Unit = { holder.removeState("scanner-flow"); rescanFoodId = null; requestId = java.util.UUID.randomUUID().toString(); mode = it; scanReturn = Destination.Home.name; selected = Destination.Scan.name }
    val insights: (String) -> Unit = { nutritionProduceId = null; insightsEntry++; topic = it; insightsReturn = selected; selected = Destination.Insights.name }
    // Logical navigation closes the request; configuration recreation keeps the same request alive.
    LaunchedEffect(selected) { if (selected != Destination.Scan.name) actions.onScanClosed() }
    if (selected == Destination.Scan.name) {
        val request = ScanRequest(requestId, com.freshcheck.app.ui.scan.ScanMode.entries.first { it.label == mode }, rescanFoodId)
        LaunchedEffect(request) { actions.onScanRequest(request) }
        holder.SaveableStateProvider("scanner-flow") {
        ScannerFlow(mode, { mode = it; requestId = java.util.UUID.randomUUID().toString() },
            onClose = { selected = scanReturn },
            onHome = { selected = Destination.Home.name },
            onNutrition = { insights("Nutrition") },
            request = request,
            providedResult = com.freshcheck.app.ui.scan.analysisFor(request, data.completedAnalysis),
            onNewRequest = { requestId = java.util.UUID.randomUUID().toString() },
            onNutritionForProduce = { id -> insights("Nutrition"); nutritionProduceId = id },
            onSubmit = actions.saveScan,
            providedSubmission = data.completedAnalysis?.takeIf { it.request == request })
        }
        return
    }
    BackHandler(selected != Destination.Home.name) { selected = Destination.Home.name }
    if (historyOpen) Dialog(onDismissRequest = { historyOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.safeDrawingPadding()) {
                HistoryFlow(history, { historyOpen = false }, { id ->
                    openFoodId = id; historyOpen = false; selected = Destination.MyFood.name
                }, actions = actions.history)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Scaffold(
            topBar = { Column {
                CenterAlignedTopAppBar(title = { Text("FreshCheck", style = MaterialTheme.typography.titleLarge) })
                if (data.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                data.error?.let {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                    if (usesRepository) TextButton(actions.retryLoad) { Text("Retry saved data") }
                }
            } },
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
                                { selected = Destination.MyFood.name }, insights,
                                onHistory = { historyOpen = true }, onProfile = { selected = Destination.Profile.name },
                                summary = if (samples) homeSummary(sampleFood(java.time.LocalDate.now()), sampleHistory(java.time.LocalDate.now())) else homeSummary(activeFood, history))
                            Destination.MyFood -> MyFoodFlow(inventory, onScan = { id ->
                                rescanFoodId = id; requestId = java.util.UUID.randomUUID().toString()
                                holder.removeState("scanner-flow")
                                mode = "After Buying"; scanReturn = Destination.MyFood.name; selected = Destination.Scan.name
                            }, onRescue = { insights("Rescue") }, openItemId = openFoodId, onOpenHandled = { openFoodId = null },
                                connectedItems = if (usesRepository) data.activeFood else null, mutations = actions.food,
                                reminderData = data.reminderData, reminderActions = actions.reminders)
                            Destination.Scan -> Unit // Full-screen scanner is hosted above the dashboard scaffold.
                            Destination.Insights -> InsightsScreen(topic, { topic = it }, activeFood,
                                onExit = { selected = insightsReturn }, onInventory = { selected = Destination.MyFood.name }, entryId = insightsEntry,
                                onHistory = { historyOpen = true }, history = history,
                                usedEvents = data.usedEvents + outcomes.filter { it.outcome == FoodOutcome.Used },
                                discardedEvents = data.discardedEvents + outcomes.filter { it.outcome == FoodOutcome.Discarded },
                                produceId = nutritionProduceId, nutritionProfile = data.nutritionProfile)
                            Destination.Profile -> ProfileScreen(name, { name = it }, activeFood.size,
                                history.size, theme, onTheme,
                                onHistory = { historyOpen = true }, onInsights = { insights("Overview") },
                                onFood = { selected = Destination.MyFood.name }, historyActions = actions.history, reminderData = data.reminderData, reminderActions = actions.reminders, containsSamples = activeFood.any { it.isDemo } || history.any { it.isDemo })
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
private fun DarkPreview() { FreshCheckApp(initialTheme = ThemePreference.Dark) }

@Preview(name = "Sample dashboard", showBackground = true, heightDp = 1600)
@Composable
private fun SamplePreview() {
    FreshCheckTheme { HomeScreen("Alex", true, {}, {}, {}, {}) }
}
