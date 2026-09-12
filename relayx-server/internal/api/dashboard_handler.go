package api

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/logging"
	"relayx-server/internal/service"
)

// DashboardHandler provides HTTP handlers for the web dashboard and observability APIs.
type DashboardHandler struct {
	dashboardService *service.DashboardService
	broadcaster      *logging.LogBroadcaster
	adminToken       string
}

// NewDashboardHandler initializes a DashboardHandler.
func NewDashboardHandler(dashService *service.DashboardService, broadcaster *logging.LogBroadcaster, adminToken string) *DashboardHandler {
	if broadcaster == nil {
		broadcaster = logging.GlobalBroadcaster
	}
	return &DashboardHandler{
		dashboardService: dashService,
		broadcaster:      broadcaster,
		adminToken:       adminToken,
	}
}

// HandleLogStream streams real-time redacted log events via Server-Sent Events (SSE).
func (h *DashboardHandler) HandleLogStream(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported by server", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache, no-transform")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")

	// Parse filters
	minLevel := strings.ToUpper(r.URL.Query().Get("level"))
	componentFilter := r.URL.Query().Get("component")
	searchFilter := strings.ToLower(r.URL.Query().Get("search"))

	ch, recent := h.broadcaster.Subscribe(100)
	defer h.broadcaster.Unsubscribe(ch)

	// Send initial connection comment
	fmt.Fprintf(w, ": connected\n\n")
	flusher.Flush()

	// Send recent logs first
	for _, entry := range recent {
		if shouldSendLog(entry, minLevel, componentFilter, searchFilter) {
			if data, err := json.Marshal(entry); err == nil {
				fmt.Fprintf(w, "data: %s\n\n", data)
			}
		}
	}
	flusher.Flush()

	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-r.Context().Done():
			return
		case <-ticker.C:
			// Heartbeat comment to keep SSE connection alive
			fmt.Fprintf(w, ": ping\n\n")
			flusher.Flush()
		case entry, ok := <-ch:
			if !ok {
				return
			}
			if shouldSendLog(entry, minLevel, componentFilter, searchFilter) {
				if data, err := json.Marshal(entry); err == nil {
					fmt.Fprintf(w, "data: %s\n\n", data)
					flusher.Flush()
				}
			}
		}
	}
}

func shouldSendLog(entry domain.LogEntry, minLevel, compFilter, search string) bool {
	if minLevel != "" && minLevel != "ALL" {
		levels := map[string]int{
			"DEBUG": 1,
			"INFO":  2,
			"WARN":  3,
			"ERROR": 4,
		}
		if levels[entry.Level] < levels[minLevel] {
			return false
		}
	}

	if compFilter != "" && !strings.EqualFold(entry.Component, compFilter) {
		return false
	}

	if search != "" {
		msgLower := strings.ToLower(entry.Message)
		if !strings.Contains(msgLower, search) {
			return false
		}
	}

	return true
}

// HandleGetMetrics returns system, memory, SQLite, and message statistics.
func (h *DashboardHandler) HandleGetMetrics(w http.ResponseWriter, r *http.Request) {
	metrics, err := h.dashboardService.GetMetrics(r.Context())
	if err != nil {
		slog.Error("failed to retrieve dashboard metrics", "error", err)
		writeJSONError(w, http.StatusInternalServerError, "failed to retrieve system metrics")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(metrics)
}

// HandleGetTables returns browsable SQLite table metadata.
func (h *DashboardHandler) HandleGetTables(w http.ResponseWriter, r *http.Request) {
	tables, err := h.dashboardService.ListTables(r.Context())
	if err != nil {
		slog.Error("failed to list database tables", "error", err)
		writeJSONError(w, http.StatusInternalServerError, "failed to list tables")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(tables)
}

// HandleQueryTable returns paginated, sorted rows from an allowed table.
func (h *DashboardHandler) HandleQueryTable(w http.ResponseWriter, r *http.Request) {
	tableName := r.PathValue("name")
	if tableName == "" {
		writeJSONError(w, http.StatusBadRequest, "table name required")
		return
	}

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	if page < 1 {
		page = 1
	}

	pageSize, _ := strconv.Atoi(r.URL.Query().Get("page_size"))
	if pageSize < 1 || pageSize > 100 {
		pageSize = 25
	}

	sortBy := r.URL.Query().Get("sort_by")
	sortOrder := r.URL.Query().Get("sort_order")
	status := r.URL.Query().Get("status")
	sender := r.URL.Query().Get("sender")
	search := r.URL.Query().Get("search")

	result, err := h.dashboardService.QueryTable(r.Context(), tableName, page, pageSize, sortBy, sortOrder, status, sender, search)
	if err != nil {
		slog.Error("failed to query table", "table", tableName, "error", err)
		writeJSONError(w, http.StatusBadRequest, err.Error())
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(result)
}

// HandleListDevices returns registered devices.
func (h *DashboardHandler) HandleListDevices(w http.ResponseWriter, r *http.Request) {
	devices, err := h.dashboardService.ListDevices(r.Context())
	if err != nil {
		slog.Error("failed to list devices", "error", err)
		writeJSONError(w, http.StatusInternalServerError, "failed to list devices")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(devices)
}

type registerDeviceRequest struct {
	Name string `json:"name"`
}

type registerDeviceResponse struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Token     string `json:"token"`
	CreatedAt string `json:"created_at"`
}

// HandleRegisterDevice creates a new device credential.
func (h *DashboardHandler) HandleRegisterDevice(w http.ResponseWriter, r *http.Request) {
	var req registerDeviceRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	summary, rawToken, err := h.dashboardService.RegisterDevice(r.Context(), req.Name)
	if err != nil {
		slog.Error("failed to register device", "error", err)
		writeJSONError(w, http.StatusInternalServerError, "failed to register device")
		return
	}

	resp := registerDeviceResponse{
		ID:        summary.ID,
		Name:      summary.Name,
		Token:     rawToken,
		CreatedAt: summary.CreatedAt.Format(time.RFC3339),
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	_ = json.NewEncoder(w).Encode(resp)
}

// HandleRevokeDevice revokes a device credential.
func (h *DashboardHandler) HandleRevokeDevice(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		writeJSONError(w, http.StatusBadRequest, "device id required")
		return
	}

	if err := h.dashboardService.RevokeDevice(r.Context(), id); err != nil {
		slog.Error("failed to revoke device", "id", id, "error", err)
		writeJSONError(w, http.StatusInternalServerError, "failed to revoke device")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{
		"message": "device revoked successfully",
	})
}

type loginRequest struct {
	Token string `json:"token"`
}

// HandleLogin verifies an admin token and sets the session cookie.
func (h *DashboardHandler) HandleLogin(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	if h.adminToken != "" && req.Token != h.adminToken {
		writeJSONError(w, http.StatusUnauthorized, "invalid admin token")
		return
	}

	http.SetCookie(w, &http.Cookie{
		Name:     "relayx_admin_token",
		Value:    req.Token,
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		MaxAge:   86400 * 30, // 30 days
	})

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success": true,
		"message": "admin authenticated",
	})
}
