# BeaconActivity Documentation

## Overview
BeaconActivity는 BLE (Bluetooth Low Energy) Beacon 및 Master 모드를 관리하는 Android 액티비티입니다. EFR32BG22 BLE 모듈을 AT 명령어로 제어하여 Beacon 브로드캐스트, BLE 디바이스 스캔, 그리고 디바이스 연결/데이터 통신 기능을 제공합니다.

## Architecture

```
BeaconActivity
├── BLE Mode Management (Beacon/Master)
├── Device Scanning & Filtering
├── Device Connection & Communication
└── UI Components
    ├── RecyclerView (Device List)
    ├── Dialog (BLE Connection)
    └── Control Buttons
```

## Core Components

### 1. BeaconActivity.java
**Location**: `app/src/main/java/com/example/apidemo/BeaconActivity.java`

**Main Features**:
- Beacon/Master 모드 전환
- Beacon 파라미터 설정 (Company ID, Major, Minor, Custom UUID)
- BLE 디바이스 스캔 및 필터링
- 디바이스 연결 다이얼로그 관리
- Advertisement/Scan Response 데이터 파싱

**Key Methods**:
```java
// Beacon Configuration
onClick(R.id.btn_beacon_config) → Lib_SetBeaconParams()
onClick(R.id.btn_beacon_query) → Lib_GetBeaconParams()
onClick(R.id.btn_beacon_start) → Lib_EnableBeacon(true)
onClick(R.id.btn_beacon_stop) → Lib_EnableBeacon(false)

// Master Scanning
onClick(R.id.btn_master_scan_config) → Configure scan filters
onClick(R.id.btn_master_scan) → Lib_AtStartNewScan()
onClick(R.id.btn_master_scanStop) → Lib_AtStopScan()

// Data Parsing
parseAdvertisementData(byte[] advertisementData) → JSONObject
parsePayload(String payload) → JSONObject
```

**Scan Data Processing**:
```java
recvScanData Runnable:
1. Lib_ComRecvAT() - Receive scan data
2. Parse "MAC:XX:XX:XX:XX:XX:XX,RSSI:-XX,ADV/RSP:HEXDATA"
3. Convert to JSON array
4. Update DeviceAdapter
5. Auto-remove devices not seen for 3 seconds
```

**Advertisement Data Types Supported**:
- `0x01`: Flags
- `0x02/0x03`: 16-bit Service UUIDs
- `0x04/0x05`: 32-bit Service UUIDs
- `0x06/0x07`: 128-bit Service UUIDs
- `0x08/0x09`: Device Name (Complete/Shortened)
- `0x0A`: TX Power Level
- `0xFF`: Manufacturer Specific Data

### 2. BleConnection.java
**Location**: `app/src/main/java/com/example/apidemo/ble/BleConnection.java`

**Purpose**: AT 명령어 기반 BLE 연결 및 데이터 통신 관리

**Connection Flow**:
```
1. AT+MASTER_PAIR=3 → Set "Just Works" pairing mode (no user intervention)
2. AT+CONNECT=,MAC_ADDRESS → Connect to device
3. Parse connection handle from response
4. AT+UUID_SCAN=1 → Scan UUID channels
5. AT+TRX_CHAN=handle,writeCh,notifyCh,type → Set TX/RX channels
```

**Public API**:
```java
// Connection Management
ConnectionResult connectToDevice(String macAddress)
boolean disconnect()

// Channel Configuration
UuidScanResult scanUuidChannels()
boolean setTrxChannel(int writeCh, int notifyCh, int type)

// Data Communication
SendResult sendData(byte[] data, int timeout)
ReceiveResult receiveData(int timeout)

// Status
boolean isConnected()
Integer getConnectionHandle()
```

**Debug Logging**:
All AT commands are logged with tags:
- `[AT CMD] >>>` : Outgoing AT commands
- `[AT RSP] <<<` : Incoming AT responses
- `[AT DATA]` : Data transmission/reception
- Return codes and data lengths are logged

**Result Classes**:
```java
ConnectionResult { boolean success, Integer handle, String error }
SendResult { boolean success, String error }
ReceiveResult { boolean success, byte[] data, String error, boolean timeout }
UuidScanResult { boolean success, List<UuidChannel> channels, String error }
UuidChannel { int channelNum, String uuid, String properties }
```

### 3. Device.java
**Location**: `app/src/main/java/com/example/apidemo/ble/Device.java`

**Purpose**: BLE 디바이스 정보를 담는 모델 클래스

**Fields**:
```java
String deviceName      // Device name from ADV/RSP
String macAddress      // MAC address (XX:XX:XX:XX:XX:XX)
int rssi               // Signal strength
String serviceUuid     // Service UUID
long Timestamp         // Last seen timestamp
```

### 4. DeviceAdapter.java
**Location**: `app/src/main/java/com/example/apidemo/adapter/DeviceAdapter.java`

**Purpose**: RecyclerView 어댑터로 스캔된 디바이스 목록 관리

**Features**:
- Header + Item 두 가지 뷰 타입 지원
- MAC 주소 기반 디바이스 업데이트
- 3초 이상 스캔되지 않은 디바이스 자동 제거
- 디바이스 클릭 리스너 지원

**Key Methods**:
```java
void updateDevice(Device device)           // Update or add device
void removeDisappearDevice()               // Remove stale devices (3s timeout)
void clearDeviceList()                     // Clear all devices
void setOnDeviceClickListener(listener)    // Set click callback
```

**Auto-Cleanup Logic**:
```java
removeBleTime = 3000ms (3 seconds)
// Devices not updated within 3s are automatically removed from list
```

### 5. DividerItemDecoration.java
**Location**: `app/src/main/java/com/example/apidemo/ble/DividerItemDecoration.java`

**Purpose**: RecyclerView 아이템 사이에 구분선 그리기

**Implementation**: `R.drawable.divider_vertical` drawable을 사용하여 vertical divider 렌더링

## UI Layouts

### activity_beacon.xml
**Location**: `app/src/main/res/layout/activity_beacon.xml`

**Structure**:
```xml
LinearLayout (vertical)
├── FlexboxLayout (Control Buttons)
│   ├── SwitchCompat (Beacon/Master toggle)
│   ├── Button: Query
│   ├── Button: Config
│   ├── Button: Start
│   ├── Button: Stop
│   ├── Button: Scan Config
│   ├── Button: Scan
│   └── Button: Stop Scan
├── Divider
├── TextView (tv_msg) - Message/Log display
└── RecyclerView - Device list
```

**Layout Weights**:
- Control buttons: 150dp + weight=1
- Message TextView: weight=1
- RecyclerView: weight=4

### dialog_ble_connect.xml
**Location**: `app/src/main/res/layout/dialog_ble_connect.xml`

**Structure**:
```xml
LinearLayout (vertical, padding=24dp)
├── TextView: Title ("BLE 연결")
├── Device Info Layout (horizontal)
│   ├── TextView: Device Name
│   └── TextView: MAC Address
├── Connection Status Layout
│   ├── TextView: Status ("연결 안됨"/"연결 중"/"연결됨")
│   └── ProgressBar (hidden by default)
├── EditText: Send Data input
├── Button Row 1 (horizontal)
│   ├── Button: Connect
│   └── Button: Send (disabled initially)
├── ScrollView: Received Log
│   └── TextView: Monospace log display
└── Button Row 2 (horizontal)
    ├── Button: Disconnect (disabled initially)
    └── Button: Close
```

**Dialog Workflow**:
```
1. User clicks device → showBleConnectDialog()
2. User clicks Connect → connectToDevice()
3. Auto scan UUID channels
4. Auto set TRX channel (1, 2, 0)
5. User enters data and clicks Send → sendData() + receiveData()
6. Log displays TX/RX data
7. User clicks Disconnect → disconnect()
8. User clicks Close → dismiss dialog (auto disconnect if connected)
```

## Data Flow

### Beacon Mode Flow
```
1. [UI] Config button clicked
2. [Dialog] User enters: Company ID, Major, Minor, Custom UUID
3. [AT] Lib_SetBeaconParams(beacon)
4. [Storage] Save to SharedPreferences
5. [UI] Start button → Lib_EnableBeacon(true)
6. [Module] Start beacon broadcasting
```

### Master Scan Flow
```
1. [UI] Scan Config button → User sets filters
2. [Storage] Save filters to SharedPreferences
3. [UI] Scan button clicked
4. [AT] Lib_EnableMaster(true)
5. [AT] Lib_AtStartNewScan(filters...)
6. [Thread] Start recvScanData runnable
   ├─ Continuously call Lib_ComRecvAT()
   ├─ Parse "MAC:...,RSSI:...,ADV/RSP:..."
   ├─ Convert to JSON array
   ├─ Send to UI via Handler (SCAN_DATA_PROMPT_MSG)
   └─ Update DeviceAdapter
7. [Adapter] Auto-remove devices not seen for 3s
8. [UI] Stop Scan button → Lib_AtStopScan()
```

### Connection & Communication Flow
```
1. [UI] User clicks device in list
2. [Dialog] Show BLE connect dialog
3. [User] Click Connect button
4. [BleConnection] connectToDevice()
   ├─ AT+MASTER_PAIR=3 (auto-pairing)
   ├─ AT+CONNECT=,MAC
   └─ Parse connection handle
5. [Auto] scanUuidChannels()
   └─ AT+UUID_SCAN=1
6. [Auto] setTrxChannel(1, 2, 0)
   └─ AT+TRX_CHAN=handle,1,2,0
7. [User] Enter data, click Send
8. [BleConnection] sendData()
   ├─ AT+SEND=handle,length,timeout
   ├─ Wait for "INPUT_BLE_DATA" prompt
   └─ Send actual data
9. [BleConnection] receiveData()
   └─ AT+RECV... (or automatic notification)
10. [UI] Display TX/RX in log
11. [User] Click Disconnect
12. [BleConnection] disconnect()
    └─ AT+DISCE=handle
```

## AT Commands Used

### Beacon Commands
```
AT+MAC                           // Get device MAC address
AT+BEACON_PARAM?                 // Query beacon parameters
AT+BEACON_PARAM=company,major,minor,uuid  // Set beacon parameters
AT+BEACON=1/0                    // Enable/Disable beacon
```

### Master Commands
```
AT+MASTER=1/0                    // Enable/Disable master mode
AT+MASTER_PAIR=3                 // Set pairing mode (3=Just Works, no user intervention)
AT+SCAN_START=mac,name,rssi,mfgId,data  // Start scan with filters
AT+SCAN_STOP                     // Stop scanning
```

### Connection Commands
```
AT+CONNECT=,MAC_ADDRESS          // Connect to device
AT+DISCE=handle                  // Disconnect from device
AT+UUID_SCAN=1                   // Scan UUID channels
AT+TRX_CHAN=handle,write,notify,type  // Set TX/RX channels
AT+SEND=handle,length,timeout    // Send data
// (Auto receive via Lib_ComRecvAT)
```

## Message Handlers

### BeaconActivity Handlers
```java
RECORD_PROMPT_MSG (0x06)
- Update tv_msg TextView
- Display general messages and logs

SCAN_DATA_PROMPT_MSG (0x08)
- Parse JSON array of scanned devices
- Update DeviceAdapter with new/updated devices
- Auto-remove stale devices

STOP_SCAN_DATA_PROMPT_MSG (0x10)
- Clear device list
- Display stop scan message
- Remove all pending handler messages
```

## SharedPreferences Storage

### Beacon Info
**Key**: "beaconInfo"
```
- companyId: String (e.g., "4C00")
- majorUuid: String (e.g., "0708")
- minorUuid: String (e.g., "0506")
- customUuid: String (e.g., "0112233445566778899AABBCCDDEEFF0")
```

### Scan Filter Info
**Key**: "scanInfo"
```
- macAddress: String (e.g., "AA:BB:CC:DD:EE:FF")
- broadcastName: String
- rssi: String (stored as string, converted to int with negation)
- manufacturerId: String (hex format)
- data: String
```

## Hardware Module

### EFR32BG22 BLE Module
**Documentation**: `app/docs/EFR32BG22 Master-Slave Module and Protocol V1.7 115200_20230526-복사.pdf`

**Key Specifications**:
- BLE 5.0 compliant
- Supports Master/Slave/Beacon modes
- UART interface (115200 bps default)
- AT command control
- Multi-connection support (up to 8 slaves)
- PHY rates: 1M, 2M, LE Coded
- Long Range and Extended broadcast
- Low power: Sleep mode 3 μA

**Supported Features**:
- AT command set (60+ commands)
- Transparent UART transmission
- Pairing/bonding
- Multiple PHY modes
- Custom UUIDs
- Scan filtering

## Error Handling

### Connection Errors
```java
- "Failed to set pairing mode" → AT+MASTER_PAIR failed
- "No response from device" → Device not responding
- "Failed to parse response" → Unexpected response format
```

### Scan Errors
```java
- Invalid MAC Address → Must match XX:XX:XX:XX:XX:XX format
- Invalid RSSI → Must be in range -120 to 20
- Invalid Manufacturer ID → Must be hex format
```

### Data Parsing Errors
```java
- JSONException → Malformed scan data
- Data format issue → Advertisement data length mismatch
- Missing required fields → MAC or RSSI not present
```

## Threading Model

### Main Thread (UI Thread)
- Activity lifecycle management
- UI updates via Handler
- Button click handling

### Background Threads
```java
// Beacon operations
new Thread(() -> {
    At.Lib_SetBeaconParams(beacon);
    At.Lib_EnableBeacon(true/false);
}).start();

// Scan receiving (long-running)
recvScanData Runnable:
while(startScan) {
    At.Lib_ComRecvAT(recvData, recvDataLen, 20, 1000);
    // Parse and send to UI via Handler
}

// Connection operations
new Thread(() -> {
    bleConnection.connectToDevice(mac);
    bleConnection.sendData(data, timeout);
    bleConnection.receiveData(timeout);
}).start();
```

## State Management

### Activity State
```java
boolean mStartFlag = false;     // Operation in progress flag
boolean mEnableFlag = true;     // Beacon/Master enabled flag
boolean startScan = false;      // Scan active flag
boolean mMasterFlag = false;    // Mode: false=Beacon, true=Master
```

### Connection State
```java
Integer connectionHandle = null;  // Current connection handle (null = disconnected)
boolean testMode = false;         // Test mode flag
```

## Validation

### MAC Address Validation
```java
Pattern: ^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$
Example: "AA:BB:CC:DD:EE:FF"
```

### Manufacturer ID Validation
```java
Pattern: ^[0-9A-Fa-f]+$
Example: "4C00" (Apple)
```

### RSSI Range Validation
```java
Valid range: -120 to 20 dBm
Values outside range are rejected
```

## Lifecycle Handling

### Activity Finish
```java
finish() override:
1. If Beacon/Master is disabled → Show dialog
   - "Yes" → Finish immediately
   - "No" → Re-enable before finishing
2. If scan is active → Stop scan
3. Call super.finish()
```

## Dependencies

### VPOS API Package
```java
import vpos.apipackage.At;      // AT command library
import vpos.apipackage.Beacon;  // Beacon data class
import vpos.apipackage.Com;     // Communication library
```

### Android Libraries
```java
// UI
androidx.recyclerview.widget.RecyclerView
androidx.appcompat.widget.SwitchCompat
com.google.android.flexbox.FlexboxLayout
com.google.android.material.switchmaterial.SwitchMaterial

// Data
org.json.JSONArray
org.json.JSONObject

// Storage
android.content.SharedPreferences
```

## Best Practices

### Thread Safety
- All AT commands executed in background threads
- UI updates via Handler and runOnUiThread()
- Atomic flag checks for state management

### Resource Management
- Stop scan thread before activity destruction
- Disconnect BLE connection before dialog dismissal
- Clean up handler callbacks on scan stop

### User Experience
- Progress indicators during long operations
- Clear error messages with specific failure reasons
- Auto-reconnect not implemented (user must manually retry)
- Auto-cleanup of stale devices (3s timeout)

### Debug Logging
- All AT commands logged with [AT CMD] tag
- All responses logged with [AT RSP] tag
- Data transmission logged with [AT DATA] tag
- Return codes and lengths included in logs

## Known Limitations

1. **Single Connection**: Only one BLE connection at a time
2. **No Auto-Reconnect**: Connection failures require manual retry
3. **Fixed TRX Channels**: Auto-set to (1, 2, 0) after connection
4. **No Bonding Management**: Bonded devices not persisted
5. **Limited Error Recovery**: Most errors require user intervention
6. **Scan Filter Limitations**: Some filters may not work depending on module firmware

## Future Improvements

1. Add support for multiple simultaneous connections
2. Implement auto-reconnect on connection loss
3. Add bonding management and device pairing history
4. Allow user to select TRX channels
5. Add configurable pairing modes (not just mode 3)
6. Implement more robust error recovery
7. Add RSSI-based device sorting
8. Support for custom scan filters per device
9. Add connection timeout configuration
10. Implement data logging to file

---

**Last Updated**: 2025-11-21
**Module Version**: EFR32BG22 V1.7
**Default Baud Rate**: 115200
