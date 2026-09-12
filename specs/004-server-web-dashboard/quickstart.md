# Quickstart: Server Web Dashboard & Live Observability

**Feature**: `specs/004-server-web-dashboard`  
**Target**: `relayx-server`  

---

## 1. Build and Run Server with Web Dashboard

```bash
cd relayx-server

# Compile standalone executable
rtk go build -o ../bin/relayx-server ./cmd/server

# Run server with debug mode
../bin/relayx-server --host 127.0.0.1 --port 8080 --debug
```

---

## 2. Access the Web Dashboard

Open your web browser and navigate to:
```text
http://127.0.0.1:8080/dashboard/
```

### Verification Steps:
1. **Overview Tab**:
   - Verify server uptime counter is ticking.
   - Verify SQLite database size, WAL file size, and message throughput counters are displayed.
2. **Live Logcat Tab**:
   - Confirm the SSE connection status shows `CONNECTED` (green dot).
   - Filter logs by `INFO`, `WARN`, `ERROR`, or search by keyword.
   - In another terminal, send a test message:
     ```bash
     curl -X POST http://127.0.0.1:8080/api/v1/messages \
       -H "Authorization: Bearer dev_token_123" \
       -H "Content-Type: application/json" \
       -d '{"messageId":"test-uuid-001","sender":"15551234567","body":"Your OTP is 123456","timestamp":"2026-09-13T01:00:00Z"}'
     ```
   - Verify that the log line appears instantly in the browser without page reload.
   - **Privacy Verification**: Confirm that the log attribute shows `"body": "[REDACTED]"` and no `123456` appears in the log stream.
3. **SQLite Database Browser**:
   - Click the "Database" tab.
   - Select `messages` table.
   - Verify that message rows appear with columns `id`, `sender`, `status`, and `body`.
   - Verify the message body is masked by default (`••••••••••••`).
   - Click the eye icon next to the body to reveal the unmasked text.
   - Click a message row to view full metadata in the inspection modal.
4. **Device Management**:
   - Click "Devices" tab.
   - Click "Register New Device", enter "Test Device", and verify a token is generated.
   - Test revoking a device.
