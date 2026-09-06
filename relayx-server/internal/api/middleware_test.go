package api_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"relayx-server/internal/api"
	"relayx-server/internal/service"
	"relayx-server/internal/storage"
)

func TestAuthenticateDeviceMiddleware(t *testing.T) {
	ctx := context.Background()
	dbPath := filepath.Join(t.TempDir(), "auth_test.db")
	db, err := storage.Open(ctx, dbPath)
	if err != nil {
		t.Fatalf("failed to open test db: %v", err)
	}
	defer db.Close()

	deviceRepo := storage.NewDeviceRepository(db)
	devService := service.NewDeviceService(deviceRepo)

	// Register test device with token
	validToken := "valid-secret-token"
	device, err := devService.RegisterDevice(ctx, "Test Phone", validToken)
	if err != nil {
		t.Fatalf("failed to register device: %v", err)
	}

	authMiddleware := api.AuthenticateDevice(devService)

	testHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authed := api.DeviceFromContext(r.Context())
		if authed == nil || authed.ID != device.ID {
			http.Error(w, "missing context device", http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusOK)
	})

	handlerToTest := authMiddleware(testHandler)

	// Case 1: Missing Authorization Header -> 401
	{
		req := httptest.NewRequest(http.MethodPost, "/api/v1/messages", nil)
		rec := httptest.NewRecorder()
		handlerToTest.ServeHTTP(rec, req)

		if rec.Code != http.StatusUnauthorized {
			t.Errorf("expected status 401 for missing auth header, got %d", rec.Code)
		}
	}

	// Case 2: Invalid Bearer Token -> 401
	{
		req := httptest.NewRequest(http.MethodPost, "/api/v1/messages", nil)
		req.Header.Set("Authorization", "Bearer invalid-token")
		rec := httptest.NewRecorder()
		handlerToTest.ServeHTTP(rec, req)

		if rec.Code != http.StatusUnauthorized {
			t.Errorf("expected status 401 for invalid token, got %d", rec.Code)
		}
	}

	// Case 3: Valid Bearer Token -> 200 OK
	{
		req := httptest.NewRequest(http.MethodPost, "/api/v1/messages", nil)
		req.Header.Set("Authorization", "Bearer "+validToken)
		rec := httptest.NewRecorder()
		handlerToTest.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected status 200 for valid token, got %d", rec.Code)
		}
	}
}

func TestSecurityHeadersMiddleware(t *testing.T) {
	dummy := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	handler := api.SecurityHeaders(dummy)
	req := httptest.NewRequest(http.MethodGet, "/api/v1/messages", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Errorf("expected nosniff, got %s", rec.Header().Get("X-Content-Type-Options"))
	}
	if rec.Header().Get("Cache-Control") != "no-store, no-cache, must-revalidate" {
		t.Errorf("expected no-store, got %s", rec.Header().Get("Cache-Control"))
	}
}
