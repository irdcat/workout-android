package com.github.irdcat.workout

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** Runs device tests in [HiltTestApplication], so `@HiltAndroidTest` classes get the test bindings. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(classLoader, HiltTestApplication::class.java.name, context)

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        // Granted up front, so the app's request (on opening a session) doesn't put a system dialog over the tests.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            uiAutomation.grantRuntimePermission(targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
