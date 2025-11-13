package com.example.androidshield.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.androidshield.MainActivity
import com.example.androidshield.R
import com.example.androidshield.api.ApiClient
import com.example.androidshield.data.DeviceInfoCollector
import com.example.androidshield.util.WorkManagerHelper
import kotlinx.coroutines.*
import android.util.Log

class DeviceMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val deviceInfoCollector by lazy { DeviceInfoCollector(this) }
    private val apiService = ApiClient.apiService

    // Update interval in milliseconds (default: 5 minutes)
    private var updateInterval: Long = 1 * 60 * 1000

    private val wakeLock: PowerManager.WakeLock? by lazy {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DeviceMonitoringService::WakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L /*10 hours*/)
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            startPeriodicDataCollection()
            // Schedule WorkManager as backup
            WorkManagerHelper.schedulePeriodicWork(this)
            Log.d(TAG, "DeviceMonitoringService created")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            // Try to start with a basic notification if the custom one fails
            try {
                val basicNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Device Monitoring")
                    .setContentText("Service running")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setOngoing(true)
                    .build()
                startForeground(NOTIFICATION_ID, basicNotification)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to create basic notification", e2)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DeviceMonitoringService started")
        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        Log.d(TAG, "DeviceMonitoringService destroyed")
        // Restart service
        restartService()
    }

    private fun startPeriodicDataCollection() {
        serviceScope.launch {
            while (isActive) {
                try {
                    collectAndSendData()
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting/sending data", e)
                }
                delay(updateInterval)
            }
        }
    }

    private suspend fun collectAndSendData() {
        try {
            Log.d(TAG, "========== Starting Data Collection & Send ==========")
            Log.d(TAG, "Timestamp: ${System.currentTimeMillis()}")
            
            // Collect device info
            Log.d(TAG, "Step 1: Collecting device information...")
            val deviceInfo = deviceInfoCollector.collectDeviceInfo()
            Log.d(TAG, "Step 1 Complete: Device info collected")
            
            // Prepare API call
            Log.d(TAG, "Step 2: Preparing to send data to API...")
            Log.d(TAG, "API Endpoint: http://192.168.42.113:9093/api/v1/devices/data")
            Log.d(TAG, "Request Body (JSON):")
            Log.d(TAG, "  - deviceId: ${deviceInfo.deviceId}")
            Log.d(TAG, "  - brand: ${deviceInfo.brand}")
            Log.d(TAG, "  - model: ${deviceInfo.model}")
            Log.d(TAG, "  - networkIpAddress: ${deviceInfo.networkIpAddress}")
            Log.d(TAG, "  - storageUsed: ${deviceInfo.storageUsed} bytes (${deviceInfo.storageUsed / (1024 * 1024 * 1024)} GB)")
            Log.d(TAG, "  - storageTotal: ${deviceInfo.storageTotal} bytes (${deviceInfo.storageTotal / (1024 * 1024 * 1024)} GB)")
            Log.d(TAG, "  - latitude: ${deviceInfo.latitude}")
            Log.d(TAG, "  - longitude: ${deviceInfo.longitude}")
            Log.d(TAG, "  - batteryPercentage: ${deviceInfo.batteryPercentage}%")
            Log.d(TAG, "  - isCharging: ${deviceInfo.isCharging}")
            Log.d(TAG, "  - timestamp: ${deviceInfo.timestamp}")
            
            // Send to API
            Log.d(TAG, "Step 3: Sending HTTP POST request to API...")
            val response = apiService.sendDeviceData(deviceInfo)
            
            // Log response
            Log.d(TAG, "Step 4: API Response received")
            Log.d(TAG, "  - Status Code: ${response.code()}")
            Log.d(TAG, "  - Is Successful: ${response.isSuccessful}")
            Log.d(TAG, "  - Response Message: ${response.message()}")
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                Log.d(TAG, "  - Response Body: $responseBody")
                Log.d(TAG, "✅ SUCCESS: Data sent successfully to API!")
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "  - Error Body: $errorBody")
                Log.e(TAG, "❌ FAILED: Failed to send data - Status: ${response.code()}, Message: ${response.message()}")
            }
            
            Log.d(TAG, "========== Data Collection & Send Complete ==========")
        } catch (e: Exception) {
            Log.e(TAG, "❌ EXCEPTION in collectAndSendData", e)
            Log.e(TAG, "Exception Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception Message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = try {
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating pending intent", e)
            null
        }

        // Use custom notification icon
        val icon = R.drawable.ic_notification

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Device Monitoring")
            .setContentText("Monitoring device information")
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun restartService() {
        val intent = Intent(this, DeviceMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    companion object {
        private const val TAG = "DeviceMonitoringService"
        private const val CHANNEL_ID = "device_monitoring_channel"
        private const val CHANNEL_NAME = "Device Monitoring"
        private const val CHANNEL_DESCRIPTION = "Service monitoring device information"
        private const val NOTIFICATION_ID = 1

        fun startService(context: Context) {
            val intent = Intent(context, DeviceMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DeviceMonitoringService::class.java)
            context.stopService(intent)
        }
    }
}

