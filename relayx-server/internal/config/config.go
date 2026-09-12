package config

import (
	"flag"
	"fmt"
	"os"
	"strconv"
)

// Config holds runtime server configuration settings.
type Config struct {
	Host        string
	Port        int
	DBPath      string
	DeviceToken string
	AdminToken  string
	Debug       bool
	ADBPort     int
	ExecHook    string
}

// DefaultConfig returns configuration with sensible production/development defaults.
func DefaultConfig() *Config {
	return &Config{
		Host:        "127.0.0.1",
		Port:        8080,
		DBPath:      "./data/sms.db",
		DeviceToken: "",
		AdminToken:  "",
		Debug:       false,
		ADBPort:     0,
		ExecHook:    "",
	}
}

// Load loads configuration from environment variables and CLI arguments.
// CLI flags override environment variables; environment variables override defaults.
func Load(args []string) (*Config, error) {
	cfg := DefaultConfig()

	// Environment variable overrides
	if host := os.Getenv("RELAYX_HOST"); host != "" {
		cfg.Host = host
	}
	if portStr := os.Getenv("RELAYX_PORT"); portStr != "" {
		if p, err := strconv.Atoi(portStr); err == nil && p > 0 && p <= 65535 {
			cfg.Port = p
		}
	}
	if dbPath := os.Getenv("RELAYX_DB_PATH"); dbPath != "" {
		cfg.DBPath = dbPath
	}
	if token := os.Getenv("RELAYX_DEVICE_TOKEN"); token != "" {
		cfg.DeviceToken = token
	}
	if adminToken := os.Getenv("RELAYX_ADMIN_TOKEN"); adminToken != "" {
		cfg.AdminToken = adminToken
	}
	if debugStr := os.Getenv("RELAYX_DEBUG"); debugStr != "" {
		cfg.Debug = debugStr == "true" || debugStr == "1"
	}
	if adbPortStr := os.Getenv("RELAYX_ADB_PORT"); adbPortStr != "" {
		if p, err := strconv.Atoi(adbPortStr); err == nil && p > 0 && p <= 65535 {
			cfg.ADBPort = p
		}
	}
	if execHook := os.Getenv("RELAYX_EXEC_HOOK"); execHook != "" {
		cfg.ExecHook = execHook
	}

	// Flag overrides
	fs := flag.NewFlagSet("relayx-server", flag.ContinueOnError)
	fs.StringVar(&cfg.Host, "host", cfg.Host, "HTTP server bind address")
	fs.IntVar(&cfg.Port, "port", cfg.Port, "HTTP server listen port")
	fs.StringVar(&cfg.DBPath, "db", cfg.DBPath, "SQLite database file path")
	fs.StringVar(&cfg.DeviceToken, "token", cfg.DeviceToken, "Authorized device Bearer token")
	fs.StringVar(&cfg.AdminToken, "admin-token", cfg.AdminToken, "Administrative token to access web dashboard")
	fs.BoolVar(&cfg.Debug, "debug", cfg.Debug, "Enable debug logging")
	fs.IntVar(&cfg.ADBPort, "adb-port", cfg.ADBPort, "Android emulator port to relay SMS via adb emu sms send (e.g. 5554)")
	fs.StringVar(&cfg.ExecHook, "exec-hook", cfg.ExecHook, "Custom executable/script hook to run on message arrival")

	if err := fs.Parse(args); err != nil {
		return nil, fmt.Errorf("parsing flags: %w", err)
	}

	if cfg.Port < 1 || cfg.Port > 65535 {
		return nil, fmt.Errorf("invalid port %d: must be between 1 and 65535", cfg.Port)
	}

	return cfg, nil
}

// ListenAddr returns the formatted "host:port" address.
func (c *Config) ListenAddr() string {
	return fmt.Sprintf("%s:%d", c.Host, c.Port)
}
