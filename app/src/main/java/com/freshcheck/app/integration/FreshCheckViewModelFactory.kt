package com.freshcheck.app.integration

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.freshcheck.app.data.local.database.FreshCheckDatabase
import com.freshcheck.app.data.repository.RoomFreshCheckRepository

class FreshCheckViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(FreshCheckViewModel::class.java))
        return FreshCheckViewModel(
            repositoryProvider = { RoomFreshCheckRepository(FreshCheckDatabase.getInstance(applicationContext)) },
            reminderProvider = { com.freshcheck.app.reminders.ReminderGraph.get(applicationContext).coordinator }
        ) as T
    }
}
