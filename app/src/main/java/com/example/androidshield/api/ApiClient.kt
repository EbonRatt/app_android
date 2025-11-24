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
     * IMPORTANT: Replace this with your actual backend API base URL
     * Example: "https://api.yourdomain.com/api/"
     * Make sure the URL ends with a trailing slash
     * For local IP with HTTPS, you may need to handle self-signed certificates
     */
    private const val BASE_URL = "https://api.amapi.site/api/v1/"

    init {
        Log.d(TAG, "========== API Client Initialized ==========")
        Log.d(TAG, "Base URL: $BASE_URL")
        Log.d(TAG, "Device Endpoint URL: ${BASE_URL}devices/sync/device/information/listener")
        Log.d(TAG, "Token Endpoint URL: ${BASE_URL}notifications/token/register")
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

