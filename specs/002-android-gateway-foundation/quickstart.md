# Quickstart & Verification: Android Gateway Foundation

**Feature**: `specs/002-android-gateway-foundation`
**Module**: `relayx-android`

---

## 1. Prerequisites

- Android SDK 37 (minSdk 24) installed.
- Android Emulator or physical device with developer options & USB debugging enabled.
- Go 1.22+ to run the local RelayX server.

---

## 2. Start the Local Server

In a separate terminal, launch the standalone RelayX server:

```bash
cd relayx-server
go run ./cmd/server --port 8080 --data ./data --debug
```

Confirm health via curl:
```bash
curl -s http://127.0.0.1:8080/api/v1/health | jq .
```

---

## 3. Build & Run the Android Gateway

### Build Debug APK
```bash
rtk ./gradlew assembleDebug
```

### Run Unit Tests
```bash
rtk ./gradlew test
```

### Install APK onto Device or Emulator
```bash
adb install -r relayx-android/build/outputs/apk/debug/relayx-android-debug.apk
```

---

## 4. Manual End-to-End Verification Procedure

1. **Launch App**: Open `RelayX` on the device.
2. **Configure Connection**:
   - Navigate to **Settings**.
   - Set Host: `10.0.2.2` (if Android Emulator) or your host machine's Wi-Fi LAN IP (if physical phone).
   - Set Port: `8080`.
   - Set Device ID: `test-device-01`.
   - Set Bearer Token: (matches server configured token, or any test token).
   - Tap **"Test Connection"**. Confirm green success indicator showing latency.
3. **Grant Permissions & Enable Forwarding**:
   - Navigate to **Dashboard**.
   - Toggle **Forwarding** to `ON`. Accept `RECEIVE_SMS` runtime permission prompt.
4. **Simulate Incoming SMS**:
   - In emulator:
     ```bash
     adb emu sms send "BANK_AUTH" "Your verification code is 591024"
     ```
   - On physical phone: Send a test SMS to the device.
5. **Verify Delivery**:
   - Dashboard counters increment (`Received: 1`, `Forwarded: 1`).
   - Query server database:
     ```bash
     curl -s -H "Authorization: Bearer <token>" "http://127.0.0.1:8080/api/v1/messages/latest" | jq .
     ```
6. **Verify Privacy (Logcat Redaction)**:
   - Check Logcat during SMS receipt:
     ```bash
     adb logcat -d | grep -i relayx
     ```
   - Confirm **zero** occurrences of `"591024"` or `"Your verification code"` in logcat output.
