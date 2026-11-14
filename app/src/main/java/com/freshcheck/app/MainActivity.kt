package com.freshcheck.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.freshcheck.app.navigation.FreshCheckApp
import com.freshcheck.app.ui.theme.FreshCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FreshCheckTheme { FreshCheckApp() } }
    }
}
