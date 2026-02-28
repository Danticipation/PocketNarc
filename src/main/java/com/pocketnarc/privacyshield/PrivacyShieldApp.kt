package com.pocketnarc.privacyshield

import android.app.Application
import android.util.Log

/**
 * Custom Application class for Privacy Shield.
 * Use this for global initialization (crash reporting, Hilt, WorkManager, etc.).
 */
class PrivacyShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Optional: log startup for debugging
        Log.i(TAG, "Privacy Shield Application started")

        // Add future global setup here:
        // - Hilt initialization (if using DI)
        // - Crashlytics / Sentry
        // - WorkManager periodic tasks (e.g. signature updates)
        // - StrictMode for dev builds
    }

    companion object {
        private const val TAG = "PrivacyShieldApp"
    }
}