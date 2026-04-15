package com.example.podvalidator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DeliveryValidatorApp()
                }
            }
        }
    }
}

@Composable
fun DeliveryValidatorApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { DeliveryRepository(context) }
    var waybill by remember { mutableStateOf("") }
    var selectedDelivery by remember { mutableStateOf<DeliveryPoint?>(null) }
    var status by remember { mutableStateOf("Enter a waybill number to begin.") }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var validationResult by remember { mutableStateOf<ValidationResult?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] == true
        val locationGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        status = when {
            !cameraGranted && !locationGranted -> "Camera and location permissions are required."
            !cameraGranted -> "Camera permission is required."
            !locationGranted -> "Location permission is required."
            else -> "Permissions granted. You can proceed."
        }
    }

    val enableLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        status = if (isLocationEnabled(context)) {
            "Location services enabled."
        } else {
            "Location must stay enabled for this app to work."
        }
    }

    val capturePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            status = "Photo capture cancelled."
        } else {
            photoBitmap = bitmap
            scope.launch {
                val delivery = selectedDelivery
                if (delivery == null) {
                    status = "Select a valid waybill first."
                    return@launch
                }
                validationResult = validateDelivery(context, bitmap, delivery)
                status = validationResult?.summary ?: "Validation complete."
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("POD Delivery Validator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Workflow: enter waybill → app finds delivery point → user must capture live photo → app checks for visible face/person risk and current location → app decides if delivery is genuine.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = waybill,
            onValueChange = {
                waybill = it.trim().uppercase()
                selectedDelivery = repo.findWaybill(waybill)
                validationResult = null
                status = if (selectedDelivery == null) {
                    "Waybill not found yet."
                } else {
                    "Waybill found. Review address and capture photo."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Waybill Number") },
            singleLine = true
        )

        selectedDelivery?.let { delivery ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Mapped Delivery", fontWeight = FontWeight.SemiBold)
                    Text("Waybill: ${delivery.waybill}")
                    Text("Customer: ${delivery.customerName}")
                    Text("Address: ${delivery.address}")
                    Text("Expected GPS: ${delivery.latitude}, ${delivery.longitude}")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (!isLocationEnabled(context)) {
                    requestLocationEnable(context, enableLocationLauncher) {
                        status = "Location must be enabled. The app will not work otherwise."
                    }
                } else {
                    status = "Location is already enabled."
                }
            }) {
                Text("Check Location")
            }
            Button(onClick = {
                selectedDelivery = repo.findWaybill(waybill.trim().uppercase())
                if (selectedDelivery == null) {
                    status = "Enter a valid waybill first."
                    return@Button
                }
                if (!isLocationEnabled(context)) {
                    status = "Location is OFF. Turn it on to continue."
                    requestLocationEnable(context, enableLocationLauncher) {}
                    return@Button
                }
                capturePhotoLauncher.launch(null)
            }) {
                Text("Take Live Photo")
            }
        }

        Text(status, style = MaterialTheme.typography.bodyLarge)

        photoBitmap?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Latest Captured Photo", fontWeight = FontWeight.SemiBold)
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Captured live proof photo",
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
            }
        }

        validationResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Validation Result", fontWeight = FontWeight.Bold)
                    Text("Decision: ${if (result.isGenuine) "GENUINE DELIVERY" else "NOT GENUINE / NEED REVIEW"}")
                    Text("Face/person risk found: ${if (result.faceDetected) "Yes" else "No"}")
                    Text("Captured GPS: ${result.capturedLatitude}, ${result.capturedLongitude}")
                    Text("Expected GPS: ${result.expectedLatitude}, ${result.expectedLongitude}")
                    Text("Distance from mapped delivery point: ${"%.2f".format(result.distanceMeters)} meters")
                    Text("Rule threshold: ${result.allowedRadiusMeters.toInt()} meters")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(result.summary)
                }
            }
        }
    }
}

private suspend fun validateDelivery(
    context: Context,
    bitmap: Bitmap,
    delivery: DeliveryPoint
): ValidationResult {
    val faceDetected = detectFace(bitmap)
    val location = getCurrentLocation(context)
    if (location == null) {
        return ValidationResult(
            isGenuine = false,
            faceDetected = faceDetected,
            capturedLatitude = null,
            capturedLongitude = null,
            expectedLatitude = delivery.latitude,
            expectedLongitude = delivery.longitude,
            distanceMeters = Double.MAX_VALUE,
            allowedRadiusMeters = delivery.allowedRadiusMeters,
            summary = "Could not fetch live location. Delivery cannot be validated."
        )
    }

    val distanceMeters = distanceMeters(
        location.latitude,
        location.longitude,
        delivery.latitude,
        delivery.longitude
    )

    val withinRadius = distanceMeters <= delivery.allowedRadiusMeters
    val isGenuine = withinRadius && !faceDetected
    val summary = when {
        faceDetected && !withinRadius -> "Delivery rejected: visible person/face detected and delivery location does not match mapped address."
        faceDetected -> "Delivery rejected: visible person/face detected. Retake the photo without any person in frame."
        !withinRadius -> "Delivery rejected: captured location is outside the allowed delivery radius."
        else -> "Delivery validated successfully. Live location matches the mapped destination and no visible face was detected."
    }

    return ValidationResult(
        isGenuine = isGenuine,
        faceDetected = faceDetected,
        capturedLatitude = location.latitude,
        capturedLongitude = location.longitude,
        expectedLatitude = delivery.latitude,
        expectedLongitude = delivery.longitude,
        distanceMeters = distanceMeters,
        allowedRadiusMeters = delivery.allowedRadiusMeters,
        summary = summary
    )
}

private suspend fun detectFace(bitmap: Bitmap): Boolean {
    val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    val image = InputImage.fromBitmap(bitmap, 0)
    val faces = detector.process(image).await()
    detector.close()
    return faces.isNotEmpty()
}

private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun requestLocationEnable(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onFailure: () -> Unit
) {
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
    val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
    val settingsClient = LocationServices.getSettingsClient(context)
    val task = settingsClient.checkLocationSettings(builder.build())
    task.addOnSuccessListener {
        // Already enabled.
    }
    task.addOnFailureListener { exception ->
        if (exception is ResolvableApiException) {
            try {
                launcher.launch(IntentSenderRequest.Builder(exception.resolution).build())
            } catch (_: IntentSender.SendIntentException) {
                onFailure()
            }
        } else {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
private suspend fun getCurrentLocation(context: Context): android.location.Location? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    return try {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
    } catch (_: SecurityException) {
        null
    }
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0].toDouble()
}
