# Android Background Monitoring Service - Implementation Plan

## Overview
This Android app implements a persistent background service that continuously monitors device information and sends it to a backend API. The service is designed to be resilient and auto-restart after reboots or app kills.

## Architecture Components

### 1. Foreground Service (`DeviceMonitoringService`)
- **Purpose**: Main service that runs continuously in the background
- **Features**:
  - Runs as a foreground service with persistent notification
  - Uses `START_STICKY` flag to auto-restart if killed
  - Maintains a wake lock to prevent CPU sleep
  - Periodically collects and sends device data (default: every 5 minutes)
  - Automatically restarts itself if destroyed

### 2. Device Information Collection (`DeviceInfoCollector`)
- **Collected Data**:
  - Device ID (Android ID)
  - Serial Number
  - Network IP Address
  - Storage (Used and Total)
  - Current Location (Latitude & Longitude)
  - Battery Information (Percentage and Charging Status)
  - Timestamp

### 3. API Communication (`ApiClient` & `ApiService`)
- **Technology**: Retrofit 2 with OkHttp
- **Features**:
  - Secure HTTPS communication
  - Automatic retry on connection failure
  - 30-second timeout for requests
  - Gson for JSON serialization
  - Logging interceptor for debugging

### 4. Auto-Restart Mechanisms

#### a. Boot Receiver (`BootReceiver`)
- Listens for `BOOT_COMPLETED` intent
- Automatically starts service after device reboot
- Handles various boot completion actions for different manufacturers

#### b. Service Restart Receiver (`ServiceRestartReceiver`)
- Listens for package replacement/restart events
- Ensures service restarts after app updates

#### c. WorkManager Backup (`DeviceDataWorker`)
- Periodic work scheduled every 15 minutes (minimum interval)
- Acts as a backup mechanism if foreground service fails
- Ensures data is sent even if service is temporarily stopped

### 5. Battery Optimization Handling (`BatteryOptimizationHelper`)
- Checks if app is excluded from battery optimization
- Requests user to disable battery optimization
- Critical for ensuring service runs continuously

## Permissions Required

### Runtime Permissions (Requested at Runtime)
1. **Location Permissions**:
   - `ACCESS_FINE_LOCATION` - For precise location
   - `ACCESS_COARSE_LOCATION` - For approximate location
   - `ACCESS_BACKGROUND_LOCATION` - For location in background (Android 10+)

2. **Notification Permission**:
   - `POST_NOTIFICATIONS` - For foreground service notification (Android 13+)

### Manifest Permissions (Declared in AndroidManifest.xml)
1. **Network**:
   - `INTERNET`
   - `ACCESS_NETWORK_STATE`
   - `ACCESS_WIFI_STATE`

2. **System**:
   - `WAKE_LOCK` - Keep CPU awake
   - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - Request battery optimization exclusion
   - `RECEIVE_BOOT_COMPLETED` - Auto-start after reboot
   - `FOREGROUND_SERVICE` - Run foreground service
   - `FOREGROUND_SERVICE_DATA_SYNC` - Foreground service type
   - `SYSTEM_ALERT_WINDOW` - For overlay permissions (if needed)

## AndroidManifest.xml Configuration

### Service Declaration
```xml
<service
    android:name=".service.DeviceMonitoringService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync"
    android:stopWithTask="false" />
```
- `stopWithTask="false"`: Service continues even when app is removed from recent apps

### Receiver Declarations
- BootReceiver: Auto-start after reboot
- ServiceRestartReceiver: Restart after package events

## Setup Instructions

### 1. Update API Base URL
Edit `app/src/main/java/com/example/androidshield/api/ApiClient.kt`:
```kotlin
private const val BASE_URL = "https://your-actual-api.com/api/"
```

### 2. Backend API Endpoint
Your backend should have a `POST /deviceData` endpoint that accepts:
```json
{
  "deviceId": "string",
  "serialNumber": "string",
  "networkIpAddress": "string",
  "storageUsed": 0,
  "storageTotal": 0,
  "latitude": 0.0,
  "longitude": 0.0,
  "batteryPercentage": 0,
  "isCharging": false,
  "timestamp": 0
}
```

### 3. Build and Install
1. Sync Gradle files
2. Build the project
3. Install on device/emulator
4. Grant all required permissions
5. Disable battery optimization when prompted
6. Start the service from MainActivity

## Security Considerations

### 1. API Communication
- **HTTPS Only**: Always use HTTPS for API communication
- **Certificate Pinning**: Consider implementing certificate pinning for production
- **Authentication**: Add authentication headers (API keys, tokens) to requests
- **Encryption**: Consider encrypting sensitive data before transmission

### 2. Data Privacy
- Location data is sensitive - ensure compliance with privacy regulations
- Consider user consent dialogs for location tracking
- Implement data retention policies

### 3. Secure Storage
- Store API keys/secrets securely (Android Keystore)
- Don't hardcode sensitive information

## Making Service Persistent

### 1. Foreground Service
- Service runs with a persistent notification
- Cannot be easily killed by the system
- User can see the service is running

### 2. START_STICKY Flag
- Service automatically restarts if killed
- Returns `START_STICKY` in `onStartCommand()`

### 3. Wake Lock
- Prevents CPU from sleeping
- Held for up to 10 hours
- Automatically released when service stops

### 4. Battery Optimization Exclusion
- Critical for continuous operation
- User must grant this permission
- App requests it automatically

### 5. WorkManager Backup
- Scheduled periodic work as fallback
- Ensures data collection continues even if service fails

## Testing Checklist

- [ ] Service starts on app launch
- [ ] Service persists after clearing recent apps
- [ ] Service restarts after device reboot
- [ ] Service restarts after force stop
- [ ] Data is collected correctly
- [ ] Data is sent to API successfully
- [ ] Location permission works
- [ ] Battery optimization exclusion works
- [ ] Notification appears and persists
- [ ] Service survives app updates

## Troubleshooting

### Service Not Starting
1. Check if all permissions are granted
2. Verify battery optimization is disabled
3. Check Logcat for errors
4. Ensure notification permission is granted (Android 13+)

### Data Not Sending
1. Verify API base URL is correct
2. Check network connectivity
3. Review API endpoint implementation
4. Check Logcat for network errors
5. Verify API authentication if required

### Service Being Killed
1. Ensure battery optimization is disabled
2. Check device manufacturer's battery saver settings
3. Some devices (Xiaomi, Huawei, etc.) have aggressive battery management
4. User may need to manually whitelist the app

### Location Not Available
1. Ensure location permissions are granted
2. Enable location services on device
3. Check if GPS is enabled
4. Background location permission may be required (Android 10+)

## Limitations and Considerations

1. **Battery Impact**: Continuous background operation will drain battery
2. **Data Usage**: Regular API calls consume data
3. **Privacy**: Location tracking requires user consent
4. **Manufacturer Restrictions**: Some manufacturers (Xiaomi, Huawei, Samsung) have aggressive battery management
5. **Android Versions**: Behavior may vary across Android versions
6. **Google Play Policies**: Ensure compliance with Google Play policies regarding background services

## Best Practices

1. **Error Handling**: All network calls have proper error handling
2. **Retry Logic**: Failed requests are retried automatically
3. **Logging**: Comprehensive logging for debugging
4. **Resource Management**: Wake locks are properly released
5. **User Experience**: Clear UI showing service status
6. **Battery Efficiency**: Consider adjusting update intervals based on battery level

## Future Enhancements

1. **Configurable Update Interval**: Allow users to set update frequency
2. **Offline Queue**: Queue data when offline, send when online
3. **Data Compression**: Compress data before sending
4. **Encryption**: Encrypt sensitive data before transmission
5. **Analytics**: Track service uptime and data collection success rate
6. **Remote Configuration**: Fetch configuration from server

## API Integration Example

### Backend Endpoint (Node.js/Express Example)
```javascript
app.post('/api/deviceData', async (req, res) => {
  const deviceInfo = req.body;
  
  // Validate data
  if (!deviceInfo.deviceId) {
    return res.status(400).json({ success: false, message: 'Missing deviceId' });
  }
  
  // Store in database
  await db.deviceData.create(deviceInfo);
  
  res.json({ success: true, message: 'Data received' });
});
```

### Adding Authentication
Update `ApiClient.kt` to include authentication headers:
```kotlin
private val okHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer YOUR_API_TOKEN")
            .addHeader("X-API-Key", "YOUR_API_KEY")
            .build()
        chain.proceed(request)
    }
    // ... other interceptors
    .build()
```

## Conclusion

This implementation provides a robust, persistent background service for Android that:
- Runs continuously in the background
- Auto-restarts after reboots and app kills
- Collects comprehensive device information
- Sends data securely to your backend API
- Handles battery optimization and permissions properly

Remember to:
1. Update the API base URL
2. Implement proper authentication
3. Test thoroughly on different devices
4. Comply with privacy regulations
5. Monitor battery usage and optimize as needed

