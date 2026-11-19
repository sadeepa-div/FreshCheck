package com.freshcheck.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Instrumentation never executes FreshCheckApplication's production reconciliation startup. */
class FreshCheckTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
