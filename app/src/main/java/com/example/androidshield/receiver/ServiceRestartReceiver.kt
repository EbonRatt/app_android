package com.example.androidshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.androidshield.service.DeviceMonitoringService

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.PACKAGE_REPLACED" ||
            intent.action == Intent.ACTION_PACKAGE_RESTARTED
        ) {
            Log.d(TAG, "Package replaced/restarted, starting DeviceMonitoringService")
            DeviceMonitoringService.startService(context)
        }
    }

    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }
}

