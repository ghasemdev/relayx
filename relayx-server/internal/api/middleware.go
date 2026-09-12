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

// AuthenticateAdmin enforces optional admin token authentication on dashboard endpoints.
// If adminToken is empty, access is unrestricted.
func AuthenticateAdmin(adminToken string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if adminToken == "" {
				next.ServeHTTP(w, r)
				return
			}

			// 1. Check Bearer Authorization header
			authHeader := r.Header.Get("Authorization")
			if authHeader != "" {
				parts := strings.SplitN(authHeader, " ", 2)
				if len(parts) == 2 && strings.EqualFold(parts[0], "Bearer") {
					if strings.TrimSpace(parts[1]) == adminToken {
						next.ServeHTTP(w, r)
						return
					}
				}
			}

			// 2. Check Cookie
			if cookie, err := r.Cookie("relayx_admin_token"); err == nil {
				if cookie.Value == adminToken {
					next.ServeHTTP(w, r)
					return
				}
			}

			// 3. Check Query parameter (convenient for SSE EventSource connections)
			if tokenParam := r.URL.Query().Get("token"); tokenParam != "" {
				if tokenParam == adminToken {
					next.ServeHTTP(w, r)
					return
				}
			}

			writeJSONError(w, http.StatusUnauthorized, "unauthorized admin access: invalid or missing admin token")
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

func (rw *responseWriterInterceptor) Flush() {
	if flusher, ok := rw.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func (rw *responseWriterInterceptor) Unwrap() http.ResponseWriter {
	return rw.ResponseWriter
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

// SecurityHeaders adds essential security and anti-caching headers to all HTTP responses (TASK-SEC-002).
func SecurityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Cache-Control", "no-store, no-cache, must-revalidate")
		w.Header().Set("Pragma", "no-cache")
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
