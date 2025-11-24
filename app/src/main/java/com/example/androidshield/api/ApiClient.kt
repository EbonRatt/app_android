package com.example.androidshield.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    
    /**
     * Base URLs for different endpoints
     * Make sure the URLs end with a trailing slash
     */
    private const val BASE_URL = "https://device.amapi.site/api/v1/devices/"
    const val BASE_URL_Notification = "https://notification.amapi.site/api/v1/"
    
    // Full endpoint URLs
    const val FCM_TOKEN_ENDPOINT = "${BASE_URL_Notification}notifications/token/register/device"

    init {
        Log.d(TAG, "========== API Client Initialized ==========")
        Log.d(TAG, "Base URL: $BASE_URL")
        Log.d(TAG, "Device Data Endpoint: ${BASE_URL}sync/device/information/listener")
        Log.d(TAG, "FCM Token Endpoint: $FCM_TOKEN_ENDPOINT")
    }

    private val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
        override fun log(message: String) {
            Log.d(TAG, "HTTP: $message")
        }
    }).apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // For local development with self-signed certificates, uncomment below:
        // .hostnameVerifier { _, _ -> true }
        // .sslSocketFactory(createInsecureSSLSocketFactory(), createInsecureTrustManager())
        .build()

    // Use a dummy base URL since we're using @Url for full URLs
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    /**
     * Update the base URL dynamically if needed
     */
    fun updateBaseUrl(newBaseUrl: String) {
        // This would require recreating the Retrofit instance
        // For now, update BASE_URL constant above
    }
}

