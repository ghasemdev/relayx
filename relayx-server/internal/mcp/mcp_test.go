package mcp_test

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"strings"
	"testing"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/mcp"
	"relayx-server/internal/service"
)

func TestEndToEndStdioMCP(t *testing.T) {
	server, msgService, _ := setupTestServer(t)

	clientInReader, serverInWriter := io.Pipe()
	serverOutReader, clientOutWriter := io.Pipe()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	stdioDone := make(chan error, 1)
	go func() {
		stdioDone <- mcp.RunStdio(ctx, server, clientInReader, clientOutWriter)
	}()

	scanner := bufio.NewScanner(serverOutReader)

	// Step 1: initialize handshake
	initReq := `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}` + "\n"
	if _, err := fmt.Fprint(serverInWriter, initReq); err != nil {
		t.Fatalf("writing initialize: %v", err)
	}

	if !scanner.Scan() {
		t.Fatal("expected initialize response")
	}
	var initResp domain.RPCResponse
	if err := json.Unmarshal(scanner.Bytes(), &initResp); err != nil {
		t.Fatalf("unmarshal init response: %v", err)
	}
	if initResp.Error != nil {
		t.Fatalf("initialize error: %v", initResp.Error)
	}

	// Step 2: tools/list
	listReq := `{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}` + "\n"
	if _, err := fmt.Fprint(serverInWriter, listReq); err != nil {
		t.Fatalf("writing tools/list: %v", err)
	}

	if !scanner.Scan() {
		t.Fatal("expected tools/list response")
	}
	var listResp domain.RPCResponse
	if err := json.Unmarshal(scanner.Bytes(), &listResp); err != nil {
		t.Fatalf("unmarshal list response: %v", err)
	}
	listMap, ok := listResp.Result.(map[string]any)
	if !ok {
		t.Fatalf("expected map result, got %T", listResp.Result)
	}
	toolsList, ok := listMap["tools"].([]any)
	if !ok || len(toolsList) != 5 {
		t.Fatalf("expected 5 registered tools, got %d", len(toolsList))
	}

	// Step 3: tools/call for get_otp
	callReq := `{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_otp","arguments":{"sender":"CHASE","timeout":5}}}` + "\n"
	if _, err := fmt.Fprint(serverInWriter, callReq); err != nil {
		t.Fatalf("writing tools/call: %v", err)
	}

	time.Sleep(50 * time.Millisecond)

	// Ingest message concurrently
	go func() {
		_, _, _ = msgService.Ingest(context.Background(), "test-device", service.IngestInput{
			MessageID:  "e2e-otp-msg",
			Sender:     "CHASE",
			Body:       "Your one-time passcode is 392810. Do not share.",
			ReceivedAt: time.Now().UnixMilli(),
		})
	}()

	if !scanner.Scan() {
		t.Fatal("expected tools/call response")
	}
	var callResp domain.RPCResponse
	if err := json.Unmarshal(scanner.Bytes(), &callResp); err != nil {
		t.Fatalf("unmarshal call response: %v", err)
	}
	callResMap, ok := callResp.Result.(map[string]any)
	if !ok {
		t.Fatalf("expected CallToolResult map, got %T", callResp.Result)
	}
	contentArr := callResMap["content"].([]any)
	firstContent := contentArr[0].(map[string]any)
	text := firstContent["text"].(string)

	if !strings.Contains(text, `"otp":"392810"`) {
		t.Errorf("expected OTP 392810 in result text, got %s", text)
	}

	// Shutdown
	serverInWriter.Close()
	cancel()
}
