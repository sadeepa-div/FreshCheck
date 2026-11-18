package com.freshcheck.app.integration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStarted
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Suppress disposed-screen callbacks; defer background delivery until the same owner is started. */
class UiCompletionGate(
    private val scope: CoroutineScope,
    private val beforeDelivery: suspend () -> Unit = {}
) {
    fun <T> once(completion: (T) -> Unit): (T) -> Unit {
        val delivered = AtomicBoolean(false)
        return { value ->
            if (delivered.compareAndSet(false, true)) scope.launch(Dispatchers.Main.immediate) {
                beforeDelivery()
                ensureActive()
                completion(value)
            }
        }
    }
}

@Composable
fun rememberUiCompletionGate(): UiCompletionGate {
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(scope, lifecycle) { UiCompletionGate(scope) { lifecycle.withStarted { } } }
}
