package com.example.androidshield.data

data class DeviceInfo(
    val deviceId: String,
    val brand: String,
    val model: String,
    val networkIpAddress: String,
    val storageUsed: Long,
    val storageTotal: Long,
    val latitude: Double?,
    val longitude: Double?,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

