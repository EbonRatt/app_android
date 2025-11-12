package com.example.androidshield.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptimizationHelper"

    /**
     * Check if battery optimization is disabled for this app
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val packageName = context.packageName
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true // For older versions, assume it's disabled
    }

    /**
     * Request to disable battery optimization
     */
    fun requestDisableBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isBatteryOptimizationDisabled(context)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open battery optimization settings", e)
                    // Fallback: Open battery settings page
                    openBatterySettings(context)
                }
            }
        }
    }

    /**
     * Open battery settings page as fallback
     */
    private fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery settings", e)
        }
    }
}

