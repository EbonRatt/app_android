package com.example.androidshield.api

import com.example.androidshield.data.DeviceInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {
    @POST("sync/device/information/listener")
    suspend fun sendDeviceData(@Body deviceInfo: DeviceInfo): Response<ApiResponse>

    @POST
    suspend fun registerFcmToken(@Url url: String, @Body request: TokenRegisterRequest): Response<TokenRegisterResponse>
}

data class ApiResponse(
    val success: Boolean,
    val message: String?,
    val brand: String?,
    val deviceId: String?
)

data class TokenRegisterResponse(
    val success: Boolean,
    val message: String?
)

