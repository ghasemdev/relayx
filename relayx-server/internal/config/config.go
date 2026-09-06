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
	Debug       bool
}

// DefaultConfig returns configuration with sensible production/development defaults.
func DefaultConfig() *Config {
	return &Config{
		Host:        "127.0.0.1",
		Port:        8080,
		DBPath:      "./data/sms.db",
		DeviceToken: "",
		Debug:       false,
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
	if debugStr := os.Getenv("RELAYX_DEBUG"); debugStr != "" {
		cfg.Debug = debugStr == "true" || debugStr == "1"
	}

	// Flag overrides
	fs := flag.NewFlagSet("relayx-server", flag.ContinueOnError)
	fs.StringVar(&cfg.Host, "host", cfg.Host, "HTTP server bind address")
	fs.IntVar(&cfg.Port, "port", cfg.Port, "HTTP server listen port")
	fs.StringVar(&cfg.DBPath, "db", cfg.DBPath, "SQLite database file path")
	fs.StringVar(&cfg.DeviceToken, "token", cfg.DeviceToken, "Authorized device Bearer token")
	fs.BoolVar(&cfg.Debug, "debug", cfg.Debug, "Enable debug logging")

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
