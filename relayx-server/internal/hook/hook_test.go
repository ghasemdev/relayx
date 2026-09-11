package hook_test

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/hook"
)

func TestHookRunnerExecHook(t *testing.T) {
	tempDir := t.TempDir()
	logFile := filepath.Join(tempDir, "hook_out.txt")

	// Create a simple shell script hook
	scriptPath := filepath.Join(tempDir, "test_hook.sh")
	scriptContent := "#!/bin/sh\necho \"$1|$RELAYX_SENDER|$RELAYX_MESSAGE_ID\" > " + logFile + "\n"
	if err := os.WriteFile(scriptPath, []byte(scriptContent), 0755); err != nil {
		t.Fatalf("failed to create test script: %v", err)
	}

	runner := hook.NewRunner(0, scriptPath)

	msg := &domain.Message{
		ID:        "uuid-1",
		MessageID: "msg-123",
		DeviceID:  "dev-1",
		Sender:    "BANK_AUTH",
		Body:      "Secret 123456",
	}

	runner.Trigger(context.Background(), msg)

	// Wait up to 2 seconds for the async script to finish
	var content []byte
	var err error
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		content, err = os.ReadFile(logFile)
		if err == nil && len(content) > 0 {
			break
		}
		time.Sleep(50 * time.Millisecond)
	}

	if err != nil {
		t.Fatalf("hook failed to execute or write output: %v", err)
	}

	expected := "BANK_AUTH|BANK_AUTH|msg-123\n"
	if string(content) != expected {
		t.Errorf("expected hook output %q, got %q", expected, string(content))
	}
}

func TestHookRunnerNilSafety(t *testing.T) {
	var runner *hook.Runner
	// Should not panic
	runner.Trigger(context.Background(), nil)
	runner.Trigger(context.Background(), &domain.Message{})
}
