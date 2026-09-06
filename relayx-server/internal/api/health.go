package api

import (
	"context"
	"database/sql"
	"encoding/json"
	"net/http"
	"time"
)

type HealthHandler struct {
	db        *sql.DB
	startTime time.Time
	version   string
}

func NewHealthHandler(db *sql.DB, version string) *HealthHandler {
	return &HealthHandler{
		db:        db,
		startTime: time.Now().UTC(),
		version:   version,
	}
}

func (h *HealthHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()

	dbStatus := "connected"
	statusCode := http.StatusOK

	if err := h.db.PingContext(ctx); err != nil {
		dbStatus = "disconnected"
		statusCode = http.StatusServiceUnavailable
	}

	uptimeSeconds := int(time.Since(h.startTime).Seconds())

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"status":         "ok",
		"uptime_seconds": uptimeSeconds,
		"database":       dbStatus,
		"version":        h.version,
	})
}
