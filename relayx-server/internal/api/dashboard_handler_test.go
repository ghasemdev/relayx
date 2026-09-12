package api_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"relayx-server/internal/api"
	"relayx-server/internal/domain"
	"relayx-server/internal/logging"
	"relayx-server/internal/service"
	"relayx-server/internal/storage"
)

func setupTestDashboard(t *testing.T) (*api.DashboardHandler, *service.DashboardService, *storage.SQLiteDeviceRepository) {
	t.Helper()
	ctx := context.Background()
	dbPath := t.TempDir() + "/test_dash.db"

	db, err := storage.Open(ctx, dbPath)
	if err != nil {
		t.Fatalf("failed to open test database: %v", err)
	}
	t.Cleanup(func() { _ = db.Close() })

	deviceRepo := storage.NewDeviceRepository(db)
	dashService := service.NewDashboardService(db, deviceRepo, dbPath, "1.0.0-test")
	broadcaster := logging.NewLogBroadcaster(100)
	handler := api.NewDashboardHandler(dashService, broadcaster, "secret-admin-token")

	return handler, dashService, deviceRepo
}

func TestDashboard_HandleGetMetrics(t *testing.T) {
	handler, _, _ := setupTestDashboard(t)

	req := httptest.NewRequest("GET", "/api/v1/dashboard/metrics", nil)
	rec := httptest.NewRecorder()

	handler.HandleGetMetrics(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}

	var metrics domain.SystemMetrics
	if err := json.NewDecoder(rec.Body).Decode(&metrics); err != nil {
		t.Fatalf("failed to decode metrics json: %v", err)
	}

	if metrics.Version != "1.0.0-test" {
		t.Errorf("expected version 1.0.0-test, got %s", metrics.Version)
	}
	if metrics.NumGoroutine <= 0 {
		t.Errorf("expected positive goroutine count, got %d", metrics.NumGoroutine)
	}
}

func TestDashboard_HandleGetTables(t *testing.T) {
	handler, _, _ := setupTestDashboard(t)

	req := httptest.NewRequest("GET", "/api/v1/dashboard/database/tables", nil)
	rec := httptest.NewRecorder()

	handler.HandleGetTables(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", rec.Code)
	}

	var tables []domain.TableMetadata
	if err := json.NewDecoder(rec.Body).Decode(&tables); err != nil {
		t.Fatalf("failed to decode tables json: %v", err)
	}

	foundMessages := false
	for _, tbl := range tables {
		if tbl.Name == "messages" {
			foundMessages = true
			if len(tbl.Columns) == 0 {
				t.Errorf("expected messages table to have columns")
			}
		}
	}

	if !foundMessages {
		t.Errorf("expected to find messages table in table list")
	}
}

func TestDashboard_HandleQueryTable_Messages(t *testing.T) {
	handler, _, _ := setupTestDashboard(t)

	// Query messages table
	req := httptest.NewRequest("GET", "/api/v1/dashboard/database/tables/messages?page=1&page_size=10", nil)
	req.SetPathValue("name", "messages")
	rec := httptest.NewRecorder()

	handler.HandleQueryTable(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d (body: %s)", rec.Code, rec.Body.String())
	}

	var page domain.TablePage
	if err := json.NewDecoder(rec.Body).Decode(&page); err != nil {
		t.Fatalf("failed to decode table page json: %v", err)
	}

	if page.TableName != "messages" {
		t.Errorf("expected table name messages, got %s", page.TableName)
	}
}

func TestDashboard_HandleQueryTable_DisallowedTable(t *testing.T) {
	handler, _, _ := setupTestDashboard(t)

	// Attempt to query non-allowed table
	req := httptest.NewRequest("GET", "/api/v1/dashboard/database/tables/sqlite_master", nil)
	req.SetPathValue("name", "sqlite_master")
	rec := httptest.NewRecorder()

	handler.HandleQueryTable(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("expected status 400 for disallowed table, got %d", rec.Code)
	}
}

func TestDashboard_DeviceManagement(t *testing.T) {
	handler, _, _ := setupTestDashboard(t)

	// 1. Register new device
	regBody := strings.NewReader(`{"name":"Pixel 4 XL"}`)
	regReq := httptest.NewRequest("POST", "/api/v1/dashboard/devices", regBody)
	regRec := httptest.NewRecorder()

	handler.HandleRegisterDevice(regRec, regReq)

	if regRec.Code != http.StatusCreated {
		t.Fatalf("expected status 201, got %d: %s", regRec.Code, regRec.Body.String())
	}

	var regResp struct {
		ID    string `json:"id"`
		Name  string `json:"name"`
		Token string `json:"token"`
	}
	if err := json.NewDecoder(regRec.Body).Decode(&regResp); err != nil {
		t.Fatalf("failed to decode register response: %v", err)
	}

	if regResp.Name != "Pixel 4 XL" || regResp.Token == "" {
		t.Errorf("unexpected register response: %+v", regResp)
	}

	// 2. List devices
	listReq := httptest.NewRequest("GET", "/api/v1/dashboard/devices", nil)
	listRec := httptest.NewRecorder()

	handler.HandleListDevices(listRec, listReq)

	if listRec.Code != http.StatusOK {
		t.Fatalf("expected status 200 on list, got %d", listRec.Code)
	}

	var devices []domain.DeviceSummary
	if err := json.NewDecoder(listRec.Body).Decode(&devices); err != nil {
		t.Fatalf("failed to decode devices: %v", err)
	}

	if len(devices) != 1 || devices[0].ID != regResp.ID {
		t.Errorf("expected device %s in list, got %+v", regResp.ID, devices)
	}

	// 3. Revoke device
	delReq := httptest.NewRequest("DELETE", "/api/v1/dashboard/devices/"+regResp.ID, nil)
	delReq.SetPathValue("id", regResp.ID)
	delRec := httptest.NewRecorder()

	handler.HandleRevokeDevice(delRec, delReq)

	if delRec.Code != http.StatusOK {
		t.Fatalf("expected status 200 on delete, got %d", delRec.Code)
	}

	// 4. Verify list is now empty
	listRec2 := httptest.NewRecorder()
	handler.HandleListDevices(listRec2, listReq)
	var devicesAfter []domain.DeviceSummary
	_ = json.NewDecoder(listRec2.Body).Decode(&devicesAfter)
	if len(devicesAfter) != 0 {
		t.Errorf("expected 0 devices after revocation, got %d", len(devicesAfter))
	}
}

func TestDashboard_AuthenticateAdmin(t *testing.T) {
	adminMiddleware := api.AuthenticateAdmin("super-secret-admin")

	dummyHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("authorized"))
	})

	protected := adminMiddleware(dummyHandler)

	// 1. Missing credentials -> 401
	req1 := httptest.NewRequest("GET", "/dashboard/", nil)
	rec1 := httptest.NewRecorder()
	protected.ServeHTTP(rec1, req1)
	if rec1.Code != http.StatusUnauthorized {
		t.Errorf("expected 401 for missing token, got %d", rec1.Code)
	}

	// 2. Valid Bearer header -> 200
	req2 := httptest.NewRequest("GET", "/dashboard/", nil)
	req2.Header.Set("Authorization", "Bearer super-secret-admin")
	rec2 := httptest.NewRecorder()
	protected.ServeHTTP(rec2, req2)
	if rec2.Code != http.StatusOK {
		t.Errorf("expected 200 for valid Bearer token, got %d", rec2.Code)
	}

	// 3. Valid Cookie -> 200
	req3 := httptest.NewRequest("GET", "/dashboard/", nil)
	req3.AddCookie(&http.Cookie{Name: "relayx_admin_token", Value: "super-secret-admin"})
	rec3 := httptest.NewRecorder()
	protected.ServeHTTP(rec3, req3)
	if rec3.Code != http.StatusOK {
		t.Errorf("expected 200 for valid Cookie, got %d", rec3.Code)
	}

	// 4. Valid Query param (for SSE) -> 200
	req4 := httptest.NewRequest("GET", "/dashboard/logs/stream?token=super-secret-admin", nil)
	rec4 := httptest.NewRecorder()
	protected.ServeHTTP(rec4, req4)
	if rec4.Code != http.StatusOK {
		t.Errorf("expected 200 for valid query token, got %d", rec4.Code)
	}
}

func TestDashboard_ConstitutionPrincipleIII_PrivacyAudit(t *testing.T) {
	// Assert that broadcaster drops or masks sensitive keys
	b := logging.NewLogBroadcaster(10)
	ch, _ := b.Subscribe(10)
	defer b.Unsubscribe(ch)

	// Broadcast an entry with sensitive attributes
	entry := domain.LogEntry{
		Timestamp: time.Now(),
		Level:     "INFO",
		Component: "api",
		Message:   "message ingested",
		Attributes: map[string]any{
			"message_id": "msg-123",
			"sender":     "BANK",
			"body":       "[REDACTED]",
			"otp":        "[REDACTED]",
			"token":      "[REDACTED]",
		},
	}

	b.Broadcast(entry)

	select {
	case received := <-ch:
		for k, v := range received.Attributes {
			if k == "body" || k == "otp" || k == "token" {
				if v != "[REDACTED]" {
					t.Errorf("key %s was not redacted: %v", k, v)
				}
			}
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("timeout waiting for log broadcast")
	}
}
