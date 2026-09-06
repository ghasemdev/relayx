package api

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"relayx-server/internal/config"
)

// Server encapsulates the HTTP server, routing, and lifecycle management.
type Server struct {
	cfg        *config.Config
	db         *sql.DB
	httpServer *http.Server
	mux        *http.ServeMux
	handler    http.Handler
}

// NewServer initializes a new Server instance.
func NewServer(cfg *config.Config, db *sql.DB) *Server {
	mux := http.NewServeMux()

	s := &Server{
		cfg: cfg,
		db:  db,
		mux: mux,
	}

	return s
}

// SetHandler configures the top-level HTTP handler (e.g. after wrapping with middleware).
func (s *Server) SetHandler(h http.Handler) {
	s.handler = h
}

// Mux returns the underlying ServeMux for route registration.
func (s *Server) Mux() *http.ServeMux {
	return s.mux
}

// Start begins listening on the configured address.
func (s *Server) Start() error {
	handler := s.handler
	if handler == nil {
		handler = s.mux
	}

	s.httpServer = &http.Server{
		Addr:         s.cfg.ListenAddr(),
		Handler:      handler,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	slog.Info("server listening", "addr", s.cfg.ListenAddr())
	if err := s.httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		return fmt.Errorf("http server failed: %w", err)
	}

	return nil
}

// Shutdown gracefully stops the HTTP server within the provided context timeout.
func (s *Server) Shutdown(ctx context.Context) error {
	if s.httpServer == nil {
		return nil
	}
	slog.Info("shutting down http server")
	return s.httpServer.Shutdown(ctx)
}

// ServeHTTP satisfies http.Handler for testing purposes.
func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if s.handler != nil {
		s.handler.ServeHTTP(w, r)
	} else {
		s.mux.ServeHTTP(w, r)
	}
}
