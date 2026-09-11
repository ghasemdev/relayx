package config_test

import (
	"os"
	"testing"

	"relayx-server/internal/config"
)

func TestDefaultConfig(t *testing.T) {
	cfg := config.DefaultConfig()
	if cfg.Host != "127.0.0.1" {
		t.Errorf("expected default host 127.0.0.1, got %s", cfg.Host)
	}
	if cfg.Port != 8080 {
		t.Errorf("expected default port 8080, got %d", cfg.Port)
	}
	if cfg.DBPath != "./data/sms.db" {
		t.Errorf("expected default db ./data/sms.db, got %s", cfg.DBPath)
	}
	if cfg.Debug {
		t.Errorf("expected default debug false, got %v", cfg.Debug)
	}
	if cfg.ListenAddr() != "127.0.0.1:8080" {
		t.Errorf("expected listen addr 127.0.0.1:8080, got %s", cfg.ListenAddr())
	}
}

func TestFlagOverrides(t *testing.T) {
	args := []string{
		"-host", "0.0.0.0",
		"-port", "9090",
		"-db", "/tmp/test.db",
		"-token", "sec-token-123",
		"-debug",
		"-adb-port", "5554",
		"-exec-hook", "/bin/echo",
	}

	cfg, err := config.Load(args)
	if err != nil {
		t.Fatalf("unexpected error loading config: %v", err)
	}

	if cfg.Host != "0.0.0.0" {
		t.Errorf("expected host 0.0.0.0, got %s", cfg.Host)
	}
	if cfg.Port != 9090 {
		t.Errorf("expected port 9090, got %d", cfg.Port)
	}
	if cfg.DBPath != "/tmp/test.db" {
		t.Errorf("expected db /tmp/test.db, got %s", cfg.DBPath)
	}
	if cfg.DeviceToken != "sec-token-123" {
		t.Errorf("expected token sec-token-123, got %s", cfg.DeviceToken)
	}
	if !cfg.Debug {
		t.Errorf("expected debug true, got %v", cfg.Debug)
	}
	if cfg.ADBPort != 5554 {
		t.Errorf("expected adb port 5554, got %d", cfg.ADBPort)
	}
	if cfg.ExecHook != "/bin/echo" {
		t.Errorf("expected exec hook /bin/echo, got %s", cfg.ExecHook)
	}
}

func TestEnvOverrides(t *testing.T) {
	os.Setenv("RELAYX_HOST", "192.168.1.50")
	os.Setenv("RELAYX_PORT", "7070")
	os.Setenv("RELAYX_DB_PATH", "./custom.db")
	os.Setenv("RELAYX_DEVICE_TOKEN", "env-token")
	os.Setenv("RELAYX_DEBUG", "1")
	os.Setenv("RELAYX_ADB_PORT", "5556")
	os.Setenv("RELAYX_EXEC_HOOK", "/usr/bin/logger")
	defer func() {
		os.Unsetenv("RELAYX_HOST")
		os.Unsetenv("RELAYX_PORT")
		os.Unsetenv("RELAYX_DB_PATH")
		os.Unsetenv("RELAYX_DEVICE_TOKEN")
		os.Unsetenv("RELAYX_DEBUG")
		os.Unsetenv("RELAYX_ADB_PORT")
		os.Unsetenv("RELAYX_EXEC_HOOK")
	}()

	cfg, err := config.Load([]string{})
	if err != nil {
		t.Fatalf("unexpected error loading config: %v", err)
	}

	if cfg.Host != "192.168.1.50" {
		t.Errorf("expected host 192.168.1.50, got %s", cfg.Host)
	}
	if cfg.Port != 7070 {
		t.Errorf("expected port 7070, got %d", cfg.Port)
	}
	if cfg.DBPath != "./custom.db" {
		t.Errorf("expected db ./custom.db, got %s", cfg.DBPath)
	}
	if cfg.DeviceToken != "env-token" {
		t.Errorf("expected token env-token, got %s", cfg.DeviceToken)
	}
	if !cfg.Debug {
		t.Errorf("expected debug true, got %v", cfg.Debug)
	}
	if cfg.ADBPort != 5556 {
		t.Errorf("expected adb port 5556, got %d", cfg.ADBPort)
	}
	if cfg.ExecHook != "/usr/bin/logger" {
		t.Errorf("expected exec hook /usr/bin/logger, got %s", cfg.ExecHook)
	}
}

func TestInvalidPort(t *testing.T) {
	_, err := config.Load([]string{"-port", "999999"})
	if err == nil {
		t.Error("expected error for invalid port 999999, got nil")
	}
}
