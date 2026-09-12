# Quickstart & Verification Guide: Android Message Inspection & Detail Modal

**Feature**: `specs/003-android-message-inspection` | **Date**: 2026-09-12

---

## 1. Automated Verification

Run unit tests covering `MessageListViewModel`, DAO queries, and filter state transitions:

```bash
# Run unit tests
rtk ./gradlew test

# Assemble and verify debug APK
rtk ./gradlew assembleDebug
```

---

## 2. Interactive Verification on Android Device / Emulator

### Prerequisites
- Android device or emulator running (`adb devices`)
- App installed: `adb install -r relayx-android/build/outputs/apk/debug/relayx-android-debug.apk`

### Verification Walkthrough

1. **Verify Metric Card Navigation**:
   - Open RelayX.
   - On the **Dashboard**, tap the **Received** card → Verify it navigates to `MessageListScreen` with the **All** tab selected.
   - Tap Back.
   - Tap the **Forwarded** card → Verify it navigates to `MessageListScreen` with the **Forwarded** tab selected.
   - Tap Back.
   - Tap the **Failed** card → Verify it navigates with the **Failed** tab selected.

2. **Verify Last Message Received Detail Bottom Sheet**:
   - Send a test SMS to the emulator:
     ```bash
     adb emu sms send 12345 "Quickstart OTP test 554433"
     ```
   - On the **Dashboard**, tap the **"Last Message Received"** card.
   - Verify the **Modal Bottom Sheet** appears smoothly.
   - Verify:
     - Message UUID is displayed with a copy button.
     - Sender shows `12345`.
     - Timestamp shows current time.
     - Body is masked by default (`••••••••`).
     - Tapping the Eye toggle reveals `Quickstart OTP test 554433`.
   - Dismiss the sheet by tapping outside or swiping down.

3. **Verify Message List Item Inspection**:
   - Tap any card in the **MessageListScreen**.
   - Verify the same `ModalBottomSheet` opens displaying that item's details.

4. **Verify Immediate Retry Action**:
   - For a message in `FAILED` status, tap **"Retry Delivery"** inside the bottom sheet.
   - Verify status transitions to `PENDING` and WorkManager schedules delivery.
