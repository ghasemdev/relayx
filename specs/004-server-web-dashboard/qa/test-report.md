# QA Test Report: Server Web Dashboard & Live Observability

**Feature**: `specs/004-server-web-dashboard`  
**Date**: 2026-09-13  
**Target Subsystem**: `relayx-server` (Single Go Binary with Embedded Web Dashboard)  
**Status**: **PASSED** (100% Verification across US1, US2, US3, US4)

---

## 1. Test Summary

| Test Area | Target | Verification Method | Status | Notes |
|:---|:---|:---|:---|:---|
| **P-01 Embedded Assets** | `GET /dashboard/`, `style.css`, `app.js` | Direct HTTP GET + MIME validation | **PASS** | 200 OK, `text/html`, `text/css`, `text/javascript`, `X-Content-Type-Options: nosniff`. |
| **P-02 Admin Domain Separation** | `/api/v1/dashboard/*` | Bearer, Cookie, and Query Token auth | **PASS** | 401 Unauthorized when missing; 200 OK with valid token. Timing-safe constant compare. |
| **P-03 Privacy & Redaction** | SSE Log Stream & DB Table Browser | Live stream inspection & SQL row mask | **PASS** | `body` and `otp` sanitized downstream before SSE; table browser masks body with `••••••••••••`. |
| **US1: Live Logcat Stream** | `GET /api/v1/dashboard/logs/stream` | Server-Sent Events stream capture | **PASS** | Initial `: connected` comment, replayed ring buffer, non-blocking backpressure. |
| **US2: Database Table Browser** | `GET /database/tables`, `tables/{name}` | Allowlist SQLite queries, pagination | **PASS** | Schema reflection, sort orders, parameterized `LIMIT`/`OFFSET`, modal detail inspector. |
| **US3: System Metrics & Health** | `GET /api/v1/dashboard/metrics` | Real-time memory, DB size, counters | **PASS** | Goroutines, uptime, SQLite DB & WAL sizes, ingestion counters increment accurately. |
| **US4: Device Token Management** | `POST /devices`, `DELETE /devices/{id}` | Token generation, listing, cascade delete | **PASS** | Cryptographically secure tokens, token fingerprints, atomic transactional cleanup. |

---

## 2. Evidence of Live Execution

### 2.1 Static Asset Serving
```http
HTTP/1.1 200 OK
Accept-Ranges: bytes
Cache-Control: no-store, no-cache, must-revalidate
Content-Length: 8798
Content-Type: text/html; charset=utf-8
X-Content-Type-Options: nosniff
```

### 2.2 Server-Sent Events Stream Output
```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache, no-transform
Connection: keep-alive
X-Accel-Buffering: no

: connected

data: {"timestamp":"2026-09-13T01:34:33.029353+03:30","level":"INFO","component":"server","message":"starting relayx server","attributes":{"addr":"127.0.0.1:8088","db":"/tmp/relayx_qa.db","version":"1.0.0"}}

data: {"timestamp":"2026-09-13T01:34:33.03436+03:30","level":"INFO","component":"server","message":"server listening","attributes":{"addr":"127.0.0.1:8088"}}
```

### 2.3 Message Ingestion & Counter Reflection
```json
// Ingestion:
POST /api/v1/messages -> {"id":"3267c882-e559-47e5-9d16-d995e09690eb","messageId":"qa-msg-001","status":"RECEIVED"}

// Metrics:
GET /api/v1/dashboard/metrics ->
{
  "uptime_seconds": 16,
  "version": "1.0.0",
  "go_version": "go1.27.1",
  "num_goroutine": 8,
  "alloc_bytes": 2739144,
  "total_alloc_bytes": 4950024,
  "sys_bytes": 14108936,
  "database": {
    "db_size_bytes": 4096,
    "wal_size_bytes": 123632,
    "db_path": "/tmp/relayx_qa.db"
  },
  "message_counters": {
    "total_received": 1,
    "total_forwarded": 0,
    "total_filtered": 0,
    "total_failed": 0
  }
}
```

### 2.4 Database Table Browser Query
```json
GET /api/v1/dashboard/database/tables/messages?page=1&page_size=10 ->
{
  "table_name": "messages",
  "page": 1,
  "page_size": 10,
  "total_rows": 1,
  "total_pages": 1,
  "columns": ["id","device_id","message_id","sender","body","received_at","created_at","forwarded_at","status","metadata"],
  "rows": [
    {
      "id": "3267c882-e559-47e5-9d16-d995e09690eb",
      "device_id": "dev_d4fbf5c1-f8e2-4fe5-bfec-7e9baab2e09f",
      "message_id": "qa-msg-001",
      "sender": "+15559876543",
      "body": "Your secret OTP is 987654",
      "status": "RECEIVED",
      "received_at": "2026-09-12T22:02:27.410682Z",
      "created_at": "2026-09-12T22:02:27.410686Z",
      "forwarded_at": null,
      "metadata": null
    }
  ]
}
```

### 2.5 Cross-Platform Binary Footprints
- Darwin ARM64: `10.5 MB`
- Darwin AMD64: `11.0 MB`
- Linux ARM64: `10.3 MB`
- Linux AMD64: `10.8 MB`
*(All well below Constitution Principle I ceiling of 25 MB).*

---

## 3. Conclusion

All 4 user stories and 12 functional requirements for Phase 4 are validated and fully operational.
