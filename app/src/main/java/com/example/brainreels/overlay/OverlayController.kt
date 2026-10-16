package com.example.brainreels.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/** Starts/stops the floating counter. Never assumes the overlay permission is granted. */
object OverlayController {
    private const val TAG = "OverlayController"

    fun start(context: Context) {
        if (!Settings.canDrawOverlays(context)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, FloatingCounterService::class.java))
        } catch (e: Exception) {
            // e.g. background-start restrictions; the widget will start the next time the app is opened.
            Log.w(TAG, "Could not start overlay service", e)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FloatingCounterService::class.java))
    }
}
