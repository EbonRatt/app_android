package com.example.androidshield.service

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.androidshield.api.ApiClient
import com.example.androidshield.api.TokenRegisterRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "FCMService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")

        // Register token with backend
        registerTokenWithBackend(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // Handle notification here
        }
    }

    private fun getEnterpriseInfo(): Pair<String?, String?> {
        // Returns Pair<enrollmentId, organizationName>
        // Note: Organization name is not directly accessible without device admin privileges
        // The enrollment ID itself uniquely identifies the enterprise enrollment
        // Works for both fully managed devices and BYOD (Work Profile) mode
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val enrollmentId = devicePolicyManager.enrollmentSpecificId

                    if (!enrollmentId.isNullOrEmpty()) {
                        Log.d(TAG, "Enterprise Enrollment ID: $enrollmentId")

                        // Check if device is in work profile (BYOD mode)
                        val isWorkProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            try {
                                val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
                                userManager.isManagedProfile
                            } catch (e: Exception) {
                                false
                            }
                        } else {
                            false
                        }

                        if (isWorkProfile) {
                            Log.d(TAG, "Device is in BYOD mode (Work Profile)")
                            Log.d(TAG, "Enrollment ID identifies the enterprise managing the work profile")
                        } else {
                            Log.d(TAG, "Device is fully managed")
                        }

                        // Enrollment ID itself identifies which enterprise manages this device/work profile
                        return Pair(enrollmentId, null)
                    }
                } catch (e: SecurityException) {
                    Log.d(TAG, "Device is not enterprise-managed or enrollment ID not available: ${e.message}")
                }
            }
            Pair(null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting enterprise info: ${e.message}", e)
            Pair(null, null)
        }
    }

    private fun retrieveDeviceId(): String? {
        return try {
            // Priority 1: Enterprise Enrollment-Specific ID (for managed devices)
            val (enrollmentId, organizationName) = getEnterpriseInfo()
            if (!enrollmentId.isNullOrEmpty()) {
                Log.d(TAG, "Using Enterprise Enrollment ID: $enrollmentId")
                if (organizationName != null) {
                    Log.d(TAG, "Enterprise: $organizationName")
                }
                Log.d(TAG, "Device ID Length: ${enrollmentId.length}")
                return enrollmentId
            }

            // Priority 2: Serial Number (hardware identifier) - requires READ_PHONE_STATE permission
            // Note: Even with permission, Build.getSerial() may throw SecurityException on newer Android versions
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Check if we have permission for Android 8.0+
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.READ_PHONE_STATE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            val serialNumber = Build.getSerial()
                            if (!serialNumber.isNullOrEmpty() && serialNumber != "unknown") {
                                Log.d(TAG, "Using Serial Number: $serialNumber")
                                Log.d(TAG, "Device ID Length: ${serialNumber.length}")
                                return serialNumber
                            } else {
                                Log.d(TAG, "Serial number is null, empty, or 'unknown', using fallback")
                            }
                        } catch (e: SecurityException) {
                            Log.d(TAG, "SecurityException when calling Build.getSerial(): ${e.message}")
                            Log.d(TAG, "App does not meet requirements to access device identifiers, using fallback")
                        }
                    } else {
                        Log.d(TAG, "READ_PHONE_STATE permission not granted, skipping serial number")
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val serialNumber = Build.SERIAL
                    if (!serialNumber.isNullOrEmpty() && serialNumber != "unknown") {
                        Log.d(TAG, "Using Serial Number: $serialNumber")
                        Log.d(TAG, "Device ID Length: ${serialNumber.length}")
                        return serialNumber
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Exception when getting serial number: ${e.message}")
            }

            // Priority 3: Android ID (fallback)
            val androidId = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID
            )
            Log.d(TAG, "Using Android ID: $androidId")
            Log.d(TAG, "Device ID Length: ${androidId?.length}")
            androidId
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID: ${e.message}", e)
            null
        }
    }

    private fun registerTokenWithBackend(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Safely get enterprise enrollment ID with fallback
                val enrollmentId = try {
                    val (id, _) = getEnterpriseInfo()
                    if (!id.isNullOrEmpty()) {
                        id
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting enterprise enrollment ID, using fallback: ${e.message}")
                    null
                }

                // Create request with fallback handling
                val request = TokenRegisterRequest(
                    token = token,
                    enterpriseEnrollmentId = enrollmentId
                )

                Log.d(TAG, "Registering FCM token")
                Log.d(TAG, "Token: ${token.take(20)}...")
                Log.d(TAG, "Type: DEVICE")
                if (enrollmentId != null) {
                    Log.d(TAG, "Enterprise Enrollment ID: $enrollmentId")
                } else {
                    Log.d(TAG, "Enterprise Enrollment ID: null (device not enterprise-managed)")
                }

                val response = ApiClient.apiService.registerFcmToken(request)

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d(TAG, "FCM token registered successfully in database: ${responseBody?.message}")
                } else {
                    Log.e(TAG, "Failed to register FCM token in database: ${response.code()} - ${response.message()}")
                    response.errorBody()?.let {
                        try {
                            Log.e(TAG, "Error body: ${it.string()}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading error body: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering FCM token in database", e)
                e.printStackTrace()
            }
        }
    }
}

