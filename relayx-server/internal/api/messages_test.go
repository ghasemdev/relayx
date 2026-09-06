package api_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"

	"relayx-server/internal/api"
	"relayx-server/internal/service"
	"relayx-server/internal/storage"
)

func setupTestServer(t *testing.T) (*api.Server, string) {
	t.Helper()
	ctx := context.Background()
	dbPath := filepath.Join(t.TempDir(), "server_test.db")
	db, err := storage.Open(ctx, dbPath)
	if err != nil {
		t.Fatalf("failed to open test db: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	deviceRepo := storage.NewDeviceRepository(db)
	messageRepo := storage.NewMessageRepository(db)

	deviceService := service.NewDeviceService(deviceRepo)
	messageService := service.NewMessageService(messageRepo)

	rawToken := "test-device-token-12345"
	_, err = deviceService.RegisterDevice(ctx, "Test Smartphone", rawToken)
	if err != nil {
		t.Fatalf("failed to register test device: %v", err)
	}

	server := api.NewServer(nil, db)
	messageHandler := api.NewMessageHandler(messageService)
	healthHandler := api.NewHealthHandler(db, "1.0.0")

	// Routes
	server.Mux().Handle("GET /api/v1/health", healthHandler)

	authMiddleware := api.AuthenticateDevice(deviceService)
	server.Mux().Handle("POST /api/v1/messages", authMiddleware(http.HandlerFunc(messageHandler.IngestMessage)))
	server.Mux().HandleFunc("GET /api/v1/messages", messageHandler.ListMessages)
	server.Mux().HandleFunc("GET /api/v1/messages/latest", messageHandler.GetLatestMessage)
	server.Mux().HandleFunc("GET /api/v1/messages/{id}", messageHandler.GetMessageByID)

	return server, rawToken
}

func TestMessageIngestionAndIdempotency(t *testing.T) {
	server, token := setupTestServer(t)

	payload := map[string]any{
		"messageId":  "msg-uuid-001",
		"sender":     "VERIFY",
		"body":       "Your code is 123456",
		"receivedAt": time.Now().UnixMilli(),
		"metadata": map[string]any{
			"sim_slot": 0,
		},
	}
	bodyBytes, _ := json.Marshal(payload)

	// 1. Initial Ingestion -> 201 Created
	req1 := httptest.NewRequest(http.MethodPost, "/api/v1/messages", bytes.NewReader(bodyBytes))
	req1.Header.Set("Authorization", "Bearer "+token)
	rec1 := httptest.NewRecorder()
	server.ServeHTTP(rec1, req1)

	if rec1.Code != http.StatusCreated {
		t.Fatalf("expected status 201 for initial ingestion, got %d: %s", rec1.Code, rec1.Body.String())
	}

	var resp1 map[string]any
	if err := json.NewDecoder(rec1.Body).Decode(&resp1); err != nil {
		t.Fatalf("failed to decode response 1: %v", err)
	}
	serverID, ok := resp1["id"].(string)
	if !ok || serverID == "" {
		t.Fatalf("expected non-empty server id in response 1")
	}
	if resp1["status"] != "RECEIVED" {
		t.Errorf("expected status RECEIVED, got %v", resp1["status"])
	}

	// 2. Duplicate Ingestion with same messageId -> 200 OK
	req2 := httptest.NewRequest(http.MethodPost, "/api/v1/messages", bytes.NewReader(bodyBytes))
	req2.Header.Set("Authorization", "Bearer "+token)
	rec2 := httptest.NewRecorder()
	server.ServeHTTP(rec2, req2)

	if rec2.Code != http.StatusOK {
		t.Fatalf("expected status 200 for duplicate message, got %d: %s", rec2.Code, rec2.Body.String())
	}

	var resp2 map[string]any
	if err := json.NewDecoder(rec2.Body).Decode(&resp2); err != nil {
		t.Fatalf("failed to decode response 2: %v", err)
	}
	if resp2["id"] != serverID {
		t.Errorf("expected duplicate response to have same server id %s, got %v", serverID, resp2["id"])
	}

	// 3. Validation failure: missing sender
	badPayload := map[string]any{
		"messageId": "msg-uuid-002",
		"body":      "Missing sender",
	}
	badBytes, _ := json.Marshal(badPayload)
	req3 := httptest.NewRequest(http.MethodPost, "/api/v1/messages", bytes.NewReader(badBytes))
	req3.Header.Set("Authorization", "Bearer "+token)
	rec3 := httptest.NewRecorder()
	server.ServeHTTP(rec3, req3)

	if rec3.Code != http.StatusBadRequest {
		t.Errorf("expected status 400 for missing sender, got %d", rec3.Code)
	}
}
