package com.freshcheck.app.ui.home
import com.freshcheck.app.ui.myfood.FoodItemUi
import com.freshcheck.app.ui.insights.ScanHistoryRecord
import com.freshcheck.app.ui.result.FreshnessCondition

data class HomeUiState(
    val totalFood: Int = 0, val useSoonCount: Int = 0,
    val useSoonNames: List<String> = emptyList(), val recentScan: ScanHistoryRecord? = null,
    val isDemo: Boolean = false
)
fun homeSummary(food: List<FoodItemUi>, history: List<ScanHistoryRecord>): HomeUiState {
    val items = food.distinctBy { it.id }
    val soon = items.filter { it.condition == FreshnessCondition.UseSoon }
    return HomeUiState(items.size, soon.size, soon.map { it.name }, history.maxByOrNull { it.at },
        items.any { it.isDemo } || history.any { it.isDemo })
}
