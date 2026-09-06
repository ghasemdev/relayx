# Quickstart: Server Foundation

**Feature**: 001-server-foundation  
**Date**: 2026-09-07  

---

## 1. Build the Binary
```bash
# From repository root or relayx-server/
go build -o sms-server ./relayx-server/cmd/server
```

## 2. Launch the Server
```bash
# Launch with default localhost binding (127.0.0.1:8080)
./sms-server

# Launch in debug mode with custom port
./sms-server --port 9090 --debug
```

On first startup, the server automatically:
- Creates `./data/` directory
- Initializes `./data/sms.db`
- Runs embedded migrations (`migrations/*.sql`)
- Begins listening on `http://127.0.0.1:8080`

## 3. Verify Health
```bash
curl -s http://127.0.0.1:8080/api/v1/health | jq .
```
Expected output:
```json
{
  "status": "ok",
  "uptime_seconds": 12,
  "database": "connected",
  "version": "1.0.0"
}
```

## 4. Test Ingestion with Curl
```bash
# Submit an incoming SMS (using test device token)
curl -s -X POST http://127.0.0.1:8080/api/v1/messages \
  -H "Authorization: Bearer test-device-token" \
  -H "Content-Type: application/json" \
  -d '{
    "messageId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "deviceId": "dev-01",
    "sender": "BANK",
    "body": "Your verification code is 482913",
    "receivedAt": 1757280000000
  }' | jq .
```

Expected output:
```json
{
  "id": "e2a3c701-4475-430b-93df-578d0a4c2f10",
  "messageId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "status": "RECEIVED"
}
```

Resending the exact same `curl` command will return HTTP 200 with `"duplicate": true` without creating a duplicate record in `./data/sms.db`.

## 5. Query Messages
```bash
curl -s http://127.0.0.1:8080/api/v1/messages?sender=BANK \
  -H "Authorization: Bearer test-device-token" | jq .
```
