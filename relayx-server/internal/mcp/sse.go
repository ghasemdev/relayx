package mcp

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"sync"

	"github.com/google/uuid"
	"relayx-server/internal/domain"
)

// SSESession represents an active Server-Sent Events client connection.
type SSESession struct {
	ID        string
	messages  chan []byte
	done      chan struct{}
	ctx       context.Context
	cancel    context.CancelFunc
	closeOnce sync.Once
}

func newSSESession() *SSESession {
	ctx, cancel := context.WithCancel(context.Background())
	return &SSESession{
		ID:        uuid.NewString(),
		messages:  make(chan []byte, 32),
		done:      make(chan struct{}),
		ctx:       ctx,
		cancel:    cancel,
	}
}

func (s *SSESession) Close() {
	s.closeOnce.Do(func() {
		close(s.done)
		s.cancel()
	})
}

// SSEHandler manages MCP sessions over HTTP/SSE.
type SSEHandler struct {
	server   *Server
	mcpToken string
	logger   *slog.Logger
	mu       sync.RWMutex
	sessions map[string]*SSESession
}

// NewSSEHandler creates an SSE transport handler for MCP.
func NewSSEHandler(server *Server, mcpToken string, logger *slog.Logger) *SSEHandler {
	if logger == nil {
		logger = slog.Default()
	}
	return &SSEHandler{
		server:   server,
		mcpToken: strings.TrimSpace(mcpToken),
		logger:   logger,
		sessions: make(map[string]*SSESession),
	}
}

// checkOrigin validates the Origin header and sets appropriate CORS response headers.
// Allows localhost origins and non-browser clients (Origin header empty).
func (h *SSEHandler) checkOrigin(w http.ResponseWriter, r *http.Request) bool {
	origin := r.Header.Get("Origin")
	if origin == "" {
		return true // Non-browser clients (native agent runtimes, curl)
	}

	u, err := url.Parse(origin)
	if err != nil {
		http.Error(w, `{"error":"forbidden: invalid origin"}`, http.StatusForbidden)
		return false
	}

	hostname := strings.ToLower(u.Hostname())
	if hostname == "localhost" || hostname == "127.0.0.1" || hostname == "::1" {
		w.Header().Set("Access-Control-Allow-Origin", origin)
		w.Header().Set("Access-Control-Allow-Credentials", "true")
		w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		return true
	}

	http.Error(w, `{"error":"forbidden: untrusted origin"}`, http.StatusForbidden)
	return false
}

// Authenticate verifies the request holds the valid MCP Bearer token.
func (h *SSEHandler) Authenticate(r *http.Request) bool {
	if h.mcpToken == "" {
		return false // Never bypass authentication when token is empty
	}

	authHeader := r.Header.Get("Authorization")
	var token string
	if strings.HasPrefix(strings.ToLower(authHeader), "bearer ") {
		token = strings.TrimSpace(authHeader[7:])
	} else {
		token = r.URL.Query().Get("token")
	}

	if token == "" {
		return false
	}

	return subtle.ConstantTimeCompare([]byte(token), []byte(h.mcpToken)) == 1
}

// HandleSSE establishes an SSE connection on /mcp/sse.
func (h *SSEHandler) HandleSSE(w http.ResponseWriter, r *http.Request) {
	if !h.checkOrigin(w, r) {
		return
	}

	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}

	if r.Method != http.MethodGet {
		http.Error(w, `{"error":"method not allowed"}`, http.StatusMethodNotAllowed)
		return
	}

	if !h.Authenticate(r) {
		http.Error(w, `{"error":"unauthorized: invalid or missing mcp token"}`, http.StatusUnauthorized)
		return
	}

	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming unsupported", http.StatusInternalServerError)
		return
	}

	session := newSSESession()
	h.mu.Lock()
	h.sessions[session.ID] = session
	h.mu.Unlock()

	defer func() {
		h.mu.Lock()
		delete(h.sessions, session.ID)
		h.mu.Unlock()
		session.Close()
		h.logger.Info("mcp sse session closed", "sessionId", session.ID)
	}()

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	// Emit endpoint event informing client where to send JSON-RPC requests
	endpointMsg := fmt.Sprintf("/mcp/messages?sessionId=%s", session.ID)
	fmt.Fprintf(w, "event: endpoint\ndata: %s\n\n", endpointMsg)
	flusher.Flush()

	h.logger.Info("mcp sse session established", "sessionId", session.ID)

	notify := r.Context().Done()
	for {
		select {
		case <-notify:
			return
		case <-session.done:
			return
		case msg := <-session.messages:
			fmt.Fprintf(w, "event: message\ndata: %s\n\n", string(msg))
			flusher.Flush()
		}
	}
}

// HandleMessages processes incoming JSON-RPC POST requests on /mcp/messages.
func (h *SSEHandler) HandleMessages(w http.ResponseWriter, r *http.Request) {
	if !h.checkOrigin(w, r) {
		return
	}

	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusNoContent)
		return
	}

	if r.Method != http.MethodPost {
		http.Error(w, `{"error":"method not allowed"}`, http.StatusMethodNotAllowed)
		return
	}

	if !h.Authenticate(r) {
		http.Error(w, `{"error":"unauthorized: invalid or missing mcp token"}`, http.StatusUnauthorized)
		return
	}

	sessionID := r.URL.Query().Get("sessionId")
	if sessionID == "" {
		http.Error(w, `{"error":"missing sessionId parameter"}`, http.StatusBadRequest)
		return
	}

	h.mu.RLock()
	session, exists := h.sessions[sessionID]
	h.mu.RUnlock()

	if !exists {
		http.Error(w, `{"error":"unknown or expired sessionId"}`, http.StatusNotFound)
		return
	}

	body, err := io.ReadAll(io.LimitReader(r.Body, 4*1024*1024))
	if err != nil {
		http.Error(w, `{"error":"reading request body"}`, http.StatusBadRequest)
		return
	}
	defer r.Body.Close()

	req, parseErr := ParseRequest(body)
	if parseErr != nil {
		errBytes, _ := json.Marshal(parseErr)
		select {
		case session.messages <- errBytes:
		default:
		}
		w.WriteHeader(http.StatusAccepted)
		return
	}

	// Process request asynchronously using session context bounded by SSE connection lifecycle
	go func(rpcReq *domain.RPCRequest, sessCtx context.Context) {
		resp := h.server.HandleRequest(sessCtx, rpcReq)
		if resp != nil {
			respBytes, err := json.Marshal(resp)
			if err == nil {
				select {
				case session.messages <- respBytes:
				case <-session.done:
				}
			}
		}
	}(req, session.ctx)

	w.WriteHeader(http.StatusAccepted)
}

