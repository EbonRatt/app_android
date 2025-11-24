package com.example.androidshield

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
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
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.androidshield.api.ApiClient
import com.example.androidshield.api.TokenRegisterRequest
import com.example.androidshield.service.DeviceMonitoringService
import com.example.androidshield.ui.theme.AndroidShieldTheme
import com.example.androidshield.util.BatteryOptimizationHelper
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private var hasLocationPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var isBatteryOptimizationDisabled by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)
    private var fcmToken by mutableStateOf<String?>(null)
    private var deviceId by mutableStateOf<String?>(null)
    private var enterpriseName by mutableStateOf<String?>(null)
    private var enterpriseEnrollmentId by mutableStateOf<String?>(null)

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
            val (enrollmentId, orgName) = getEnterpriseInfo()
            enterpriseName = orgName
            enterpriseEnrollmentId = enrollmentId
            // If enrollment ID exists, device is enterprise-managed
            deviceId = enrollmentId ?: retrieveDeviceIdWithoutEnterprise()

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
                        deviceId = deviceId,
                        enterpriseName = enterpriseName,
                        enterpriseEnrollmentId = enterpriseEnrollmentId,
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
            requestLocationPermission()
            return
        }

        // Request notification permission if not granted
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
            return
        }

        // Request battery optimization if not disabled
        if (!isBatteryOptimizationDisabled) {
            requestBatteryOptimization()
            return
        }

        // Start service if all permissions are granted
        if (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
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

    private fun getEnterpriseInfo(): Pair<String?, String?> {
        // Returns Pair<enrollmentId, organizationName>
        // Note: Organization name is not directly accessible without device admin privileges
        // The enrollment ID itself uniquely identifies the enterprise enrollment
        // Works for both fully managed devices and BYOD (Work Profile) mode
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val enrollmentId = devicePolicyManager.enrollmentSpecificId

                    if (!enrollmentId.isNullOrEmpty()) {
                        Log.d(TAG, "Enterprise Enrollment ID: $enrollmentId")

                        // Check if device is in work profile (BYOD mode)
                        val isWorkProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            try {
                                val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
                                userManager.isManagedProfile
                            } catch (e: Exception) {
                                false
                            }
                        } else {
                            false
                        }

                        if (isWorkProfile) {
                            Log.d(TAG, "Device is in BYOD mode (Work Profile)")
                            Log.d(TAG, "Enrollment ID identifies the enterprise managing the work profile")
                        } else {
                            Log.d(TAG, "Device is fully managed")
                        }

                        // Enrollment ID itself identifies which enterprise manages this device/work profile
                        return Pair(enrollmentId, null)
                    }
                } catch (e: SecurityException) {
                    Log.d(TAG, "Device is not enterprise-managed or enrollment ID not available: ${e.message}")
                }
            }
            Pair(null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting enterprise info: ${e.message}", e)
            Pair(null, null)
        }
    }

    private fun retrieveDeviceIdWithoutEnterprise(): String? {
        // This function is called when enterprise enrollment ID is not available
        return try {
            // Priority 2: Serial Number (hardware identifier) - requires READ_PHONE_STATE permission
            // Note: Even with permission, Build.getSerial() may throw SecurityException on newer Android versions
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Check if we have permission for Android 8.0+
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_PHONE_STATE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            val serialNumber = Build.getSerial()
                            if (!serialNumber.isNullOrEmpty() && serialNumber != "unknown") {
                                Log.d(TAG, "Using Serial Number: $serialNumber")
                                Log.d(TAG, "Device ID Length: ${serialNumber.length}")
                                return serialNumber
                            } else {
                                Log.d(TAG, "Serial number is null, empty, or 'unknown', using fallback")
                            }
                        } catch (e: SecurityException) {
                            Log.d(TAG, "SecurityException when calling Build.getSerial(): ${e.message}")
                            Log.d(TAG, "App does not meet requirements to access device identifiers, using fallback")
                        }
                    } else {
                        Log.d(TAG, "READ_PHONE_STATE permission not granted, skipping serial number")
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val serialNumber = Build.SERIAL
                    if (!serialNumber.isNullOrEmpty() && serialNumber != "unknown") {
                        Log.d(TAG, "Using Serial Number: $serialNumber")
                        Log.d(TAG, "Device ID Length: ${serialNumber.length}")
                        return serialNumber
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Exception when getting serial number: ${e.message}")
            }

            // Priority 3: Android ID (fallback)
            val androidId = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ANDROID_ID
            )
            Log.d(TAG, "Using Android ID: $androidId")
            Log.d(TAG, "Device ID Length: ${androidId?.length}")
            androidId
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device ID: ${e.message}", e)
            null
        }
    }

    private fun getFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                fcmToken = null
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            fcmToken = token
            Log.d(TAG, "FCM Registration Token: $token")

            // Register token with backend database
            if (token != null) {
                registerFcmTokenWithBackend(token)
            }
        }
    }

    private fun registerFcmTokenWithBackend(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Safely get enterprise enrollment ID with fallback
                val enrollmentId = try {
                    val (id, _) = getEnterpriseInfo()
                    if (!id.isNullOrEmpty()) {
                        id
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting enterprise enrollment ID, using fallback: ${e.message}")
                    null
                }

                // Create request with fallback handling
                val request = TokenRegisterRequest(
                    token = token,
                    enterpriseEnrollmentId = enrollmentId
                )

                Log.d(TAG, "Registering FCM token")
                Log.d(TAG, "Token: ${token.take(20)}...")
                Log.d(TAG, "Type: DEVICE")
                if (enrollmentId != null) {
                    Log.d(TAG, "Enterprise Enrollment ID: $enrollmentId")
                } else {
                    Log.d(TAG, "Enterprise Enrollment ID: null (device not enterprise-managed)")
                }

                val response = ApiClient.apiService.registerFcmToken(request)

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d(TAG, "FCM token registered successfully in database: ${responseBody?.message}")
                } else {
                    Log.e(TAG, "Failed to register FCM token in database: ${response.code()} - ${response.message()}")
                    response.errorBody()?.let {
                        try {
                            Log.e(TAG, "Error body: ${it.string()}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading error body: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering FCM token in database", e)
                e.printStackTrace()
            }
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
    deviceId: String?,
    enterpriseName: String?,
    enterpriseEnrollmentId: String?,
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

        Spacer(modifier = Modifier.height(16.dp))

        DeviceIdCard(
            deviceId = deviceId,
            enterpriseName = enterpriseName,
            enterpriseEnrollmentId = enterpriseEnrollmentId
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
                        text = if (token != null) "Registered" else "Not Available",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (token != null) {
                        Text(
                            text = token.take(50) + if (token.length > 50) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
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

@Composable
fun DeviceIdCard(deviceId: String?, enterpriseName: String?, enterpriseEnrollmentId: String?) {
    val isEnterpriseManaged = !enterpriseEnrollmentId.isNullOrEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (deviceId != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Device ID",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (deviceId != null) "Available" else "Not Available",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Show Enterprise Enrollment ID if available
            if (isEnterpriseManaged) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enterprise Enrollment ID:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = enterpriseEnrollmentId ?: "N/A",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Device is enterprise-managed (BYOD or Fully Managed)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Device is not enterprise-managed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (enterpriseName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enterprise: $enterpriseName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (deviceId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Device Identifier:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = deviceId,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Length: ${deviceId.length} characters",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
