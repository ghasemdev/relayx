package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/google/uuid"
	"relayx-server/internal/api"
	"relayx-server/internal/config"
	"relayx-server/internal/hook"
	"relayx-server/internal/logging"
	"relayx-server/internal/service"
	"relayx-server/internal/storage"
)

const ServerVersion = "1.0.0"

func main() {
	cfg, err := config.Load(os.Args[1:])
	if err != nil {
		fmt.Fprintf(os.Stderr, "Configuration error: %v\n", err)
		os.Exit(1)
	}

	logger := logging.InitLogger(cfg.Debug)
	logger.Info("starting relayx server", "version", ServerVersion, "db", cfg.DBPath, "addr", cfg.ListenAddr())

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	db, err := storage.Open(ctx, cfg.DBPath)
	cancel()
	if err != nil {
		logger.Error("failed to open database", "error", err)
		os.Exit(1)
	}
	defer db.Close()

	deviceRepo := storage.NewDeviceRepository(db)
	messageRepo := storage.NewMessageRepository(db)

	deviceService := service.NewDeviceService(deviceRepo)
	messageService := service.NewMessageService(messageRepo)

	hookRunner := hook.NewRunner(cfg.ADBPort, cfg.ExecHook)
	messageService.SetHook(hookRunner)
	if cfg.ADBPort > 0 {
		logger.Info("emulator adb relay hook enabled", "port", cfg.ADBPort, "target", fmt.Sprintf("emulator-%d", cfg.ADBPort))
	}
	if cfg.ExecHook != "" {
		logger.Info("custom exec relay hook enabled", "hook", cfg.ExecHook)
	}

	// Handle device token setup
	initCtx := context.Background()
	if cfg.DeviceToken != "" {
		if _, err := deviceService.RegisterDevice(initCtx, "Configured Gateway", cfg.DeviceToken); err != nil {
			logger.Error("failed to register configured device token", "error", err)
			os.Exit(1)
		}
		logger.Info("configured gateway device token registered successfully")
	} else {
		// Check if any device exists, if not generate one
		var count int
		_ = db.QueryRowContext(initCtx, "SELECT COUNT(1) FROM devices").Scan(&count)
		if count == 0 {
			generatedToken := "rx-" + uuid.NewString()
			if _, err := deviceService.RegisterDevice(initCtx, "Default Gateway Phone", generatedToken); err != nil {
				logger.Error("failed to register initial gateway device", "error", err)
				os.Exit(1)
			}
			fmt.Println("==================================================================")
			fmt.Println(" [RelayX] Generated Initial Device Write Token:")
			fmt.Printf("   Bearer %s\n", generatedToken)
			fmt.Println(" Configure your Android RelayX gateway app with this Bearer token.")
			fmt.Println("==================================================================")
		}
	}

	server := api.NewServer(cfg, db)
	messageHandler := api.NewMessageHandler(messageService)
	healthHandler := api.NewHealthHandler(db, ServerVersion)

	// Routes
	server.Mux().Handle("GET /api/v1/health", healthHandler)

	authMiddleware := api.AuthenticateDevice(deviceService)
	server.Mux().Handle("POST /api/v1/messages", authMiddleware(http.HandlerFunc(messageHandler.IngestMessage)))
	server.Mux().HandleFunc("GET /api/v1/messages", messageHandler.ListMessages)
	server.Mux().HandleFunc("GET /api/v1/messages/latest", messageHandler.GetLatestMessage)
	server.Mux().HandleFunc("GET /api/v1/messages/{id}", messageHandler.GetMessageByID)

	// Wrap server with security headers, logging, and recovery middleware
	rootHandler := api.Recovery(api.SecurityHeaders(api.RequestLogger(server.Mux())))
	server.SetHandler(rootHandler)

	// Run server in goroutine
	go func() {
		if err := server.Start(); err != nil {
			logger.Error("server stopped unexpectedly", "error", err)
			os.Exit(1)
		}
	}()

	// Listen for shutdown signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
	sig := <-sigChan
	logger.Info("shutdown signal received", "signal", sig.String())

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Error("error during graceful shutdown", "error", err)
	}

	logger.Info("relayx server stopped cleanly")
}
