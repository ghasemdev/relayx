package hook

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"os/exec"
	"time"

	"relayx-server/internal/domain"
)

// Hook defines the interface for triggering external relay actions on message ingestion.
type Hook interface {
	Trigger(ctx context.Context, msg *domain.Message)
}

// Runner executes configured command hooks (ADB emulator relay or custom script) when messages arrive.
type Runner struct {
	adbPort  int
	execHook string
}

// NewRunner creates a new Hook Runner.
func NewRunner(adbPort int, execHook string) *Runner {
	return &Runner{
		adbPort:  adbPort,
		execHook: execHook,
	}
}

// Trigger asynchronously invokes active hooks for newly ingested messages.
func (r *Runner) Trigger(ctx context.Context, msg *domain.Message) {
	if r == nil || msg == nil {
		return
	}

	if r.adbPort > 0 {
		go r.runADB(msg)
	}

	if r.execHook != "" {
		go r.runExecHook(msg)
	}
}

func (r *Runner) runADB(msg *domain.Message) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	targetDevice := fmt.Sprintf("emulator-%d", r.adbPort)
	slog.Info("executing adb emulator sms relay hook",
		"target", targetDevice,
		"sender", msg.Sender,
		"message_id", msg.MessageID,
	)

	cmd := exec.CommandContext(ctx, "adb", "-s", targetDevice, "emu", "sms", "send", msg.Sender, msg.Body)
	output, err := cmd.CombinedOutput()
	if err != nil {
		slog.Error("adb emulator sms relay failed",
			"target", targetDevice,
			"error", err,
			"output", string(output),
			"message_id", msg.MessageID,
		)
		return
	}

	slog.Info("adb emulator sms relay succeeded",
		"target", targetDevice,
		"message_id", msg.MessageID,
	)
}

func (r *Runner) runExecHook(msg *domain.Message) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	slog.Info("executing custom script relay hook",
		"hook", r.execHook,
		"sender", msg.Sender,
		"message_id", msg.MessageID,
	)

	cmd := exec.CommandContext(ctx, r.execHook, msg.Sender, msg.Body, msg.MessageID, msg.DeviceID)
	cmd.Env = append(os.Environ(),
		"RELAYX_SENDER="+msg.Sender,
		"RELAYX_BODY="+msg.Body,
		"RELAYX_MESSAGE_ID="+msg.MessageID,
		"RELAYX_DEVICE_ID="+msg.DeviceID,
	)

	output, err := cmd.CombinedOutput()
	if err != nil {
		slog.Error("custom relay hook failed",
			"hook", r.execHook,
			"error", err,
			"output", string(output),
			"message_id", msg.MessageID,
		)
		return
	}

	slog.Info("custom relay hook succeeded",
		"hook", r.execHook,
		"message_id", msg.MessageID,
	)
}
