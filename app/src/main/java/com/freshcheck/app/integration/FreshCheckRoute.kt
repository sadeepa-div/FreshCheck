package com.freshcheck.app.integration

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshcheck.app.navigation.FreshCheckApp

@Composable
fun FreshCheckRoute() {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) { FreshCheckViewModelFactory(context) }
    val owner: FreshCheckViewModel = viewModel(factory = factory)
    val data by owner.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val actions = remember(owner, scope, data.inventoryConnected) {
        owner.integrationActions(scope, data.inventoryConnected)
    }
    FreshCheckApp(data = data, actions = actions, production = true)
}
