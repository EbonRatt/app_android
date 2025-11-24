package com.example.androidshield.api

data class TokenRegisterRequest(
    val token: String,
    val enterpriseEnrollmentId: String? = ""
)

