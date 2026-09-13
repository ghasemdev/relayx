# Quickstart: Android Rule & Filtering Engine

**Feature**: `specs/005-android-rule-engine`  
**Date**: 2026-09-14  

---

## 1. Running Automated Verification Tests

```bash
# Run all unit tests including RuleEngineTest and RuleRepositoryTest
rtk ./gradlew test

# Run only rule engine test suite
rtk ./gradlew testDebugUnitTest --tests "com.parsomash.relayx.domain.engine.*"
```

---

## 2. Testing in the Android App (On-Device Sandbox)

1. Launch **RelayX** on a connected device or Android Emulator.
2. Tap the new **Rules** tab in the bottom navigation bar.
3. Review the seeded starter rules (e.g., Default OTP Rule).
4. Tap **Rule Sandbox** at the bottom of the screen:
   - **Sender**: `CHASE_ALERT`
   - **Message**: `Your temporary passcode is 948201. Valid for 10 minutes.`
   - Tap **"Test Against Rules"**.
5. Observe the instant match evaluation preview:
   - **Matched Rule**: Default Verification Code
   - **Evaluated Action**: `FORWARD_TRANSFORMED`
   - **Extracted Payload**: `948201`
   - **Delivery Plan**: Ready for forward (raw message omitted)

---

## 3. Simulating Live Inbound SMS (No SIM Card Required)

Test that unmatched messages are dropped locally (`FILTERED`), while matching messages are enqueued and dispatched:

```bash
# 1. Start RelayX Server
bin/relayx-server --host 127.0.0.1 --port 8080 --debug

# 2. Inject an UNMATCHED SMS from friend (should be DROPPED)
adb -s emulator-5554 emu sms send "FRIEND_JOHN" "Hey, are you free for coffee today?"

# Check Android App -> In Dashboard, 'Filtered' counter increments to 1.
# Server logs receive 0 requests.

# 3. Inject a MATCHED SMS (should be FORWARDED)
adb -s emulator-5554 emu sms send "BANK_AUTH" "Your verification code is 482910."

# Check Android App -> 'Forwarded' counter increments to 1.
# Server receives the extracted OTP and records the message in SQLite!
```
