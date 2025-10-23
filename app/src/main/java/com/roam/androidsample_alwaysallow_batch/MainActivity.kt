package com.roam.androidsample_alwaysallow_batch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.roam.androidsample_alwaysallow_batch.ui.theme.RoamAndroidSampleAlwaysAllowBatchTheme
import com.roam.sdk.Roam
import com.roam.sdk.callback.TrackingCallback
import com.roam.sdk.models.RoamError

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoamAndroidSampleAlwaysAllowBatchTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TrackingScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun TrackingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isTracking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Tracking is stopped") }
    var isLoading by remember { mutableStateOf(false) }

    // Check tracking status on composition
    LaunchedEffect(Unit) {
        isTracking = Roam.isLocationTracking()
        statusMessage = if (isTracking) "Tracking is started" else "Tracking is stopped"
    }

    // Helper function to actually start tracking (called when all permissions are granted)
    val startTrackingInternal = {
        // Start foreground service first
        ForegroundLocationService.startService(context)

        startTracking(
            onSuccess = { message ->
                isTracking = true
                statusMessage = "Tracking is started"
                isLoading = false
                Toast.makeText(context, "Tracking started successfully", Toast.LENGTH_SHORT)
                    .show()
            },
            onError = { error ->
                // Stop foreground service if tracking failed
                ForegroundLocationService.stopService(context)
                isLoading = false
                Toast.makeText(
                    context,
                    "Failed to start tracking: ${error?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // Notification permission launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Notification permission granted, proceed with starting tracking
            startTrackingInternal()
        } else {
            isLoading = false
            Toast.makeText(
                context,
                "Notification permission is required to show tracking status",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Helper function to start tracking after checking all permissions
    fun startTrackingWithPermissions() {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!notificationPermissionGranted) {
                // Request notification permission first
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // All permissions granted, start tracking
        startTrackingInternal()
    }

    // Background location permission launcher
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startTrackingWithPermissions()
        } else {
            isLoading = false
            Toast.makeText(
                context,
                "Background location permission is required for always-on tracking",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Initial permissions launcher (fine location + phone state)
    val initialPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val phoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false

        if (fineLocationGranted && phoneStateGranted) {
            // Check if we need background location permission (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val backgroundLocationGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!backgroundLocationGranted) {
                    // Request background location permission
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    // All permissions granted, start tracking
                    startTrackingWithPermissions()
                }
            } else {
                // Android 9 and below, no background permission needed
                startTrackingWithPermissions()
            }
        } else {
            isLoading = false
            Toast.makeText(
                context,
                "Location and Phone State permissions are required",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Location Tracking",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = statusMessage,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }

        Button(
            onClick = {
                if (isTracking) {
                    // Stop tracking and foreground service
                    isLoading = true
                    stopTracking(
                        onSuccess = {
                            // Stop foreground service after stopping tracking
                            ForegroundLocationService.stopService(context)
                            isTracking = false
                            statusMessage = "Tracking is stopped"
                            isLoading = false
                            Toast.makeText(context, "Tracking stopped", Toast.LENGTH_SHORT)
                                .show()
                        },
                        onError = {
                            // Stop foreground service even if tracking failed
                            ForegroundLocationService.stopService(context)
                            isLoading = false
                            Toast.makeText(
                                context,
                                "Failed to stop tracking",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                } else {
                    // Check permissions before starting tracking
                    val fineLocationGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val phoneStateGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED

                    val backgroundLocationGranted =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true // Background permission not needed for Android 9 and below
                        }

                    val notificationPermissionGranted =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true // Notification permission not needed for Android 12 and below
                        }

                    if (fineLocationGranted && phoneStateGranted && backgroundLocationGranted && notificationPermissionGranted) {
                        // All permissions granted, start tracking directly
                        isLoading = true
                        startTrackingWithPermissions()
                    } else {
                        // Request missing permissions
                        isLoading = true
                        if (!fineLocationGranted || !phoneStateGranted) {
                            // Request initial permissions first
                            initialPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                            )
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !backgroundLocationGranted) {
                            // Request background location permission
                            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                            // Request notification permission
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (isTracking) "Stop Tracking" else "Start Tracking",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = "Permissions required:\n• Location (Always Allow)\n• Read Phone State\n• Notifications (Android 13+)\n\nTracking automatically includes foreground service with notification for persistent background operation.",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun startTracking(
    onSuccess: (String?) -> Unit,
    onError: (RoamError?) -> Unit
) {
    Roam.startTracking(object : TrackingCallback {
        override fun onSuccess(message: String?) {
            onSuccess(message)
        }

        override fun onError(error: RoamError?) {
            onError(error)
        }
    })
}

private fun stopTracking(
    onSuccess: () -> Unit,
    onError: () -> Unit
) {
    try {
        Roam.stopTracking(object : TrackingCallback {
            override fun onSuccess(message: String?) {
                onSuccess()
            }

            override fun onError(error: RoamError?) {
                onError()
            }
        })
    } catch (e: Exception) {
        onError()
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RoamAndroidSampleAlwaysAllowBatchTheme {
        TrackingScreen()
    }
}