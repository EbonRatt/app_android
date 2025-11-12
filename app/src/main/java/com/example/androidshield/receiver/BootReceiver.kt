package com.example.androidshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.androidshield.service.DeviceMonitoringService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED
        ) {
            Log.d(TAG, "Boot completed, starting DeviceMonitoringService")
            DeviceMonitoringService.startService(context)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}

