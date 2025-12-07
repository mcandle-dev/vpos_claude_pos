# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android demo application for the VPOS 3893 device, showcasing integration with various hardware components including BLE Beacon/Master functionality, PICC (contactless card reader), ICC (chip card reader), MSR (magnetic stripe reader), printer, barcode scanner, and serial communication.

**Key Focus**: The primary feature is BLE Beacon/Master management using the EFR32BG22 BLE module controlled via AT commands.

## Build Commands

### Building the Application
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing configuration in gradle.properties)
./gradlew assembleRelease

# Clean build
./gradlew clean

# Install debug APK to connected device
./gradlew installDebug
```

### Testing
```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Code Quality
```bash
# Check for lint issues
./gradlew lint

# View lint report at: app/build/reports/lint-results.html
```

## Project Structure

### Build Configuration
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 29 (Android 10)
- **Compile SDK**: 34
- **Java Version**: 11
- **Application ID**: com.example.apidemo
- **Project Name**: 3893ApiDemo

### Core Dependencies
- **VPOS Library**: `app/libs/libVpos3893_release_20251202.aar` - Proprietary SDK for hardware control
  - Note: CI workflow downloads version `20250930` from the `vpos_scanner` repo
- **MLKit**: Barcode scanning (v2.1.0)
- **ExFilePicker**: File selection for firmware updates
- **Flexbox Layout**: Flexible button layouts in UI
- **RecyclerView**: Device list display

### Source Structure
```
app/src/main/java/com/example/apidemo/
├── MainActivity.java           - Entry point with feature selection
├── BeaconActivity.java        - BLE Beacon/Master management (PRIMARY FEATURE)
├── ComActivity.java           - Serial communication demo
├── IccActivity.java           - Chip card reader demo
├── MsrActivity.java           - Magnetic stripe reader demo
├── PiccActivity.java          - Contactless card reader demo
├── PrintActivity.java         - Thermal printer demo
├── ScanActivity.java          - Barcode/QR scanner demo
├── SysActivity.java           - System utilities (firmware update, locale)
├── adapter/
│   └── DeviceAdapter.java     - RecyclerView adapter for BLE device list
├── barcode/
│   ├── BarcodeScanActivity.java
│   └── QRCodeScanActivity.java
├── ble/
│   ├── BleConnection.java     - AT command-based BLE connection manager
│   ├── Device.java            - BLE device model
│   └── DividerItemDecoration.java
└── receiver/
    └── BootReceiver.java      - Boot broadcast receiver
```

## BLE Beacon/Master Architecture

### Overview
The BLE functionality is the primary feature of this app. It interfaces with the EFR32BG22 BLE module via AT commands over UART.

### Key Components

#### BeaconActivity (`app/src/main/java/com/example/apidemo/BeaconActivity.java`)
Central activity managing two modes:
1. **Beacon Mode**: Broadcasts iBeacon-compatible advertisements
2. **Master Mode**: Scans for BLE devices, connects, and communicates

**Core Operations**:
- Configure beacon parameters (Company ID, Major/Minor UUIDs, Custom UUID)
- Start/stop beacon broadcasting
- Configure scan filters (MAC address, name, RSSI, manufacturer data)
- Scan for BLE devices with real-time updates
- Parse Advertisement and Scan Response data (AD Types: 0x01-0x09, 0xFF)
- Display scanned devices in RecyclerView with auto-removal (3s timeout)

#### BleConnection (`app/src/main/java/com/example/apidemo/ble/BleConnection.java`)
Handles BLE connection lifecycle and data communication:
- Connection establishment with "Just Works" pairing (AT+MASTER_PAIR=3)
- UUID channel scanning
- TX/RX channel configuration
- Data transmission with timeout handling
- Comprehensive debug logging of all AT commands

**Connection Flow**:
```
1. Set pairing mode → AT+MASTER_PAIR=3
2. Connect → AT+CONNECT=,MAC_ADDRESS
3. Scan channels → AT+UUID_SCAN=1
4. Set TX/RX → AT+TRX_CHAN=handle,writeCh,notifyCh,type
5. Send data → AT+SEND=handle,length,timeout
6. Receive data → AT+RECV or automatic notifications
7. Disconnect → AT+DISCE=handle
```

### AT Command Set
The VPOS library communicates with the EFR32BG22 module using AT commands:

**Beacon Commands**:
- `AT+MAC` - Get device MAC address
- `AT+BEACON_PARAM?` - Query beacon parameters
- `AT+BEACON_PARAM=company,major,minor,uuid` - Set beacon parameters
- `AT+BEACON=1/0` - Enable/disable beacon

**Master Commands**:
- `AT+MASTER=1/0` - Enable/disable master mode
- `AT+MASTER_PAIR=3` - Set "Just Works" pairing (no PIN)
- `AT+SCAN_START=mac,name,rssi,mfgId,data` - Start filtered scan
- `AT+SCAN_STOP` - Stop scanning

**Connection Commands**:
- `AT+CONNECT=,MAC` - Connect to device
- `AT+DISCE=handle` - Disconnect
- `AT+UUID_SCAN=1` - Scan UUID channels
- `AT+TRX_CHAN=handle,write,notify,type` - Configure channels
- `AT+SEND=handle,length,timeout` - Send data

### Threading Model
All AT command operations run on background threads to avoid blocking the UI:
- Beacon operations: Single-shot background threads
- Scan receiving: Continuous loop with 1000ms timeout per read
- Connection/data operations: Background threads with UI updates via Handler
- UI updates: Posted to main thread via Handler messages

### Data Flow Patterns

**Beacon Configuration**:
```
User Input → SharedPreferences → Background Thread → AT+BEACON_PARAM → Module
```

**Master Scanning**:
```
AT+SCAN_START → Continuous Loop (Lib_ComRecvAT) → Parse "MAC:...,RSSI:...,ADV:..."
→ Convert to JSON → Handler Message → UI Update → DeviceAdapter
```

**Device Connection**:
```
User Click → Dialog → Connect Thread → Parse Handle → Auto UUID Scan
→ Auto TRX Setup → Enable Send Button → User Data Exchange → Disconnect
```

## VPOS API Package

The proprietary VPOS library (`vpos.apipackage`) provides hardware abstraction:
- `At` - AT command interface for BLE module
- `Beacon` - Beacon configuration data class
- `Com` - Serial/UART communication
- `Sys` - System utilities (firmware, locale, device info)
- `Icc`, `Msr`, `Picc`, `Printer` - Card reader and printer interfaces

All VPOS APIs are synchronous and should be called from background threads.

## Storage

### SharedPreferences Keys
- **"beaconInfo"**: Stores beacon configuration (companyId, majorUuid, minorUuid, customUuid)
- **"scanInfo"**: Stores scan filter settings (macAddress, broadcastName, rssi, manufacturerId, data)

## Hardware Module

**EFR32BG22 BLE Module**:
- BLE 5.0 compliant
- Supports Master/Slave/Beacon modes
- UART interface at 115200 baud (default)
- Documentation: `app/docs/EFR32BG22 Master-Slave Module and Protocol V1.7 115200_20230526-복사.pdf`
- Long Range and Extended broadcast support
- Multi-connection capable (up to 8 slaves)

## Development Notes

### Launcher Activity
The app is configured to launch directly to `BeaconActivity` (see AndroidManifest.xml line 46-50). `MainActivity` serves as an alternative entry point for accessing all features but is not currently exposed.

### Signing Configuration
Release builds require signing keys defined in `gradle.properties`:
- `KEY_PATH` - Path to keystore (default: platform.jks)
- `KEY_ALIAS` - Key alias (default: systemkey)
- `KEY_PASSWORD` - Key password
- `STORE_PASSWORD` - Keystore password

### Lint Configuration
Lint is configured to not abort on errors (`abortOnError false`) and skip release build checks. Review lint reports after changes.

### ProGuard
ProGuard is enabled for release builds. Rules are in `app/proguard-rules.pro`.

### CI/CD
GitHub Actions workflow (`.github/workflows/android-ci.yml`) automatically:
- Downloads VPOS library from `vpos_scanner` repository
- Builds debug APK on push/PR to master/main branches
- Uploads APK artifact with 7-day retention

**Note**: The CI downloads a different version of the VPOS library (`20250930` vs local `20251202`). Keep this in sync if APIs change.

## Common Patterns

### Background Thread with Handler
```java
new Thread(() -> {
    // Perform AT command or long operation
    int result = At.SomeOperation();

    // Update UI via handler
    Message msg = Message.obtain();
    msg.what = SOME_MSG_CODE;
    msg.obj = "Result: " + result;
    handler.sendMessage(msg);
}).start();
```

### Device List Auto-Cleanup
The `DeviceAdapter` automatically removes devices not seen for 3+ seconds using timestamps. This is called periodically from the scan data handler.

### Activity State Management
Each activity uses boolean flags (`mStartFlag`, `mEnableFlag`, etc.) to track operation state. Always check these before starting operations to prevent concurrent execution.

## Debugging

### AT Command Logging
`BleConnection` logs all AT commands and responses:
- `[AT CMD] >>>` - Outgoing commands
- `[AT RSP] <<<` - Incoming responses
- `[AT DATA]` - Data transmission logs

Search logcat for these tags when debugging BLE issues.

### Common Issues
1. **No scan results**: Check scan filters are valid (RSSI range: -120 to 20)
2. **Connection fails**: Ensure master mode is enabled before connecting
3. **Data not received**: Verify TRX channels match device characteristics
4. **Stale devices**: Normal behavior - devices auto-removed after 3s of no advertisement

## Documentation Reference

For detailed BLE implementation documentation, refer to:
`BeaconActivity_Documentation.md` - Comprehensive guide covering:
- Complete AT command reference
- Advertisement data parsing (AD Types)
- Connection workflow diagrams
- Error handling patterns
- Threading model details
- State management
- Validation rules

This documentation is essential for understanding the BLE implementation and should be consulted when modifying BLE-related code.
