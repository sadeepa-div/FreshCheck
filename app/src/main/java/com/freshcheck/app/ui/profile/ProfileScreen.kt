package com.freshcheck.app.ui.profile
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.freshcheck.app.ui.components.*
@Composable
fun ProfileScreen(name: String, onName: (String) -> Unit) {
    Page {
        SectionTitle("Make yourself at home", "No account needed.")
        Panel {
            OutlinedTextField(name, { onName(it.take(32)) }, Modifier.fillMaxWidth(),
                label = { Text("What should we call you?") }, singleLine = true,
                supportingText = { Text("Optional · used only for your greeting in this session.") })
            Text("Your name is kept through screen rotation, but is not saved to an account or database.")
        }
        Panel {
            SectionTitle("FreshCheck", "EC5208 · Mobile Application Development")
            Text("Phase 1 · UI foundation")
            Text("Designed to help you explore visual produce freshness and reduce food waste. Appearance cannot confirm food safety.")
        }
    }
}
