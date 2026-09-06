package logging_test

import (
	"bytes"
	"context"
	"log/slog"
	"strings"
	"testing"

	"relayx-server/internal/logging"
)

func TestRedactingHandler(t *testing.T) {
	buf := &bytes.Buffer{}
	handler := logging.NewRedactingHandler(buf, true)
	logger := slog.New(handler)

	ctx := context.Background()

	// 1. Log with sensitive body
	logger.LogAttrs(ctx, slog.LevelInfo, "incoming sms",
		slog.String("sender", "BANK"),
		slog.String("body", "Your OTP is 987654. Do not share."),
		slog.String("token", "super-secret-token"),
	)

	output := buf.String()

	if strings.Contains(output, "987654") {
		t.Errorf("expected OTP code to be redacted, found in logs: %s", output)
	}
	if strings.Contains(output, "super-secret-token") {
		t.Errorf("expected token to be redacted, found in logs: %s", output)
	}
	if !strings.Contains(output, `"[REDACTED]"`) {
		t.Errorf("expected [REDACTED] in output, got: %s", output)
	}
	if !strings.Contains(output, "BANK") {
		t.Errorf("expected non-sensitive sender 'BANK' to be preserved, got: %s", output)
	}
}
