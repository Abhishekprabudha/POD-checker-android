package com.example.podvalidator

import kotlinx.serialization.Serializable

@Serializable
data class DeliveryPoint(
    val waybill: String,
    val customerName: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val allowedRadiusMeters: Double = 120.0
)

data class ValidationResult(
    val isGenuine: Boolean,
    val faceDetected: Boolean,
    val validationMode: String,
    val waybillEntryLatitude: Double?,
    val waybillEntryLongitude: Double?,
    val capturedLatitude: Double?,
    val capturedLongitude: Double?,
    val expectedLatitude: Double?,
    val expectedLongitude: Double?,
    val waybillToPhotoDistanceMeters: Double?,
    val waybillToBackendDistanceMeters: Double?,
    val photoToBackendDistanceMeters: Double?,
    val allowedRadiusMeters: Double,
    val summary: String
)
