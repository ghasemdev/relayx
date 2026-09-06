package api

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/service"
)

type contextKey string

const (
	deviceContextKey contextKey = "authenticated_device"
)

// DeviceFromContext retrieves the authenticated Device from the request context.
func DeviceFromContext(ctx context.Context) *domain.Device {
	if val, ok := ctx.Value(deviceContextKey).(*domain.Device); ok {
		return val
	}
	return nil
}

// AuthenticateDevice enforces Bearer token authentication for gateway requests.
func AuthenticateDevice(devService *service.DeviceService) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := r.Header.Get("Authorization")
			if authHeader == "" {
				writeJSONError(w, http.StatusUnauthorized, "missing authorization header")
				return
			}

			parts := strings.SplitN(authHeader, " ", 2)
			if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
				writeJSONError(w, http.StatusUnauthorized, "invalid authorization header format")
				return
			}

			rawToken := strings.TrimSpace(parts[1])
			device, err := devService.Authenticate(r.Context(), rawToken)
			if err != nil {
				if errors.Is(err, service.ErrUnauthorized) {
					writeJSONError(w, http.StatusUnauthorized, "unauthorized device token")
					return
				}
				slog.Error("device authentication error", "error", err)
				writeJSONError(w, http.StatusInternalServerError, "authentication processing failed")
				return
			}

			ctx := context.WithValue(r.Context(), deviceContextKey, device)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// responseWriterInterceptor captures the HTTP response status code.
type responseWriterInterceptor struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriterInterceptor) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}

// RequestLogger logs incoming HTTP requests and durations without leaking sensitive payloads.
func RequestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		interceptor := &responseWriterInterceptor{
			ResponseWriter: w,
			statusCode:     http.StatusOK,
		}

		next.ServeHTTP(interceptor, r)

		duration := time.Since(start)
		slog.Info("http request",
			"method", r.Method,
			"path", r.URL.Path,
			"status", interceptor.statusCode,
			"duration_ms", duration.Milliseconds(),
			"remote_addr", r.RemoteAddr,
		)
	})
}

// Recovery catches panics and returns a generic 500 error.
func Recovery(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if rec := recover(); rec != nil {
				slog.Error("panic recovered in http handler", "panic", rec)
				writeJSONError(w, http.StatusInternalServerError, "internal server error")
			}
		}()
		next.ServeHTTP(w, r)
	})
}

func writeJSONError(w http.ResponseWriter, statusCode int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	_ = json.NewEncoder(w).Encode(map[string]string{
		"error": message,
	})
}
