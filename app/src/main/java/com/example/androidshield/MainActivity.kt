package com.example.androidshield

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.androidshield.service.DeviceMonitoringService
import com.example.androidshield.ui.theme.AndroidShieldTheme
import com.example.androidshield.util.BatteryOptimizationHelper

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    private var hasLocationPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var isBatteryOptimizationDisabled by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (hasLocationPermission) {
            requestBackgroundLocationPermission()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            startMonitoringService()
        }
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Background location permission: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            
            checkPermissions()
            checkBatteryOptimization()
            checkServiceStatus()
            
            setContent {
            AndroidShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        hasLocationPermission = hasLocationPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        isBatteryOptimizationDisabled = isBatteryOptimizationDisabled,
                        isServiceRunning = isServiceRunning,
                        onRequestLocationPermission = { requestLocationPermission() },
                        onRequestNotificationPermission = { requestNotificationPermission() },
                        onRequestBatteryOptimization = { requestBatteryOptimization() },
                        onStartService = { startMonitoringService() }
                    )
                }
            }
        }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            // Show error message
            setContent {
                AndroidShieldTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Error: ${e.message}",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
        
        // Auto-start service if permissions are granted (after UI is set)
        if (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Delay service start slightly to ensure UI is ready
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    startMonitoringService()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting service", e)
                }
            }, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        checkBatteryOptimization()
        checkServiceStatus()
    }

    private fun checkPermissions() {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkBatteryOptimization() {
        isBatteryOptimizationDisabled = BatteryOptimizationHelper.isBatteryOptimizationDisabled(this)
    }

    private fun checkServiceStatus() {
        try {
            // Check if service is running
            val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            val serviceClassName = DeviceMonitoringService::class.java.name
            isServiceRunning = runningServices.any { it.service.className == serviceClassName }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service status", e)
            isServiceRunning = false
        }
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitoringService()
        }
    }

    private fun requestBatteryOptimization() {
        BatteryOptimizationHelper.requestDisableBatteryOptimization(this)
    }

    private fun startMonitoringService() {
        try {
            if (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                DeviceMonitoringService.startService(this)
                isServiceRunning = true
                Log.d(TAG, "Service started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting monitoring service", e)
            e.printStackTrace()
        }
    }
}

@Composable
fun MainScreen(
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    isBatteryOptimizationDisabled: Boolean,
    isServiceRunning: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onStartService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Device Monitoring Service",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        PermissionStatusCard(
            title = "Location Permission",
            isGranted = hasLocationPermission,
            onRequest = onRequestLocationPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionStatusCard(
            title = "Notification Permission",
            isGranted = hasNotificationPermission,
            onRequest = onRequestNotificationPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionStatusCard(
            title = "Battery Optimization",
            isGranted = isBatteryOptimizationDisabled,
            onRequest = onRequestBatteryOptimization
        )

        Spacer(modifier = Modifier.height(32.dp))

        ServiceStatusCard(isRunning = isServiceRunning)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartService,
            enabled = hasNotificationPermission || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Monitoring Service")
        }
    }
}

@Composable
fun PermissionStatusCard(
    title: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isGranted) "Granted" else "Not Granted",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!isGranted) {
                TextButton(onClick = onRequest) {
                    Text("Request")
                }
            }
        }
    }
}

@Composable
fun ServiceStatusCard(isRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Service Status",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isRunning) "Running" else "Stopped",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}