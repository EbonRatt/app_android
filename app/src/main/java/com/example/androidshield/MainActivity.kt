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
import com.google.firebase.messaging.FirebaseMessaging
import android.provider.Settings

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    
    private var hasLocationPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var isBatteryOptimizationDisabled by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var fcmToken by mutableStateOf<String?>(null)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (hasLocationPermission) {
            requestBackgroundLocationPermission()
        }
        
        // Continue with next permission request
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            autoRequestPermissions()
        }, 300)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            startMonitoringService()
        }
        
        // Continue with next permission request
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            autoRequestPermissions()
        }, 300)
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
            getFcmToken()
            
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
                        fcmToken = fcmToken,
                        onRequestLocationPermission = { requestLocationPermission() },
                        onRequestNotificationPermission = { requestNotificationPermission() },
                        onRequestBatteryOptimization = { requestBatteryOptimization() },
                        onStartService = { startMonitoringService() },
                        onGetFcmToken = { getFcmToken() }
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
        
        // Auto-request permissions after UI is set up
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            autoRequestPermissions()
        }, 500)
    }

    private fun autoRequestPermissions() {
        // Request location permission first if not granted
        if (!hasLocationPermission) {
            Log.d(TAG, "Auto-requesting location permission")
            requestLocationPermission()
            return
        }

        // Request notification permission if not granted
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "Auto-requesting notification permission")
            requestNotificationPermission()
            return
        }

        // Request battery optimization if not disabled
        if (!isBatteryOptimizationDisabled) {
            Log.d(TAG, "Auto-requesting battery optimization")
            requestBatteryOptimization()
            return
        }

        // Start service if all permissions are granted
        if (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "All permissions granted, starting service")
            startMonitoringService()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        checkBatteryOptimization()
        checkServiceStatus()
        
        // Auto-request permissions if not granted when returning to app
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            autoRequestPermissions()
        }, 500)
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

    private fun getFcmToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                fcmToken = token
                Log.d(TAG, "FCM Registration Token: $token")
                // Token will be automatically registered by MyFirebaseMessagingService.onNewToken()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token", e)
        }
    }
}

@Composable
fun MainScreen(
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    isBatteryOptimizationDisabled: Boolean,
    isServiceRunning: Boolean,
    fcmToken: String?,
    onRequestLocationPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onStartService: () -> Unit,
    onGetFcmToken: () -> Unit
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

        Spacer(modifier = Modifier.height(16.dp))

        FcmTokenCard(
            token = fcmToken,
            onGetToken = onGetFcmToken
        )

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

@Composable
fun FcmTokenCard(
    token: String?,
    onGetToken: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (token != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FCM Token",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (token != null) "Available" else "Not Available",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (token != null) {
                        Text(
                            text = token,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TextButton(onClick = onGetToken) {
                    Text("Refresh")
                }
            }
        }
    }
}