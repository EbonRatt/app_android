package com.example.androidshield.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.androidshield.api.ApiClient
import com.example.androidshield.data.DeviceInfoCollector

class DeviceDataWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "========== WorkManager: Starting Data Collection ==========")
            Log.d(TAG, "WorkManager triggered at: ${System.currentTimeMillis()}")
            
            val collector = DeviceInfoCollector(applicationContext)
            val deviceInfo = collector.collectDeviceInfo()
            
            Log.d(TAG, "WorkManager: Sending data to API...")
            val response = ApiClient.apiService.sendDeviceData(deviceInfo)
            
            Log.d(TAG, "WorkManager: API Response - Status: ${response.code()}, Success: ${response.isSuccessful}")
            
            if (response.isSuccessful) {
                Log.d(TAG, "✅ WorkManager: Data sent successfully!")
                Result.success()
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ WorkManager: Failed to send data - Status: ${response.code()}")
                Log.e(TAG, "Error Body: $errorBody")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ WorkManager: Exception occurred", e)
            Log.e(TAG, "Exception Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception Message: ${e.message}")
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DeviceDataWorker"
    }
}

