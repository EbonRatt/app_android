# Android Background Monitoring Service

A robust Android application that continuously runs in the background, collecting device information and sending it to a backend API. The service is designed to be persistent and auto-restart after reboots or app kills.

## Features

- ✅ **Persistent Foreground Service** - Runs continuously with a notification
- ✅ **Auto-Restart** - Automatically restarts after device reboot or app kill
- ✅ **Device Information Collection**:
  - Device ID
  - Serial Number
  - Network IP Address
  - Storage (Used and Total)
  - Current Location (Latitude & Longitude)
  - Battery Information (Percentage and Charging Status)
- ✅ **Secure API Communication** - HTTPS with retry logic
- ✅ **Battery Optimization Handling** - Requests exclusion from battery optimization
- ✅ **WorkManager Backup** - Ensures data collection continues even if service fails

## Quick Start

### 1. Update API Base URL

Edit `app/src/main/java/com/example/androidshield/api/ApiClient.kt`:

```kotlin
private const val BASE_URL = "https://your-actual-api.com/api/"
```

### 2. Build and Run

1. Sync Gradle files
2. Build the project
3. Install on device
4. Grant all required permissions when prompted
5. Disable battery optimization when requested
6. Service will start automatically

## Required Permissions

The app will request these permissions at runtime:
- **Location** (Fine & Coarse)
- **Notifications** (Android 13+)

The app also requires:
- Battery optimization exclusion (user must grant)
- Internet access (declared in manifest)

## Project Structure

```
app/src/main/java/com/example/androidshield/
├── api/
│   ├── ApiClient.kt          # Retrofit client configuration
│   └── ApiService.kt          # API interface
├── data/
│   ├── DeviceInfo.kt          # Data model
│   └── DeviceInfoCollector.kt # Device information collection
├── receiver/
│   ├── BootReceiver.kt        # Auto-start after reboot
│   └── ServiceRestartReceiver.kt # Restart after package events
├── service/
│   └── DeviceMonitoringService.kt # Main foreground service
├── util/
│   ├── BatteryOptimizationHelper.kt # Battery optimization handling
│   └── WorkManagerHelper.kt   # WorkManager scheduling
├── worker/
│   └── DeviceDataWorker.kt    # Backup worker for data collection
└── MainActivity.kt            # Main activity with permission handling
```

## Backend API Endpoint

Your backend should implement a `POST /deviceData` endpoint that accepts:

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

And returns:

```json
{
  "success": true,
  "message": "Data received"
}
```

## How It Works

1. **Foreground Service**: Runs continuously with a persistent notification
2. **Periodic Collection**: Collects device info every 5 minutes (configurable)
3. **Auto-Restart**: Uses `START_STICKY` flag and receivers to restart automatically
4. **Wake Lock**: Prevents CPU from sleeping during operation
5. **WorkManager Backup**: Scheduled periodic work as a fallback mechanism

## Making Service Persistent

The service is designed to be persistent through:

1. **Foreground Service** - Cannot be easily killed
2. **START_STICKY** - Auto-restarts if killed
3. **Wake Lock** - Prevents CPU sleep
4. **Battery Optimization Exclusion** - Critical for continuous operation
5. **Boot Receiver** - Starts after device reboot
6. **WorkManager** - Backup mechanism

## Important Notes

⚠️ **Battery Impact**: Continuous background operation will drain battery

⚠️ **Privacy**: Location tracking requires user consent and compliance with regulations

⚠️ **Manufacturer Restrictions**: Some manufacturers have aggressive battery management that may affect service

⚠️ **Google Play Policies**: Ensure compliance with Google Play policies

## Troubleshooting

### Service Not Starting
- Check if all permissions are granted
- Verify battery optimization is disabled
- Check Logcat for errors

### Data Not Sending
- Verify API base URL is correct
- Check network connectivity
- Review API endpoint implementation

### Service Being Killed
- Ensure battery optimization is disabled
- Check device manufacturer's battery saver settings
- Some devices require manual whitelisting

## Documentation

See `IMPLEMENTATION_PLAN.md` for detailed implementation documentation.

## License

This project is provided as-is for educational and development purposes.

