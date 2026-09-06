package api_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"relayx-server/internal/api"
	"relayx-server/internal/storage"
)

func TestHealthHandler(t *testing.T) {
	ctx := context.Background()
	dbPath := filepath.Join(t.TempDir(), "health_test.db")
	db, err := storage.Open(ctx, dbPath)
	if err != nil {
		t.Fatalf("failed to open db: %v", err)
	}
	defer db.Close()

	handler := api.NewHealthHandler(db, "1.0.0")

	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	rec := httptest.NewRecorder()

	handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}

	var resp map[string]any
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}

	if resp["status"] != "ok" {
		t.Errorf("expected status ok, got %v", resp["status"])
	}
	if resp["database"] != "connected" {
		t.Errorf("expected database connected, got %v", resp["database"])
	}
	if resp["version"] != "1.0.0" {
		t.Errorf("expected version 1.0.0, got %v", resp["version"])
	}
}
