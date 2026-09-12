package logging

import (
	"context"
	"io"
	"log/slog"
	"os"
	"regexp"
	"strings"

	"relayx-server/internal/domain"
)

var (
	sensitiveKeys = map[string]bool{
		"body":          true,
		"otp":           true,
		"code":          true,
		"token":         true,
		"token_hash":    true,
		"authorization": true,
		"auth":          true,
		"secret":        true,
		"password":      true,
	}

	otpRegex = regexp.MustCompile(`\b\d{4,8}\b`)
)

// RedactingHandler wraps an slog.Handler to scrub sensitive values and tokens.
type RedactingHandler struct {
	next slog.Handler
}

// NewRedactingHandler creates an slog.Handler that redacts sensitive keys and patterns.
func NewRedactingHandler(w io.Writer, debug bool) *RedactingHandler {
	level := slog.LevelInfo
	if debug {
		level = slog.LevelDebug
	}

	opts := &slog.HandlerOptions{
		Level: level,
		ReplaceAttr: func(groups []string, a slog.Attr) slog.Attr {
			return sanitizeAttr(a)
		},
	}

	jsonHandler := slog.NewJSONHandler(w, opts)
	return &RedactingHandler{next: jsonHandler}
}

func sanitizeAttr(a slog.Attr) slog.Attr {
	keyLower := strings.ToLower(a.Key)

	// Direct sensitive key matching
	if sensitiveKeys[keyLower] {
		return slog.String(a.Key, "[REDACTED]")
	}

	// Handle string values that may contain sensitive patterns
	if a.Value.Kind() == slog.KindString {
		val := a.Value.String()
		// Redact Bearer tokens in headers or messages
		if strings.HasPrefix(strings.ToLower(val), "bearer ") {
			return slog.String(a.Key, "Bearer [REDACTED]")
		}
	}

	return a
}

func (h *RedactingHandler) Enabled(ctx context.Context, level slog.Level) bool {
	return h.next.Enabled(ctx, level)
}

func (h *RedactingHandler) Handle(ctx context.Context, r slog.Record) error {
	// Sanitize attributes inside the record
	newRecord := slog.NewRecord(r.Time, r.Level, r.Message, r.PC)
	attrsMap := make(map[string]any)
	r.Attrs(func(a slog.Attr) bool {
		sanitized := sanitizeAttr(a)
		newRecord.AddAttrs(sanitized)
		attrsMap[sanitized.Key] = sanitized.Value.Any()
		return true
	})

	if GlobalBroadcaster != nil {
		component := "server"
		if comp, ok := attrsMap["component"].(string); ok && comp != "" {
			component = comp
		}
		GlobalBroadcaster.Broadcast(domain.LogEntry{
			Timestamp:  r.Time,
			Level:      r.Level.String(),
			Component:  component,
			Message:    r.Message,
			Attributes: attrsMap,
		})
	}

	return h.next.Handle(ctx, newRecord)
}

func (h *RedactingHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	sanitized := make([]slog.Attr, 0, len(attrs))
	for _, a := range attrs {
		sanitized = append(sanitized, sanitizeAttr(a))
	}
	return &RedactingHandler{next: h.next.WithAttrs(sanitized)}
}

func (h *RedactingHandler) WithGroup(name string) slog.Handler {
	return &RedactingHandler{next: h.next.WithGroup(name)}
}

// InitLogger initializes and sets the default global slog logger.
func InitLogger(debug bool) *slog.Logger {
	handler := NewRedactingHandler(os.Stdout, debug)
	logger := slog.New(handler)
	slog.SetDefault(logger)
	return logger
}
