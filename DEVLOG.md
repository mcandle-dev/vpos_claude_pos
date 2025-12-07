# 개발 로그 (Development Log)

## 2025-12-06: UUID_SCAN 응답 수신 이슈

### 🐛 현재 이슈

**증상**: `AT+UUID_SCAN=1` 명령 후 채널 정보가 완전히 수신되지 않음

**로그 예시**:
```
[AT CMD] >>> AT+UUID_SCAN=1
[AT RSP] <<< OK\r\n72:54:CF:62:3B:90 CONNECTED 1\r\n-CHAR:0 UUID:052A,Indicate;\r\n-CHAR:1 UUID:3A2B,Read;\r\n-CHAR:2 UUID:292B,Read,Write;\r\n-CHAR:3 UUID:2A2B,Read;\r\n-CHAR:4 UUID:002A,Read;\r\n-CHAR:5 UUID:012A,Read;\r\n-CHAR:6 UUID:A62A,Read;\r\n-CHAR:7 UUID:932B,Read,Notify;\r\n-CHAR:8 UUID:962B,Notify;\r\n-CHAR:9 UUID:972B,Read,Notify;\r\n-CHAR:10 UUID:982B,Read,Notify;\r\n-CHAR:11 UUID:992B,Read,Write Without Response,Write,Notify;\r\n-CHAR:12 UUID:9A2B,Read,Write Without Response,Write,Notify;\r\n-CHAR:13 UUID:9B2B,Read,Notify;\r\n-CHAR:14 UU
```

**문제점**:
1. ✅ `OK\r\n` 응답이 옴 (정상)
2. ✅ `-CHAR:...` 데이터가 시작됨 (정상)
3. ❌ `-CHAR:14` 항목이 중간에 잘림 (`UUID:` 이후 데이터 누락)
4. ❌ 이후 채널 정보가 더 있을 수 있지만 수신되지 않음

### 📋 예상 원인

#### 원인 1: 응답이 한 번에 도착하는 경우
BLE_GATT_Connection_Guide.md에서는 UUID_SCAN이 2개의 응답을 보낸다고 했지만:
- 이론: `OK\r\n` → (대기) → `-CHAR:...` (별도 응답)
- 실제: `OK\r\n...-CHAR:...\r\n-CHAR:...` (한 번에 수신)

현재 코드 (BleConnection.java:335-381):
```java
// Step 5-1: OK만 읽기 (128 bytes 버퍼)
ret = At.Lib_ComRecvAT(okResponse, okLen, 2000, 128);
// → "OK\r\n72:54:CF:62:3B:90 CONNECTED 1\r\n-CHAR:0..." 모두 수신

// Step 5-2: 3초 대기 (불필요)
Thread.sleep(3000);

// Step 5-3: 추가 데이터 읽기 (4096 bytes 버퍼)
ret = At.Lib_ComRecvAT(uuidResponse, uuidLen, 8000, 4096);
// → 더 이상 수신할 데이터 없음 또는 타임아웃
```

**문제**:
- 첫 번째 `Lib_ComRecvAT()`가 **128 bytes 버퍼**로 제한되어 모든 CHAR 데이터를 받을 수 없음
- 두 번째 `Lib_ComRecvAT()`는 이미 버퍼에 남은 데이터가 없거나 부족함

#### 원인 2: 버퍼 크기 부족
```java
byte[] okResponse = new byte[128];  // ← 너무 작음!
```

실제 수신 데이터 크기 추정:
```
OK\r\n (4 bytes)
+ 72:54:CF:62:3B:90 CONNECTED 1\r\n (31 bytes)
+ -CHAR:0 ~ -CHAR:14 (약 14줄 × 평균 50 bytes = 700 bytes)
= 약 735 bytes
```

128 bytes 버퍼로는 일부만 수신 가능 → **데이터 잘림**

### 🔧 해결 방안

#### 방안 A: 한 번에 모두 읽기 (권장)
UUID_SCAN 응답이 실제로는 한 번에 온다면:

```java
// Step 5: UUID Scan (한 번에 읽기)
String uuidScanCmd = "AT+UUID_SCAN=1\r\n";
At.Lib_ComSend(uuidScanCmd.getBytes(), uuidScanCmd.length());

// 충분한 대기 시간 (GATT Discovery)
Thread.sleep(3000);

// 큰 버퍼로 한 번에 모두 수신
byte[] uuidResponse = new byte[4096];
int[] uuidLen = new int[1];
ret = At.Lib_ComRecvAT(uuidResponse, uuidLen, 8000, 4096);

String uuidResponseStr = new String(uuidResponse, 0, uuidLen[0]);
// OK, CONNECTED, -CHAR:... 모두 포함

// OK 확인
if (!uuidResponseStr.contains("OK")) {
    return error;
}

// CHAR 파싱
List<UuidChannel> channels = parseUuidScanResponse(uuidResponseStr);
```

#### 방안 B: 두 번 읽되 첫 버퍼 크기 증가
```java
// Step 5-1: OK + 일부 CHAR 데이터 읽기 (큰 버퍼)
byte[] firstResponse = new byte[4096];  // 128 → 4096
int[] firstLen = new int[1];
ret = At.Lib_ComRecvAT(firstResponse, firstLen, 5000, 4096);

// Step 5-2: 추가 데이터가 있을 경우 더 읽기
byte[] secondResponse = new byte[4096];
int[] secondLen = new int[1];
ret = At.Lib_ComRecvAT(secondResponse, secondLen, 2000, 4096);

// 두 응답 합치기
String combined = new String(firstResponse, 0, firstLen[0]) +
                  new String(secondResponse, 0, secondLen[0]);
```

#### 방안 C: maxLen 파라미터 확인
`Lib_ComRecvAT()` 시그니처:
```java
int Lib_ComRecvAT(byte[] buffer, int[] len, int timeout, int maxLen)
//                                                         ↑
//                                              실제로 읽을 최대 바이트
```

`maxLen=128`은 **128 bytes까지만 읽음** → 나머지 데이터는 버퍼에 남음

### 📝 다음 디버깅 단계

1. **로그 확인**:
   ```java
   Log.i(TAG, "[DEBUG] okLen[0] = " + okLen[0]);  // 실제 수신 바이트 수
   Log.i(TAG, "[DEBUG] Full response: " + okStr);
   ```

2. **테스트 A: 한 번에 읽기**
   - `Thread.sleep(3000)` 후 단 한 번만 `Lib_ComRecvAT()` 호출
   - 버퍼: 4096 bytes
   - Timeout: 8000ms
   - maxLen: 4096

3. **테스트 B: 여러 번 읽기**
   - 첫 번째: maxLen=4096
   - 두 번째: maxLen=4096 (추가 데이터 확인)
   - 각 수신 데이터 길이 로그

4. **BLE 모듈 문서 재확인**:
   - UUID_SCAN 응답 형식이 실제로 2개로 나뉘는지 확인
   - 응답 예시가 한 번에 오는지, 비동기인지 명확히 파악

### 🔍 관련 파일

- `BleConnection.java:315-381` - UUID_SCAN 처리 로직
- `BLE_GATT_Connection_Guide.md:200-241` - UUID_SCAN 문서
- `ConnectLogic.md` - Connect/Send 로직 상세 문서

### ⏸️ 임시 해결

현재 코드 상태로 커밋하여 이력 보존. 다음 세션에서 위 해결 방안 테스트 예정.

---

## 2025-12-06: BLE Connection 로직 리팩토링 완료

### ✅ 완료된 작업

1. **BleConnection.java 리팩토링**
   - `connectToDevice()`: Steps 2-4 통합
   - `sendDataComplete()`: Steps 5-9 통합
   - `disconnect()`: Step 10

2. **문서 작성**
   - `ConnectLogic.md`: Connect/Send 버튼 로직 상세 문서
     - Thread 및 비동기 처리 설명
     - 타이밍 다이어그램
     - Blocking Point 분석

3. **BeaconActivity.java 수정 최소화**
   - 로직은 모두 BleConnection으로 이동
   - Activity는 UI 업데이트만 담당

### 📚 참고 문서

- `BLE_GATT_Connection_Guide.md` - AT Command 가이드
- `ConnectLogic.md` - 로직 상세 설명
- `CLAUDE.md` - 프로젝트 전체 문서

---

**Next Session TODO**:
- [ ] UUID_SCAN 응답 수신 문제 해결 (방안 A 우선 테스트)
- [ ] 로그 추가하여 실제 수신 데이터 크기 확인
- [ ] 필요시 BLE 모듈 문서 재확인
