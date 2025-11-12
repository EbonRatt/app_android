package com.example.androidshield.util

import android.content.Context
import androidx.work.*
import com.example.androidshield.worker.DeviceDataWorker
import java.util.concurrent.TimeUnit

object WorkManagerHelper {
    private const val WORK_NAME = "device_data_sync_work"
    
    /**
     * Schedule periodic work as a backup mechanism
     */
    fun schedulePeriodicWork(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<DeviceDataWorker>(
            15, TimeUnit.MINUTES // Minimum interval for periodic work
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    /**
     * Cancel the periodic work
     */
    fun cancelPeriodicWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

