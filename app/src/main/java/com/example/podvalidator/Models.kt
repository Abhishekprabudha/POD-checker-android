package com.example.podvalidator

import kotlinx.serialization.Serializable

@Serializable
data class DeliveryPoint(
    val waybill: String,
    val customerName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val allowedRadiusMeters: Double
)

data class ValidationResult(
    val isGenuine: Boolean,
    val faceDetected: Boolean,
    val capturedLatitude: Double?,
    val capturedLongitude: Double?,
    val expectedLatitude: Double,
    val expectedLongitude: Double,
    val distanceMeters: Double,
    val allowedRadiusMeters: Double,
    val summary: String
)
