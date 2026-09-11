# Interface Contract: Android Gateway -> RelayX Server

**Feature**: `specs/002-android-gateway-foundation`
**Protocol**: HTTP/1.1 or HTTP/2 over cleartext HTTP or TLS (HTTPS)

---

## 1. Health Probe (`GET /api/v1/health`)

Used by the "Test Connection" button in the Settings screen.

- **Method**: `GET`
- **Path**: `/api/v1/health`
- **Headers**:
  - `Accept: application/json`
- **Authentication**: None required.
- **Success Response (200 OK)**:
  ```json
  {
    "status": "ok",
    "uptime_seconds": 3600,
    "database": "connected",
    "version": "1.0.0"
  }
  ```
- **Error Handling**:
  - `SocketTimeoutException` / `ConnectException`: Display "Server unreachable at <host>:<port>".
  - HTTP 4xx/5xx: Display "Server returned error: HTTP <status>".

---

## 2. Ingest SMS Message (`POST /api/v1/messages`)

Used by the Android message dispatch worker to transmit queued SMS records.

- **Method**: `POST`
- **Path**: `/api/v1/messages`
- **Headers**:
  - `Authorization: Bearer <device_bearer_token>`
  - `Content-Type: application/json`
  - `Accept: application/json`
- **Request Body**:
  ```json
  {
    "messageId": "4c52089f-8a07-4e92-bc10-1849db9f4e22",
    "deviceId": "pixel8-primary-01",
    "sender": "BANK_AUTH",
    "body": "Your secret code is 849201",
    "receivedAt": 1757280000000,
    "metadata": {
      "sim_slot": 0,
      "carrier": "T-Mobile",
      "retry_count": 0
    }
  }
  ```

- **Responses**:
  - `201 Created`:
    ```json
    {
      "id": "18fbc8d0-6bc7-43a7-bfa8-0e318cfb704c",
      "messageId": "4c52089f-8a07-4e92-bc10-1849db9f4e22",
      "status": "RECEIVED"
    }
    ```
    *Action*: Transition outbox record to `DELIVERED`. Increment `Forwarded` counter.
  - `200 OK` (Idempotent duplicate acknowledgement):
    ```json
    {
      "id": "18fbc8d0-6bc7-43a7-bfa8-0e318cfb704c",
      "messageId": "4c52089f-8a07-4e92-bc10-1849db9f4e22",
      "status": "RECEIVED",
      "duplicate": true
    }
    ```
    *Action*: Transition outbox record to `DELIVERED`. Increment `Forwarded` counter.
  - `401 Unauthorized`:
    ```json
    {
      "error": "unauthorized"
    }
    ```
    *Action*: Record failure with reason "Invalid Bearer Token". Halt retries until user updates settings.
  - `5xx Server Error` / Network Dropout:
    *Action*: Reschedule message with exponential backoff (1s, 2s, 5s, 10s, 30s, 60s max).
