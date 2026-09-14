package mcp_test

import (
	"bufio"
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/mcp"
)

func TestSSEAuthentication(t *testing.T) {
	server, _, _ := setupTestServer(t)
	handler := mcp.NewSSEHandler(server, "valid-agent-token", nil)

	// 1. Missing Token -> 401
	rec1 := httptest.NewRecorder()
	req1 := httptest.NewRequest("GET", "/mcp/sse", nil)
	handler.HandleSSE(rec1, req1)
	if rec1.Code != http.StatusUnauthorized {
		t.Errorf("expected 401 for missing token, got %d", rec1.Code)
	}

	// 2. Invalid Token (or device write token) -> 401
	rec2 := httptest.NewRecorder()
	req2 := httptest.NewRequest("GET", "/mcp/sse", nil)
	req2.Header.Set("Authorization", "Bearer android-device-token")
	handler.HandleSSE(rec2, req2)
	if rec2.Code != http.StatusUnauthorized {
		t.Errorf("expected 401 for wrong token, got %d", rec2.Code)
	}

	// 3. Valid Token via Header -> 200 SSE Stream initiated
	rec3 := httptest.NewRecorder()
	reqCtx, reqCancel := context.WithCancel(context.Background())
	req3 := httptest.NewRequest("GET", "/mcp/sse", nil).WithContext(reqCtx)
	req3.Header.Set("Authorization", "Bearer valid-agent-token")

	// Cancel after short delay to break streaming loop
	go func() {
		time.Sleep(50 * time.Millisecond)
		reqCancel()
	}()

	handler.HandleSSE(rec3, req3)
	if rec3.Code != http.StatusOK {
		t.Errorf("expected 200 OK, got %d", rec3.Code)
	}
	if !strings.Contains(rec3.Body.String(), "event: endpoint\ndata: /mcp/messages?sessionId=") {
		t.Errorf("expected endpoint event in body, got: %s", rec3.Body.String())
	}
}

func TestSSEMessageDispatch(t *testing.T) {
	server, _, _ := setupTestServer(t)
	handler := mcp.NewSSEHandler(server, "test-mcp-token", nil)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /mcp/sse", handler.HandleSSE)
	mux.HandleFunc("POST /mcp/messages", handler.HandleMessages)
	ts := httptest.NewServer(mux)
	defer ts.Close()

	// Connect SSE client
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sseReq, _ := http.NewRequestWithContext(ctx, "GET", ts.URL+"/mcp/sse", nil)
	sseReq.Header.Set("Authorization", "Bearer test-mcp-token")

	resp, err := http.DefaultClient.Do(sseReq)
	if err != nil {
		t.Fatalf("connecting to sse: %v", err)
	}
	defer resp.Body.Close()

	scanner := bufio.NewScanner(resp.Body)
	var endpointPath string
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "data: /mcp/messages?sessionId=") {
			endpointPath = strings.TrimPrefix(line, "data: ")
			break
		}
	}

	if endpointPath == "" {
		t.Fatal("failed to read endpoint event from SSE stream")
	}

	// Send JSON-RPC initialize request via POST to endpoint
	rpcPayload := `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}`
	postReq, _ := http.NewRequest("POST", ts.URL+endpointPath, strings.NewReader(rpcPayload))
	postReq.Header.Set("Authorization", "Bearer test-mcp-token")
	postReq.Header.Set("Content-Type", "application/json")

	postResp, err := http.DefaultClient.Do(postReq)
	if err != nil {
		t.Fatalf("sending post message: %v", err)
	}
	defer postResp.Body.Close()

	if postResp.StatusCode != http.StatusAccepted {
		t.Errorf("expected 202 Accepted, got %d", postResp.StatusCode)
	}

	// Read response back from SSE stream
	var sseResponseLine string
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "data: ") {
			sseResponseLine = strings.TrimPrefix(line, "data: ")
			break
		}
	}

	if sseResponseLine == "" {
		t.Fatal("failed to receive response line from SSE stream")
	}

	if !strings.Contains(sseResponseLine, `"protocolVersion":"2024-11-05"`) {
		t.Errorf("expected protocolVersion in response, got: %s", sseResponseLine)
	}
}

func TestSSEOriginValidation(t *testing.T) {
	server, _, _ := setupTestServer(t)
	handler := mcp.NewSSEHandler(server, "test-mcp-token", nil)

	// 1. Untrusted origin -> 403 Forbidden
	rec1 := httptest.NewRecorder()
	req1 := httptest.NewRequest("GET", "/mcp/sse", nil)
	req1.Header.Set("Origin", "http://evil.com")
	req1.Header.Set("Authorization", "Bearer test-mcp-token")
	handler.HandleSSE(rec1, req1)
	if rec1.Code != http.StatusForbidden {
		t.Errorf("expected 403 Forbidden for evil.com origin, got %d", rec1.Code)
	}

	// 2. Trusted localhost origin -> 200 OK with specific origin header
	rec2 := httptest.NewRecorder()
	reqCtx, reqCancel := context.WithCancel(context.Background())
	req2 := httptest.NewRequest("GET", "/mcp/sse", nil).WithContext(reqCtx)
	req2.Header.Set("Origin", "http://localhost:3000")
	req2.Header.Set("Authorization", "Bearer test-mcp-token")

	go func() {
		time.Sleep(50 * time.Millisecond)
		reqCancel()
	}()

	handler.HandleSSE(rec2, req2)
	if rec2.Code != http.StatusOK {
		t.Errorf("expected 200 OK for localhost origin, got %d", rec2.Code)
	}
	if rec2.Header().Get("Access-Control-Allow-Origin") != "http://localhost:3000" {
		t.Errorf("expected Access-Control-Allow-Origin http://localhost:3000, got: %s", rec2.Header().Get("Access-Control-Allow-Origin"))
	}
	if rec2.Header().Get("Access-Control-Allow-Origin") == "*" {
		t.Error("wildcard CORS '*' must never be returned")
	}

	// 3. Preflight OPTIONS request for localhost origin -> 204 No Content
	rec3 := httptest.NewRecorder()
	req3 := httptest.NewRequest("OPTIONS", "/mcp/messages", nil)
	req3.Header.Set("Origin", "http://127.0.0.1:5173")
	handler.HandleMessages(rec3, req3)
	if rec3.Code != http.StatusNoContent {
		t.Errorf("expected 204 No Content for OPTIONS, got %d", rec3.Code)
	}
	if rec3.Header().Get("Access-Control-Allow-Origin") != "http://127.0.0.1:5173" {
		t.Errorf("expected Access-Control-Allow-Origin http://127.0.0.1:5173, got %s", rec3.Header().Get("Access-Control-Allow-Origin"))
	}
}

func TestSSEEmptyTokenRejection(t *testing.T) {
	server, _, _ := setupTestServer(t)
	handler := mcp.NewSSEHandler(server, "", nil) // Empty token

	rec := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/mcp/sse", nil)
	handler.HandleSSE(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized when server has no MCP token set, got %d", rec.Code)
	}
}

func TestSSEAsyncToolContextSurvival(t *testing.T) {
	server, _, broker := setupTestServer(t)
	handler := mcp.NewSSEHandler(server, "test-token", nil)

	mux := http.NewServeMux()
	mux.HandleFunc("/mcp/sse", handler.HandleSSE)
	mux.HandleFunc("/mcp/messages", handler.HandleMessages)
	ts := httptest.NewServer(mux)
	defer ts.Close()

	// Connect SSE stream
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sseReq, _ := http.NewRequestWithContext(ctx, "GET", ts.URL+"/mcp/sse", nil)
	sseReq.Header.Set("Authorization", "Bearer test-token")

	sseResp, err := http.DefaultClient.Do(sseReq)
	if err != nil {
		t.Fatalf("connecting to sse: %v", err)
	}
	defer sseResp.Body.Close()

	scanner := bufio.NewScanner(sseResp.Body)
	var endpointPath string
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "data: /mcp/messages?sessionId=") {
			endpointPath = strings.TrimPrefix(line, "data: ")
			break
		}
	}

	if endpointPath == "" {
		t.Fatal("failed to read endpoint event")
	}

	// Dispatch wait_for_message via POST
	rpcPayload := `{"jsonrpc":"2.0","id":42,"method":"tools/call","params":{"name":"wait_for_message","arguments":{"sender":"CHASE","timeout":5}}}`
	postReq, _ := http.NewRequest("POST", ts.URL+endpointPath, strings.NewReader(rpcPayload))
	postReq.Header.Set("Authorization", "Bearer test-token")
	postReq.Header.Set("Content-Type", "application/json")

	postResp, err := http.DefaultClient.Do(postReq)
	if err != nil {
		t.Fatalf("sending post request: %v", err)
	}
	defer postResp.Body.Close()

	if postResp.StatusCode != http.StatusAccepted {
		t.Fatalf("expected 202 Accepted, got %d", postResp.StatusCode)
	}

	// Wait until wait_for_message has actively subscribed to the broker
	for i := 0; i < 100; i++ {
		if broker.ActiveSubscribers() > 0 {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}

	// Publish message to wake up the waiting tool
	broker.Publish(&domain.Message{
		ID:         "msg-async-1",
		Sender:     "CHASE",
		Body:       "Code 998877",
		ReceivedAt: time.Now().UTC(),
		Status:     domain.StatusReceived,
	})

	// Assert response arrives in SSE stream
	var foundMessage bool
	for scanner.Scan() {
		line := scanner.Text()
		if strings.Contains(line, "998877") && strings.Contains(line, "success") {
			foundMessage = true
			break
		}
	}

	if !foundMessage {
		t.Error("expected wait_for_message response in SSE stream after POST returned, but none was received")
	}
}

