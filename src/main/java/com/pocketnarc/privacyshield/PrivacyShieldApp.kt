package com.pocketnarc.privacyshield

import android.app.Application
import android.util.Log

/**
 * Custom Application class for PrivatAid.
 */
class PrivacyShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PrivatAid Application started")
    }

    companion object {
        private const val TAG = "PrivatAidApp"
    }
}
