package com.example.androidshield.data

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface

class DeviceInfoCollector(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceInfoCollector"
    }

    suspend fun collectDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== Starting Device Info Collection ==========")
        
        val deviceId = getDeviceId()
        Log.d(TAG, "Device ID: $deviceId")
        
        val brand = getBrand()
        Log.d(TAG, "Brand: $brand")
        
        val model = getModel()
        Log.d(TAG, "Model: $model")
        
        val networkIpAddress = getNetworkIpAddress()
        Log.d(TAG, "Network IP Address: $networkIpAddress")
        
        val storageUsed = getStorageUsed()
        val storageTotal = getStorageTotal()
        Log.d(TAG, "Storage - Used: ${storageUsed / (1024 * 1024 * 1024)} GB, Total: ${storageTotal / (1024 * 1024 * 1024)} GB")
        
        val location = getLocation()
        val latitude = location?.latitude
        val longitude = location?.longitude
        Log.d(TAG, "Location - Latitude: $latitude, Longitude: $longitude")
        
        val batteryPercentage = getBatteryPercentage()
        val isCharging = isBatteryCharging()
        Log.d(TAG, "Battery - Percentage: $batteryPercentage%, Is Charging: $isCharging")
        
        val deviceInfo = DeviceInfo(
            deviceId = deviceId,
            brand = brand,
            model = model,
            networkIpAddress = networkIpAddress,
            storageUsed = storageUsed,
            storageTotal = storageTotal,
            latitude = latitude,
            longitude = longitude,
            batteryPercentage = batteryPercentage,
            isCharging = isCharging
        )
        
        Log.d(TAG, "========== Device Info Collection Complete ==========")
        Log.d(TAG, "Collected Data: $deviceInfo")
        
        deviceInfo
    }

    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    private fun getBrand(): String {
        return try {
            Build.BRAND ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting brand: ${e.message}", e)
            "unknown"
        }
    }

    private fun getModel(): String {
        return try {
            Build.MODEL ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model: ${e.message}", e)
            "unknown"
        }
    }

    private fun getNetworkIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "unknown"
                    }
                }
            }
            "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getStorageUsed(): Long {
        return try {
            // Get user-accessible internal storage (what users see in Settings)
            // Note: getExternalStorageDirectory() actually returns internal storage, not SD card
            val storagePath = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                Environment.getExternalStorageDirectory().path
            } else {
                // Fallback to data directory if external storage not available
                Environment.getDataDirectory().path
            }
            
            val storageStat = StatFs(storagePath)
            val blockSize = storageStat.blockSizeLong
            val totalBlocks = storageStat.blockCountLong
            val availableBlocks = storageStat.availableBlocksLong
            val used = (totalBlocks - availableBlocks) * blockSize
            
            Log.d(TAG, "Storage Used (User Internal Storage): ${used / (1024.0 * 1024.0 * 1024.0)} GB")
            Log.d(TAG, "Storage Path: $storagePath")
            used
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage used: ${e.message}", e)
            0L
        }
    }

    private fun getStorageTotal(): Long {
        return try {
            // Get user-accessible internal storage (what users see in Settings)
            // Note: getExternalStorageDirectory() actually returns internal storage, not SD card
            val storagePath = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                Environment.getExternalStorageDirectory().path
            } else {
                // Fallback to data directory if external storage not available
                Environment.getDataDirectory().path
            }
            
            val storageStat = StatFs(storagePath)
            val blockSize = storageStat.blockSizeLong
            val totalBlocks = storageStat.blockCountLong
            val total = totalBlocks * blockSize
            
            Log.d(TAG, "Storage Total (User Internal Storage): ${total / (1024.0 * 1024.0 * 1024.0)} GB")
            Log.d(TAG, "Storage Path: $storagePath")
            total
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage total: ${e.message}", e)
            0L
        }
    }

    private suspend fun getLocation(): Location? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext null
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null

            for (provider in providers) {
                val location = try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
                if (location != null) {
                    if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                        bestLocation = location
                    }
                }
            }
            bestLocation
        } catch (e: Exception) {
            null
        }
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            0
        }
    }

    private fun isBatteryCharging(): Boolean {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }
}

