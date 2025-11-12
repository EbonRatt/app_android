package com.example.androidshield.api

import com.example.androidshield.data.DeviceInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("data")
    suspend fun sendDeviceData(@Body deviceInfo: DeviceInfo): Response<ApiResponse>
}

data class ApiResponse(
    val success: Boolean,
    val message: String?
)

