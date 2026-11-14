package com.freshcheck.app.ui.insights
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.freshcheck.app.ui.theme.FreshCheckTheme
import com.freshcheck.app.ui.myfood.sampleFood
import com.freshcheck.app.ui.history.HistoryFlow
import com.freshcheck.app.ui.profile.ProfileScreen
import com.freshcheck.app.ui.settings.*
import java.time.LocalDate

@Preview(name = "Insights · 320dp", widthDp = 320, heightDp = 900)
@Composable
private fun InsightsPreview() { FreshCheckTheme { Surface {
    InsightsDashboard(sampleFood(LocalDate.now()), sampleHistory(LocalDate.now()), {}, {}, {})
} } }
@Preview(name = "History · dark", widthDp = 320, heightDp = 900)
@Composable
private fun HistoryPreview() { FreshCheckTheme(darkTheme = true) { Surface {
    HistoryFlow(sampleHistory(LocalDate.now()), {}, {})
} } }
@Preview(name = "Settings · large text", widthDp = 320, heightDp = 900, fontScale = 1.5f)
@Composable
private fun SettingsPreview() { FreshCheckTheme(darkTheme = true) { Surface {
    SettingsScreen(ThemePreference.Dark, {}, {}, {}, {})
} } }
@Preview(name = "Local profile", widthDp = 320, heightDp = 900)
@Composable
private fun ProfilePreview() { FreshCheckTheme { Surface { ProfileScreen("", {}) } } }
