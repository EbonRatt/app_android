package com.example.androidshield.api

import com.example.androidshield.data.DeviceInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("devices/sync/device/information/listener")
    suspend fun sendDeviceData(@Body deviceInfo: DeviceInfo): Response<ApiResponse>

    @POST("notifications/token/register/device")
    suspend fun registerFcmToken(@Body request: TokenRegisterRequest): Response<TokenRegisterResponse>
}

data class ApiResponse(
    val success: Boolean,
    val message: String?,
    val brand: String?,
    val deviceId: String?
)

data class TokenRegisterResponse(
    val success: Boolean?,
    val message: String?
)

