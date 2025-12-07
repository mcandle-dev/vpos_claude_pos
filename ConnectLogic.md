# BLE Connection Logic 상세 문서

## 목차
1. [개요](#개요)
2. [Connect 버튼 로직 (Steps 2-4)](#connect-버튼-로직-steps-2-4)
3. [Send 버튼 로직 (Steps 5-9)](#send-버튼-로직-steps-5-9)
4. [Thread 및 비동기 처리](#thread-및-비동기-처리)
5. [타이밍 다이어그램](#타이밍-다이어그램)
6. [에러 처리](#에러-처리)

---

## 개요

BLE 연결 및 데이터 전송은 두 가지 주요 함수로 구성됩니다:

- **`connectToDevice()`** - BLE 장치 연결 (Steps 2-4)
- **`sendDataComplete()`** - 데이터 전송 (Steps 5-9)

모든 AT Command 작업은 **Background Thread**에서 실행되며, UI 업데이트는 **runOnUiThread()**를 통해 Main Thread에서 처리됩니다.

---

## Connect 버튼 로직 (Steps 2-4)

### 1. UI Layer (BeaconActivity.java:893-930)

```java
btnConnect.setOnClickListener(v -> {
    // 1. UI 상태 업데이트 (Main Thread)
    progressConnection.setVisibility(View.VISIBLE);
    tvConnectionStatus.setText("연결 중...");
    btnConnect.setEnabled(false);

    // 2. Background Thread 시작
    new Thread(() -> {
        // 3. BleConnection.connectToDevice() 호출 (Blocking)
        ConnectionResult result = bleConnection.connectToDevice(device.getMacAddress());

        // 4. UI 업데이트 (Main Thread로 전환)
        runOnUiThread(() -> {
            progressConnection.setVisibility(View.GONE);
            if (result.isSuccess()) {
                tvConnectionStatus.setText("연결됨 (Handle: " + result.getHandle() + ")");
                btnSend.setEnabled(true);
                btnDisconnect.setEnabled(true);
            } else {
                tvConnectionStatus.setText("연결 실패");
                btnConnect.setEnabled(true);
            }
        });
    }).start();
});
```

### 2. Business Logic Layer (BleConnection.java:120-227)

#### Thread 실행 흐름
```
Main Thread (UI)
    ↓
    새로운 Background Thread 생성 (new Thread().start())
    ↓
    BleConnection.connectToDevice() 실행 (Blocking 동기 호출)
    ↓
    runOnUiThread() → Main Thread로 복귀
```

#### Step 2: Set Master Mode (AT+ROLE=1)

**목적**: BLE 모듈을 Master 모드로 설정

```java
// BleConnection.java:128-148
String roleCmd = "AT+ROLE=1\r\n";
int ret = At.Lib_ComSend(roleCmd.getBytes(), roleCmd.length());
// ret == 0: 성공, ret != 0: 실패

byte[] roleResponse = new byte[256];
int[] roleLen = new int[1];
ret = At.Lib_ComRecvAT(roleResponse, roleLen, 3000, 256);
//                                            ↑      ↑
//                                         timeout  max buffer size
String roleResponseStr = new String(roleResponse, 0, roleLen[0]);
// Expected response: "OK\r\n"
```

**AT Command Flow**:
```
App → BLE Module: AT+ROLE=1\r\n
BLE Module → App: OK\r\n
```

**Blocking Point**: `Lib_ComRecvAT()`는 응답을 받거나 3000ms 타임아웃까지 **대기** (Blocking)

---

#### Step 4-1: Set Pairing Mode (AT+MASTER_PAIR=3)

**목적**: "Just Works" 페어링 설정 (PIN 없이 자동 페어링)

```java
// BleConnection.java:153-173
String pairCmd = "AT+MASTER_PAIR=3\r\n";
int ret = At.Lib_ComSend(pairCmd.getBytes(), pairCmd.length());

byte[] pairResponse = new byte[256];
int[] pairLen = new int[1];
ret = At.Lib_ComRecvAT(pairResponse, pairLen, 3000, 256);

String pairResponseStr = new String(pairResponse, 0, pairLen[0]);
// Expected: "OK\r\n"
```

**AT Command Flow**:
```
App → BLE Module: AT+MASTER_PAIR=3\r\n
BLE Module → App: OK\r\n
```

**페어링 타입**:
- `0`: No pairing
- `1`: PIN Code
- `2`: Passkey Entry
- `3`: Just Works (PIN 없음) ← **사용 중**

---

#### Step 4-2: Connect to Device (AT+CONNECT)

**목적**: 스캔된 BLE 장치에 실제 연결

```java
// BleConnection.java:178-220
String connectCmd = "AT+CONNECT=," + macAddress + "\r\n";
//                            ↑ 비어있음 (default 파라미터)
int ret = At.Lib_ComSend(connectCmd.getBytes(), connectCmd.length());

byte[] connectResponse = new byte[512];
int[] connectLen = new int[1];
ret = At.Lib_ComRecvAT(connectResponse, connectLen, 5000, 512);
//                                                   ↑
//                                          연결 시간이 오래 걸리므로 5초 타임아웃

String connectResponseStr = new String(connectResponse, 0, connectLen[0]);
```

**AT Command Flow**:
```
App → BLE Module: AT+CONNECT=,F1:F2:F3:F4:F5:F6\r\n
BLE Module → Slave: BLE Connection Request (무선 통신)
Slave → BLE Module: Connection Accepted
BLE Module → App: +CONNECTED:1,F1:F2:F3:F4:F5:F6\r\nOK\r\n
                   ↑          ↑
                   handle     MAC address
```

**Connection Handle 파싱**:
```java
// BleConnection.java:204-214
Integer handle = parseConnectResponse(connectResponseStr);
// Regex: "CONNECTED\\s+(\\d+)"
// Example: "+CONNECTED:1,..." → handle = 1

if (handle == null) {
    // Fallback: AT+CNT_LIST로 연결된 장치 목록에서 handle 조회
    handle = getConnectionHandleFromDeviceList();
}

connectionHandle = handle; // 인스턴스 변수에 저장 (이후 Send에서 사용)
```

**Fallback 메커니즘** (BleConnection.java:780-837):
```java
// AT+CONNECT 응답에서 handle을 못 찾은 경우
String cmd = "AT+CNT_LIST\r\n";
At.Lib_ComSend(cmd.getBytes(), cmd.length());

byte[] response = new byte[256];
int[] len = new int[1];
At.Lib_ComRecvAT(response, len, 3000, 256);

// Expected response:
// AT+CNT_LIST=
// 1* (F1:F2:F3:F4:F5:F6)  ← '*'는 현재 연결된 장치
// OK

// Regex: "(\\d+)[ ]*\\("
// Example: "1* (F1:F2:..." → handle = 1
```

### 3. 결과 반환

```java
// BleConnection.java:220
return new ConnectionResult(true, handle, null);
//                          ↑     ↑      ↑
//                       success  handle error message
```

**ConnectionResult 구조**:
```java
public static class ConnectionResult {
    private final boolean success;    // 연결 성공 여부
    private final Integer handle;     // Connection Handle (1, 2, 3, ...)
    private final String error;       // 에러 메시지 (실패 시)
}
```

---

## Send 버튼 로직 (Steps 5-9)

### 1. UI Layer (BeaconActivity.java:932-983)

```java
btnSend.setOnClickListener(v -> {
    String sendData = etSendData.getText().toString().trim();
    btnSend.setEnabled(false);
    progressConnection.setVisibility(View.VISIBLE);

    new Thread(() -> {
        // Execute Steps 5-9
        SendResult result = bleConnection.sendDataComplete(sendData, 3000);

        runOnUiThread(() -> {
            btnSend.setEnabled(true);
            progressConnection.setVisibility(View.GONE);
            // Log 업데이트
        });

        // Optional: 응답 수신 시도
        if (result.isSuccess()) {
            ReceiveResult recvResult = bleConnection.receiveData(2000);
            // RX 데이터 로그 업데이트
        }
    }).start();
});
```

### 2. Business Logic Layer (BleConnection.java:305-558)

#### Thread 실행 흐름
```
Main Thread (UI)
    ↓
    새로운 Background Thread 생성
    ↓
    BleConnection.sendDataComplete() 실행 (Blocking)
    ↓ (내부적으로 Thread.sleep() 사용)
    Step 5-1: UUID_SCAN 명령 전송
    Step 5-2: Thread.sleep(3000) → GATT Discovery 대기 (Blocking)
    Step 5-3: Characteristic 데이터 수신
    Step 6: CNT_LIST
    Step 7: TRX_CHAN
    Step 8: TTM_HANDLE
    Step 9: SEND
    ↓
    runOnUiThread() → Main Thread로 복귀
```

---

#### Step 5: Enable UUID Scan (AT+UUID_SCAN=1)

**목적**: 연결된 장치의 GATT Services 및 Characteristics 스캔

**중요**: UUID_SCAN은 **2개의 응답**을 반환합니다!

##### Step 5-1: 명령 전송 및 OK 수신

```java
// BleConnection.java:318-352
String uuidScanCmd = "AT+UUID_SCAN=1\r\n";
int ret = At.Lib_ComSend(uuidScanCmd.getBytes(), uuidScanCmd.length());

// 첫 번째 응답: 즉시 OK 수신
byte[] okResponse = new byte[128];
int[] okLen = new int[1];
ret = At.Lib_ComRecvAT(okResponse, okLen, 2000, 128);

String okStr = new String(okResponse, 0, okLen[0]);
// Expected: "OK\r\n"
```

**AT Command Flow (1차)**:
```
App → BLE Module: AT+UUID_SCAN=1\r\n
BLE Module → App: OK\r\n (즉시 응답)
```

**Blocking Point**: `Lib_ComRecvAT()` 2000ms까지 대기

---

##### Step 5-2: GATT Service Discovery 대기

```java
// BleConnection.java:355-356
Thread.sleep(3000); // 3초 대기 (Blocking!)
//            ↑
//    GATT Service Discovery 완료 대기
```

**비동기 처리 과정**:
```
App (Thread.sleep)
    ↓
    [대기 중...]
    ↓
BLE Module (백그라운드에서 GATT Discovery 실행)
    ↓
    Slave Device에게 Service/Characteristic 요청
    ↓
    Slave → Module: GATT Table 전송
    ↓
    Module: 내부 파싱 완료
    ↓
App (Thread.sleep 종료)
    ↓
    UUID Scan 데이터 요청
```

**왜 3초 대기?**
- GATT Service Discovery는 **비동기 작업**
- BLE 모듈이 Slave에게 Service/Characteristic 정보를 요청하는 데 시간 소요
- 복잡한 GATT Profile (많은 Services/Characteristics)일 경우 2초 이상 걸릴 수 있음

---

##### Step 5-3: Characteristic 데이터 수신

```java
// BleConnection.java:358-381
byte[] uuidResponse = new byte[4096]; // 큰 버퍼 (많은 Characteristics 대응)
int[] uuidLen = new int[1];
ret = At.Lib_ComRecvAT(uuidResponse, uuidLen, 8000, 4096);
//                                            ↑      ↑
//                                       8초 timeout  4KB buffer

String uuidResponseStr = new String(uuidResponse, 0, uuidLen[0]);
```

**AT Command Flow (2차)**:
```
App → BLE Module: (데이터 읽기 요청)
BLE Module → App:
-CHAR:0 UUID:002A,Read;
-CHAR:1 UUID:052A,Indicate;
-CHAR:2 UUID:E4FF,Notify;
-CHAR:3 UUID:E9FF,Write Without Response,Write;
-CHAR:4 UUID:F3FF,Read,Notify;
-CHAR:5 UUID:91FF,Read,Write Without Response,Write;
```

**Characteristic 파싱** (BleConnection.java:840-859):
```java
List<UuidChannel> channels = parseUuidScanResponse(uuidResponseStr);

// Regex: "-CHAR:(\\d+)\\s+UUID:([^,]+),([^;]+);"
// Example: "-CHAR:3 UUID:E9FF,Write Without Response,Write;"
//           ↑     ↑       ↑    ↑
//         prefix channel uuid  properties

public static class UuidChannel {
    public final int channelNum;      // 3
    public final String uuid;         // "E9FF"
    public final String properties;   // "Write Without Response,Write"
}
```

**Blocking Point**: `Lib_ComRecvAT()` 8000ms까지 대기

---

#### Step 6: Check Connection Handle (AT+CNT_LIST)

**목적**: 현재 연결 상태 확인 (멀티 커넥션 지원)

```java
// BleConnection.java:386-406
String cntListCmd = "AT+CNT_LIST\r\n";
int ret = At.Lib_ComSend(cntListCmd.getBytes(), cntListCmd.length());

byte[] cntListResponse = new byte[512];
int[] cntListLen = new int[1];
ret = At.Lib_ComRecvAT(cntListResponse, cntListLen, 3000, 512);

String cntListResponseStr = new String(cntListResponse, 0, cntListLen[0]);
// Expected: "AT+CNT_LIST=\r\n1* (F1:F2:F3:F4:F5:F6)\r\nOK\r\n"

// connectionHandle이 응답에 포함되어 있는지 확인
if (!cntListResponseStr.contains(String.valueOf(connectionHandle))) {
    return new SendResult(false, "Device not connected");
}
```

**AT Command Flow**:
```
App → BLE Module: AT+CNT_LIST\r\n
BLE Module → App:
AT+CNT_LIST=
1* (F1:F2:F3:F4:F5:F6)  ← '*'는 현재 활성 연결
OK
```

---

#### Step 7: Set TRX Channel (AT+TRX_CHAN)

**목적**: Write/Notify Characteristic 설정

##### Write Channel 찾기

```java
// BleConnection.java:413-421
UuidChannel writeChannel = null;
for (UuidChannel channel : channels) {
    if (channel.properties.contains("Write")) {
        writeChannel = channel;
        break;
    }
}
// Example: CH3 UUID:E9FF, properties="Write Without Response,Write"
```

##### Notify Channel 찾기

```java
// BleConnection.java:423-431
UuidChannel notifyChannel = null;
for (UuidChannel channel : channels) {
    if (channel.properties.contains("Notify") ||
        channel.properties.contains("Indicate")) {
        notifyChannel = channel;
        break;
    }
}
// Example: CH2 UUID:E4FF, properties="Notify"
```

##### Write Type 결정

```java
// BleConnection.java:440
int writeType = writeChannel.properties.contains("Write Without Response") ? 0 : 1;
//              ↑
//  0 = Write Without Response (빠름, 응답 없음)
//  1 = Write With Response (느림, 응답 대기)
```

##### TRX Channel 설정

```java
// BleConnection.java:443-464
String trxCmd = String.format("AT+TRX_CHAN=%d,%d,%d,%d\r\n",
    connectionHandle,           // 1
    writeChannel.channelNum,    // 3
    notifyChannel.channelNum,   // 2
    writeType);                 // 0

// Example: "AT+TRX_CHAN=1,3,2,0\r\n"

int ret = At.Lib_ComSend(trxCmd.getBytes(), trxCmd.length());

byte[] trxResponse = new byte[256];
int[] trxLen = new int[1];
ret = At.Lib_ComRecvAT(trxResponse, trxLen, 3000, 256);

String trxResponseStr = new String(trxResponse, 0, trxLen[0]);
// Expected: "OK\r\n"
```

**AT Command Flow**:
```
App → BLE Module: AT+TRX_CHAN=1,3,2,0\r\n
                               ↑ ↑ ↑ ↑
                          handle│ │ write type
                           write│ notify channel
                            channel
BLE Module → App: OK\r\n
```

---

#### Step 8: Set Transparent Transmission Handle (AT+TTM_HANDLE)

**목적**: 투명 전송 모드 활성화 (AT 명령어 없이 직접 데이터 전송 가능)

```java
// BleConnection.java:469-489
String ttmCmd = "AT+TTM_HANDLE=" + connectionHandle + "\r\n";
// Example: "AT+TTM_HANDLE=1\r\n"

int ret = At.Lib_ComSend(ttmCmd.getBytes(), ttmCmd.length());

byte[] ttmResponse = new byte[256];
int[] ttmLen = new int[1];
ret = At.Lib_ComRecvAT(ttmResponse, ttmLen, 3000, 256);

String ttmResponseStr = new String(ttmResponse, 0, ttmLen[0]);
// Expected: "OK\r\n"
```

**AT Command Flow**:
```
App → BLE Module: AT+TTM_HANDLE=1\r\n
BLE Module → App: OK\r\n
```

**투명 전송 모드란?**
- 설정 후에는 AT 명령어 없이 **raw data**를 직접 전송 가능
- 하지만 이 코드에서는 여전히 `AT+SEND` 명령어 사용 (명시적 전송)

---

#### Step 9: Send Data (AT+SEND)

**가장 복잡한 단계**: 4단계로 나뉨

##### Step 9-1: AT+SEND 명령 전송

```java
// BleConnection.java:494-508
byte[] dataBytes = data.getBytes();
int dataLength = dataBytes.length;

String sendCmd = String.format("AT+SEND=%d,%d,%d\r\n",
    connectionHandle,   // 1
    dataLength,         // 3 (예: "fff" → 3 bytes)
    timeout);           // 3000 (ms)

// Example: "AT+SEND=1,3,3000\r\n"

int ret = At.Lib_ComSend(sendCmd.getBytes(), sendCmd.length());
```

**AT Command Flow (1단계)**:
```
App → BLE Module: AT+SEND=1,3,3000\r\n
                         ↑ ↑ ↑
                    handle│ timeout
                      data length
```

---

##### Step 9-2: "INPUT_BLE_DATA:" 프롬프트 대기

```java
// BleConnection.java:510-520
byte[] sendResponse = new byte[256];
int[] sendLen = new int[1];
ret = At.Lib_ComRecvAT(sendResponse, sendLen, 1000, 256);

String sendResponseStr = new String(sendResponse, 0, sendLen[0]);
// Expected: "INPUT_BLE_DATA:3\r\n"

if (!sendResponseStr.contains("INPUT_BLE_DATA:" + dataLength)) {
    return new SendResult(false, "Unexpected response: " + sendResponseStr);
}
```

**AT Command Flow (2단계)**:
```
BLE Module → App: INPUT_BLE_DATA:3\r\n
                  ↑                ↑
              프롬프트         데이터 길이 확인
```

**Blocking Point**: `Lib_ComRecvAT()` 1000ms까지 대기

---

##### Step 9-3: 실제 데이터 전송 (NO CRLF!)

```java
// BleConnection.java:522-531
ret = At.Lib_ComSend(dataBytes, dataLength);
//                   ↑          ↑
//              raw bytes    정확한 길이만 전송 (CRLF 없음!)

// Example: "fff" (3 bytes)
// 주의: "fff\r\n" (5 bytes) 아님!
```

**AT Command Flow (3단계)**:
```
App → BLE Module: fff (3 bytes, NO CRLF!)
BLE Module → Slave: BLE Write Characteristic (무선 전송)
```

**중요**:
- 데이터 전송 시 **\r\n을 추가하지 않음**
- AT 명령어는 `\r\n` 필요, 실제 데이터는 불필요
- `dataLength`만큼만 전송

**300ms 대기**:
```java
// BleConnection.java:534
Thread.sleep(300); // 데이터 전송 완료 대기 (Blocking)
```

---

##### Step 9-4: 전송 확인 수신

```java
// BleConnection.java:535-548
byte[] confirmResponse = new byte[256];
int[] confirmLen = new int[1];
ret = At.Lib_ComRecvAT(confirmResponse, confirmLen, 5000, 256);
//                                                   ↑
//                           Slave 응답 대기 (timeout 파라미터 사용)

String confirmResponseStr = new String(confirmResponse, 0, confirmLen[0]);
// Expected: "OK\r\n" 또는 "SEND_OK\r\n"

if (confirmResponseStr.contains("OK") || confirmResponseStr.contains("SEND_OK")) {
    return new SendResult(true, null);
} else {
    return new SendResult(false, "Send failed: " + confirmResponseStr);
}
```

**AT Command Flow (4단계)**:
```
BLE Module → App: OK\r\n (또는 SEND_OK\r\n)
              ↑
        전송 성공 확인
```

**Blocking Point**: `Lib_ComRecvAT()` 5000ms까지 대기 (timeout 파라미터 값)

---

## Thread 및 비동기 처리

### 1. Thread 생성 및 관리

#### Connect 버튼 Thread

```java
// BeaconActivity.java:899
new Thread(() -> {
    // 이 람다 함수는 새로운 Background Thread에서 실행
    ConnectionResult result = bleConnection.connectToDevice(device.getMacAddress());
    // ↑ 동기 Blocking 호출 (Thread가 여기서 대기)

    runOnUiThread(() -> {
        // UI 업데이트는 Main Thread로 전환
        tvConnectionStatus.setText("연결됨");
    });
}).start();
// .start() 호출 즉시 새 Thread 생성 및 실행
// Main Thread는 즉시 다음 코드 실행 (비동기)
```

**Thread Lifecycle**:
```
Main Thread                    Background Thread
    │                                 │
    ├─ new Thread().start() ─────────┤
    │  (즉시 반환)                    │
    │                                 ├─ connectToDevice() 시작
    │                                 │  (Blocking 대기...)
    │                                 │  - AT+ROLE=1 (3초 대기)
    │                                 │  - AT+MASTER_PAIR=3 (3초 대기)
    │                                 │  - AT+CONNECT (5초 대기)
    │                                 ├─ connectToDevice() 완료
    │                                 │
    ├─ runOnUiThread() ◄─────────────┤
    ├─ tvConnectionStatus.setText()  │
    │                                 │
    │                                 └─ Thread 종료
    ↓
```

#### Send 버튼 Thread

```java
// BeaconActivity.java:944
new Thread(() -> {
    SendResult result = bleConnection.sendDataComplete(sendData, 3000);
    // ↑ 동기 Blocking 호출
    //   - Thread.sleep(3000) 포함 (GATT Discovery 대기)
    //   - 여러 AT 명령어 순차 실행 (각각 Blocking)

    runOnUiThread(() -> {
        // UI 업데이트
    });

    // Optional: 추가 수신 작업 (같은 Background Thread에서)
    if (result.isSuccess()) {
        ReceiveResult recvResult = bleConnection.receiveData(2000);
        // ↑ 추가 Blocking (2초 대기)

        runOnUiThread(() -> {
            // RX 데이터 UI 업데이트
        });
    }
}).start();
```

**Thread Lifecycle**:
```
Main Thread                    Background Thread
    │                                 │
    ├─ new Thread().start() ─────────┤
    │  (즉시 반환)                    │
    │                                 ├─ sendDataComplete() 시작
    │                                 │
    │                                 ├─ AT+UUID_SCAN=1 (2초 대기)
    │                                 ├─ Thread.sleep(3000) ← Blocking!
    │                                 ├─ UUID data 수신 (8초 대기)
    │                                 ├─ AT+CNT_LIST (3초 대기)
    │                                 ├─ AT+TRX_CHAN (3초 대기)
    │                                 ├─ AT+TTM_HANDLE (3초 대기)
    │                                 ├─ AT+SEND (1초 대기)
    │                                 ├─ Data 전송
    │                                 ├─ Thread.sleep(300) ← Blocking!
    │                                 ├─ 확인 수신 (5초 대기)
    │                                 │
    ├─ runOnUiThread() ◄─────────────┤
    ├─ btnSend.setEnabled(true)      │
    │                                 │
    │                                 ├─ receiveData() 시작
    │                                 ├─ 수신 대기 (2초)
    │                                 │
    ├─ runOnUiThread() ◄─────────────┤
    ├─ tvReceivedLog.setText()       │
    │                                 │
    │                                 └─ Thread 종료
    ↓
```

### 2. Blocking vs Non-Blocking

#### Blocking 함수들

```java
// 1. Lib_ComRecvAT - UART에서 응답 수신까지 대기
ret = At.Lib_ComRecvAT(response, len, timeout, maxLen);
// ↑ timeout(ms)까지 대기 (응답 받으면 즉시 반환)

// 2. Thread.sleep - 지정된 시간만큼 무조건 대기
Thread.sleep(3000);
// ↑ 3000ms 동안 Thread 정지 (다른 작업 불가)
```

**Blocking 동작 원리**:
```
Thread State: RUNNING
    ↓
At.Lib_ComRecvAT() 호출
    ↓
Thread State: WAITING (UART 응답 대기)
    ↓ (시간 경과...)
    ↓
UART에서 데이터 도착 또는 timeout
    ↓
Thread State: RUNNING (재개)
```

#### Non-Blocking 함수들

```java
// 1. Lib_ComSend - 데이터 전송 후 즉시 반환
ret = At.Lib_ComSend(cmd.getBytes(), cmd.length());
// ↑ 데이터를 UART 버퍼에 쓰고 즉시 반환 (0 = 성공)

// 2. new Thread().start() - Thread 생성 후 즉시 반환
new Thread(() -> { ... }).start();
// ↑ Main Thread는 여기서 대기하지 않음
```

### 3. UI Thread 전환 (runOnUiThread)

```java
// Background Thread에서 실행 중...
runOnUiThread(() -> {
    // 이 블록은 Main Thread에서 실행됨
    tvConnectionStatus.setText("연결됨");
    btnSend.setEnabled(true);
});
// Background Thread는 여기서 계속 실행
```

**Thread 전환 원리**:
```
Background Thread
    ↓
runOnUiThread() 호출
    ↓
Main Thread의 Message Queue에 Runnable 추가
    ↓
                                Main Thread
                                    ↓
                            Message Loop에서 Runnable 실행
                                    ↓
                            UI 업데이트 (setText 등)
                                    ↓
                            계속 Event 처리
```

**왜 필요한가?**
- Android에서 **UI 업데이트는 반드시 Main Thread**에서 실행
- Background Thread에서 `setText()` 호출 시 → `CalledFromWrongThreadException` 발생

### 4. Thread Safety

#### 안전한 패턴

```java
// BleConnection.java:19
private Integer connectionHandle = null;
// ↑ 인스턴스 변수이지만 Thread-safe 사용

// Connect Thread에서 쓰기
connectionHandle = handle;

// Send Thread에서 읽기
if (connectionHandle == null) { ... }
```

**왜 안전한가?**
- Connect와 Send는 **순차적**으로 실행 (UI에서 버튼 비활성화)
- Connect 완료 전까지 Send 버튼 비활성화
- 동시 접근 불가능

#### 위험한 패턴 (이 코드에는 없음)

```java
// 만약 이렇게 작성했다면 위험
new Thread(() -> {
    connectionHandle = 1;  // Thread A 쓰기
}).start();

new Thread(() -> {
    int handle = connectionHandle;  // Thread B 읽기
    // ↑ Race Condition 가능!
}).start();
```

---

## 타이밍 다이어그램

### Connect 프로세스 전체 타이밍

```
Time (ms)    App Thread                BLE Module                Slave Device
    0        ┌─────────────┐
             │btn Connect  │
             │clicked      │
             └──────┬──────┘
                    │
                    ├─ new Thread().start()
                    │
  100        ┌──────▼──────┐
             │AT+ROLE=1    ├──────────────►
             └─────────────┘
 3100               ◄───────────────────────┤ OK
             ┌─────────────┐
             │AT+MASTER_   │
             │PAIR=3       ├──────────────►
             └─────────────┘
 6100               ◄───────────────────────┤ OK
             ┌─────────────┐
             │AT+CONNECT=  │
             │,MAC         ├──────────────►┌────────────┐
             └─────────────┘               │Connection  │
                                           │Request     ├───►
10000                                      └────────────┘
                                                  ◄────────────┤ Accept
11100              ◄───────────────────────┤ +CONNECTED:1
             ┌─────────────┐
             │Parse handle │
             │= 1          │
             └──────┬──────┘
                    │
                    ├─ runOnUiThread()
                    │
11200        ┌──────▼──────┐
             │UI Update    │
             │"연결됨"     │
             │btnSend      │
             │.setEnabled  │
             └─────────────┘

Total: ~11.2초
```

### Send 프로세스 전체 타이밍

```
Time (ms)    App Thread                BLE Module                Slave Device
    0        ┌─────────────┐
             │btn Send     │
             │clicked      │
             │data="fff"   │
             └──────┬──────┘
                    │
                    ├─ new Thread().start()
                    │
  100        ┌──────▼──────┐
             │AT+UUID_SCAN │
             │=1           ├──────────────►
             └─────────────┘
 2100               ◄───────────────────────┤ OK
             ┌─────────────┐
             │Thread.sleep │
             │(3000)       │ ← App 대기
             └─────────────┘
 5100        ┌─────────────┐               ┌────────────┐
             │Lib_ComRecv  │               │GATT        │
             │AT()         │               │Discovery   ├───►
             └─────────────┘               │(백그라운드)│
13100              ◄───────────────────────┤ -CHAR:0... ◄────┤ Services
                                           └────────────┘
             ┌─────────────┐
             │Parse UUID   │
             │channels     │
             └─────────────┘
13200        ┌─────────────┐
             │AT+CNT_LIST  ├──────────────►
             └─────────────┘
16200              ◄───────────────────────┤ 1* (MAC) OK
             ┌─────────────┐
             │AT+TRX_CHAN  │
             │=1,3,2,0     ├──────────────►
             └─────────────┘
19200              ◄───────────────────────┤ OK
             ┌─────────────┐
             │AT+TTM_      │
             │HANDLE=1     ├──────────────►
             └─────────────┘
22200              ◄───────────────────────┤ OK
             ┌─────────────┐
             │AT+SEND=     │
             │1,3,3000     ├──────────────►
             └─────────────┘
23200              ◄───────────────────────┤ INPUT_BLE_DATA:3
             ┌─────────────┐
             │Send "fff"   ├──────────────►┌────────────┐
             │(3 bytes)    │               │BLE Write   ├───►
             └─────────────┘               │Char        │
23500        ┌─────────────┐               └────────────┘
             │Thread.sleep │
             │(300)        │
             └─────────────┘
23800        ┌─────────────┐
             │Lib_ComRecv  │
             │AT(5000)     │
             └─────────────┘
28800              ◄───────────────────────┤ OK (or SEND_OK)
             ┌─────────────┐
             │runOnUiThread│
             │UI Update    │
             └──────┬──────┘
                    │
29000        ┌──────▼──────┐
             │"Data Send   │
             │Successful"  │
             └─────────────┘
             ┌─────────────┐
             │receiveData  │
             │(2000)       │← Optional
             └─────────────┘
31000              ◄───────────────────────┤ (RX data or timeout)

Total: ~31초 (수신 포함)
       ~29초 (전송만)
```

### Blocking Time 분석

| 단계 | 함수 | Timeout | 평균 실제 시간 | 비고 |
|------|------|---------|---------------|------|
| **Connect** |
| Step 2 | Lib_ComRecvAT | 3000ms | ~100ms | Master mode 설정 |
| Step 4-1 | Lib_ComRecvAT | 3000ms | ~100ms | Pairing 설정 |
| Step 4-2 | Lib_ComRecvAT | 5000ms | ~1000ms | BLE 연결 (무선) |
| **소계** | | **11000ms** | **~1200ms** | |
| **Send** |
| Step 5-1 | Lib_ComRecvAT | 2000ms | ~100ms | OK 수신 |
| Step 5-2 | Thread.sleep | 3000ms | 3000ms | **무조건 대기** |
| Step 5-3 | Lib_ComRecvAT | 8000ms | ~2000ms | GATT data |
| Step 6 | Lib_ComRecvAT | 3000ms | ~100ms | CNT_LIST |
| Step 7 | Lib_ComRecvAT | 3000ms | ~100ms | TRX_CHAN |
| Step 8 | Lib_ComRecvAT | 3000ms | ~100ms | TTM_HANDLE |
| Step 9-2 | Lib_ComRecvAT | 1000ms | ~100ms | INPUT prompt |
| Step 9-3 | Thread.sleep | 300ms | 300ms | **무조건 대기** |
| Step 9-4 | Lib_ComRecvAT | 5000ms | ~500ms | 전송 확인 |
| **소계** | | **28300ms** | **~6300ms** | |
| **합계** | | **39300ms** | **~7500ms** | |

**최적화 포인트**:
- `Thread.sleep(3000)`: 3초 고정 대기 → 실제 GATT Discovery 완료 시 즉시 진행하도록 개선 가능
- `Thread.sleep(300)`: 300ms 고정 대기 → 불필요할 수 있음

---

## 에러 처리

### 1. Connection 에러

#### AT Command 전송 실패

```java
// BleConnection.java:134-136
int ret = At.Lib_ComSend(roleCmd.getBytes(), roleCmd.length());
if (ret != 0) {
    return new ConnectionResult(false, null, "Failed to set Master mode: " + ret);
}
```

**에러 코드**:
- `0`: 성공
- `!= 0`: UART 전송 실패 (하드웨어 오류, 버퍼 Full 등)

#### 응답 수신 실패

```java
// BleConnection.java:195-197
ret = At.Lib_ComRecvAT(connectResponse, connectLen, 5000, 512);
if (ret != 0 || connectLen[0] == 0) {
    return new ConnectionResult(false, null, "No connection response from device");
}
```

**에러 원인**:
- `ret != 0`: UART 수신 오류
- `connectLen[0] == 0`: Timeout (5초 내 응답 없음)

#### 응답 내용 검증 실패

```java
// BleConnection.java:145-147
if (!roleResponseStr.contains("OK")) {
    return new ConnectionResult(false, null, "Master mode response: " + roleResponseStr);
}
```

**에러 응답 예시**:
- `ERROR\r\n`: 명령어 거부
- `BUSY\r\n`: 모듈이 다른 작업 중
- `INVALID PARAM\r\n`: 파라미터 오류

### 2. Send 에러

#### UUID Scan 실패

```java
// BleConnection.java:377-380
if (channels.isEmpty()) {
    Log.e(TAG, "No characteristics found in response");
    Log.w(TAG, "Raw response: " + uuidResponseStr);
    return new SendResult(false, "No GATT characteristics found");
}
```

**에러 원인**:
- GATT Discovery 타임아웃
- Slave가 Service를 제공하지 않음
- `Thread.sleep(3000)` 부족 (복잡한 Profile)

**해결**:
```java
Thread.sleep(5000); // 3000 → 5000으로 증가
```

#### Write/Notify Channel 없음

```java
// BleConnection.java:433-436
if (writeChannel == null || notifyChannel == null) {
    Log.e(TAG, "Required channels not found (Write:" + (writeChannel != null) +
          ", Notify:" + (notifyChannel != null) + ")");
    return new SendResult(false, "Required GATT channels not found");
}
```

**에러 원인**:
- Slave가 Write Characteristic을 제공하지 않음
- Notify/Indicate Characteristic 없음

#### DATA Prompt 실패

```java
// BleConnection.java:517-520
if (!sendResponseStr.contains("INPUT_BLE_DATA:" + dataLength)) {
    Log.e(TAG, "Module not ready for data input");
    return new SendResult(false, "Unexpected response: " + sendResponseStr);
}
```

**에러 응답 예시**:
- `ERROR\r\n`: TRX 채널 미설정
- `NOT CONNECTED\r\n`: 연결 끊김
- `TIMEOUT\r\n`: Slave 응답 없음

### 3. Exception 처리

```java
// BleConnection.java:222-226
} catch (Exception e) {
    Log.e(TAG, "Connection error: " + e.getMessage());
    e.printStackTrace();
    return new ConnectionResult(false, null, "Connection error: " + e.getMessage());
}
```

**Exception 종류**:
- `NumberFormatException`: Handle 파싱 실패
- `InterruptedException`: Thread.sleep() 중단
- `NullPointerException`: 예상치 못한 null 값

### 4. UI Layer 에러 처리

```java
// BeaconActivity.java:919-927
if (result.isSuccess()) {
    tvConnectionStatus.setText("연결됨");
    btnSend.setEnabled(true);
} else {
    tvConnectionStatus.setText("연결 실패");
    btnConnect.setEnabled(true); // 재시도 가능
    logBuilder.append("Error: ").append(result.getError()).append("\n");
    tvReceivedLog.setText(logBuilder.toString());
}
```

---

## 요약

### Connect 버튼 (Steps 2-4)
1. **UI Thread**: 버튼 클릭 → Background Thread 생성
2. **Background Thread**:
   - Step 2: AT+ROLE=1 (Master 모드)
   - Step 4-1: AT+MASTER_PAIR=3 (Just Works 페어링)
   - Step 4-2: AT+CONNECT (실제 연결)
   - Handle 파싱 및 저장
3. **UI Thread**: 연결 결과 표시, Send 버튼 활성화

**총 소요 시간**: ~1.2초 (실제), 최대 11초 (timeout)

### Send 버튼 (Steps 5-9)
1. **UI Thread**: 버튼 클릭 → Background Thread 생성
2. **Background Thread**:
   - Step 5: UUID_SCAN (OK → 3초 대기 → Characteristics 수신)
   - Step 6: CNT_LIST (연결 확인)
   - Step 7: TRX_CHAN (Write/Notify 채널 설정)
   - Step 8: TTM_HANDLE (투명 전송 모드)
   - Step 9: SEND (명령 → Prompt → Data → 300ms 대기 → 확인)
3. **UI Thread**: 전송 결과 표시
4. **Background Thread**: receiveData() (Optional)
5. **UI Thread**: 수신 결과 표시

**총 소요 시간**: ~6.3초 (실제), 최대 28.3초 (timeout)

### 핵심 비동기 처리
- **Thread.sleep()**: 고정 시간 Blocking (GATT Discovery, 데이터 전송 안정화)
- **Lib_ComRecvAT()**: Timeout까지 응답 대기 (응답 수신 즉시 반환)
- **runOnUiThread()**: Background → Main Thread 전환 (UI 업데이트)

### 주요 Blocking Points
1. Connect: 3개 AT 명령어 (각각 Lib_ComRecvAT)
2. Send: 7개 AT 명령어 + 2개 Thread.sleep (총 9개 Blocking)

---

**문서 끝**
