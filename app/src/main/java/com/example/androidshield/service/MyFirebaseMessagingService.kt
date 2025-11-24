package com.example.androidshield.service

import android.util.Log
import com.example.androidshield.api.ApiClient
import com.example.androidshield.api.TokenRegisterRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.provider.Settings

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

    private fun registerTokenWithBackend(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get Android ID for device identification
                val androidId = Settings.Secure.getString(
                    applicationContext.contentResolver,
                    Settings.Secure.ANDROID_ID
                )

                // Create request
                val request = TokenRegisterRequest(
                    token = token,
                    androidId = androidId
                )

                Log.d(TAG, "Registering FCM token")
                Log.d(TAG, "Token: ${token.take(20)}...")
                Log.d(TAG, "Android ID: $androidId")

                val response = ApiClient.apiService.registerFcmToken(ApiClient.FCM_TOKEN_ENDPOINT, request)

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d(TAG, "FCM token registered successfully: ${responseBody?.message}")
                } else {
                    Log.e(TAG, "Failed to register FCM token: ${response.code()} - ${response.message()}")
                    response.errorBody()?.let {
                        try {
                            Log.e(TAG, "Error body: ${it.string()}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading error body: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering FCM token", e)
                e.printStackTrace()
            }
        }
    }
}

