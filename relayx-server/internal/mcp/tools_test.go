package mcp_test

import (
	"context"
	"encoding/json"
	"fmt"
	"testing"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/mcp"
	"relayx-server/internal/service"
	"relayx-server/internal/storage"
)

func setupTestServer(t *testing.T) (*mcp.Server, *service.MessageService, *mcp.MemoryBroker) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	db, err := storage.Open(ctx, ":memory:")
	if err != nil {
		t.Fatalf("failed to open memory db: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	deviceRepo := storage.NewDeviceRepository(db)
	_ = deviceRepo.Upsert(ctx, &domain.Device{
		ID:        "test-device",
		Name:      "Test Phone",
		TokenHash: "dummy-hash",
		CreatedAt: time.Now().UTC(),
	})

	msgRepo := storage.NewMessageRepository(db)
	msgService := service.NewMessageService(msgRepo)
	broker := mcp.NewMemoryBroker()
	msgService.SetBroker(broker)

	server := mcp.NewServer(nil)
	mcp.RegisterAllTools(server, msgService, broker)

	return server, msgService, broker
}

func TestWaitForMessageTimeout(t *testing.T) {
	server, _, _ := setupTestServer(t)

	req := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      1,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "wait_for_message",
			"arguments": {
				"sender": "NONEXISTENT",
				"timeout": 1
			}
		}`),
	}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()

	resp := server.HandleRequest(ctx, req)
	if resp.Error != nil {
		t.Fatalf("unexpected rpc error: %v", resp.Error)
	}

	toolRes, ok := resp.Result.(domain.CallToolResult)
	if !ok {
		t.Fatalf("expected CallToolResult, got %T", resp.Result)
	}
	if len(toolRes.Content) == 0 {
		t.Fatal("expected content in tool response")
	}

	var res domain.WaitForMessageResult
	if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &res); err != nil {
		t.Fatalf("unmarshaling wait result: %v", err)
	}

	if res.Status != "timeout" {
		t.Errorf("expected status 'timeout', got %s", res.Status)
	}
}

func TestWaitForMessageImmediateWakeup(t *testing.T) {
	server, msgService, _ := setupTestServer(t)

	req := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      2,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "wait_for_message",
			"arguments": {
				"sender": "MYBANK",
				"timeout": 5
			}
		}`),
	}

	done := make(chan *domain.RPCResponse, 1)

	// Invoke wait_for_message in background
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		done <- server.HandleRequest(ctx, req)
	}()

	// Small pause to ensure listener is subscribed
	time.Sleep(50 * time.Millisecond)

	// Simulate SMS ingestion
	ctx := context.Background()
	_, _, err := msgService.Ingest(ctx, "test-device", service.IngestInput{
		MessageID:  "msg-mcp-1",
		Sender:     "MYBANK",
		Body:       "Your security code is 748291",
		ReceivedAt: time.Now().UnixMilli(),
	})
	if err != nil {
		t.Fatalf("ingest failed: %v", err)
	}

	select {
	case resp := <-done:
		if resp.Error != nil {
			t.Fatalf("unexpected error: %v", resp.Error)
		}
		toolRes := resp.Result.(domain.CallToolResult)
		var res domain.WaitForMessageResult
		if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &res); err != nil {
			t.Fatalf("unmarshal failed: %v", err)
		}
		if res.Status != "success" {
			t.Errorf("expected status success, got %s", res.Status)
		}
		if res.Message == nil || res.Message.Sender != "MYBANK" {
			t.Errorf("unexpected message in result: %+v", res.Message)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("wait_for_message failed to unblock on message ingestion")
	}
}

func TestGetOTPDefaultRegex(t *testing.T) {
	server, msgService, _ := setupTestServer(t)

	req := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      3,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "get_otp",
			"arguments": {
				"sender": "GOOGLE",
				"timeout": 5
			}
		}`),
	}

	done := make(chan *domain.RPCResponse, 1)
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		done <- server.HandleRequest(ctx, req)
	}()

	time.Sleep(50 * time.Millisecond)

	ctx := context.Background()
	_, _, err := msgService.Ingest(ctx, "test-device", service.IngestInput{
		MessageID:  "msg-otp-1",
		Sender:     "GOOGLE",
		Body:       "G-839201 is your Google verification code.",
		ReceivedAt: time.Now().UnixMilli(),
	})
	if err != nil {
		t.Fatalf("ingest failed: %v", err)
	}

	select {
	case resp := <-done:
		if resp.Error != nil {
			t.Fatalf("unexpected error: %v", resp.Error)
		}
		toolRes := resp.Result.(domain.CallToolResult)
		var res domain.GetOTPResult
		if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &res); err != nil {
			t.Fatalf("unmarshal failed: %v", err)
		}
		if res.Status != "success" {
			t.Errorf("expected status success, got %s", res.Status)
		}
		if res.OTP != "839201" {
			t.Errorf("expected OTP 839201, got %s", res.OTP)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("get_otp timed out")
	}
}

func TestGetOTPCustomRegex(t *testing.T) {
	server, msgService, _ := setupTestServer(t)

	req := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      4,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "get_otp",
			"arguments": {
				"sender": "APP",
				"regex": "PIN:\\s*([A-Z0-9]{5})",
				"timeout": 5
			}
		}`),
	}

	done := make(chan *domain.RPCResponse, 1)
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		done <- server.HandleRequest(ctx, req)
	}()

	time.Sleep(50 * time.Millisecond)

	ctx := context.Background()
	_, _, err := msgService.Ingest(ctx, "test-device", service.IngestInput{
		MessageID:  "msg-otp-2",
		Sender:     "APP",
		Body:       "Your secret PIN: AB789 to login",
		ReceivedAt: time.Now().UnixMilli(),
	})
	if err != nil {
		t.Fatalf("ingest failed: %v", err)
	}

	select {
	case resp := <-done:
		toolRes := resp.Result.(domain.CallToolResult)
		var res domain.GetOTPResult
		if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &res); err != nil {
			t.Fatalf("unmarshal failed: %v", err)
		}
		if res.Status != "success" {
			t.Errorf("expected status success, got %s", res.Status)
		}
		if res.OTP != "AB789" {
			t.Errorf("expected OTP AB789, got %s", res.OTP)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("get_otp timed out")
	}
}

func TestGetOTPNoMatch(t *testing.T) {
	server, msgService, _ := setupTestServer(t)

	req := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      5,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "get_otp",
			"arguments": {
				"sender": "NEWS",
				"timeout": 5
			}
		}`),
	}

	done := make(chan *domain.RPCResponse, 1)
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		done <- server.HandleRequest(ctx, req)
	}()

	time.Sleep(50 * time.Millisecond)

	ctx := context.Background()
	_, _, err := msgService.Ingest(ctx, "test-device", service.IngestInput{
		MessageID:  "msg-otp-3",
		Sender:     "NEWS",
		Body:       "Weather today is sunny with clear skies.",
		ReceivedAt: time.Now().UnixMilli(),
	})
	if err != nil {
		t.Fatalf("ingest failed: %v", err)
	}

	select {
	case resp := <-done:
		toolRes := resp.Result.(domain.CallToolResult)
		var res domain.GetOTPResult
		if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &res); err != nil {
			t.Fatalf("unmarshal failed: %v", err)
		}
		if res.Status != "no_match" {
			t.Errorf("expected status no_match, got %s", res.Status)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("get_otp timed out")
	}
}

func TestGetLatestMessage(t *testing.T) {
	server, msgService, _ := setupTestServer(t)

	ctx := context.Background()
	_, _, _ = msgService.Ingest(ctx, "test-device", service.IngestInput{
		MessageID:  "msg-lat-1",
		Sender:     "BANK",
		Body:       "Bank alert",
		ReceivedAt: 1000,
	})
	_, _, _ = msgService.Ingest(ctx, "test-device", service.IngestInput{
		MessageID:  "msg-lat-2",
		Sender:     "SHOP",
		Body:       "Order shipped",
		ReceivedAt: 2000,
	})

	// Query latest across all senders
	req := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      10,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "get_latest_message",
			"arguments": {}
		}`),
	}

	resp := server.HandleRequest(ctx, req)
	toolRes := resp.Result.(domain.CallToolResult)
	var latest domain.Message
	if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &latest); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}
	if latest.Sender != "SHOP" {
		t.Errorf("expected latest sender SHOP, got %s", latest.Sender)
	}

	// Query latest filtered by BANK
	reqBank := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      11,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "get_latest_message",
			"arguments": {
				"sender": "BANK"
			}
		}`),
	}

	respBank := server.HandleRequest(ctx, reqBank)
	toolResBank := respBank.Result.(domain.CallToolResult)
	var latestBank domain.Message
	if err := json.Unmarshal([]byte(toolResBank.Content[0].Text), &latestBank); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}
	if latestBank.Sender != "BANK" {
		t.Errorf("expected latest sender BANK, got %s", latestBank.Sender)
	}
}

func TestGetMessagesPagination(t *testing.T) {
	server, msgService, _ := setupTestServer(t)

	ctx := context.Background()
	for i := 1; i <= 5; i++ {
		_, _, _ = msgService.Ingest(ctx, "test-device", service.IngestInput{
			MessageID:  fmt.Sprintf("msg-list-%d", i),
			Sender:     "ALERT",
			Body:       fmt.Sprintf("Alert number %d", i),
			ReceivedAt: int64(1000 * i),
		})
	}

	req := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      20,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "get_messages",
			"arguments": {
				"sender": "ALERT",
				"limit": 2
			}
		}`),
	}

	resp := server.HandleRequest(ctx, req)
	toolRes := resp.Result.(domain.CallToolResult)
	var result struct {
		Messages []domain.Message `json:"messages"`
		Total    int              `json:"total"`
		Limit    int              `json:"limit"`
	}
	if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &result); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}

	if len(result.Messages) != 2 {
		t.Errorf("expected 2 messages, got %d", len(result.Messages))
	}
	if result.Total != 5 {
		t.Errorf("expected total 5 messages, got %d", result.Total)
	}
}

func TestSearchMessages(t *testing.T) {
	server, msgService, _ := setupTestServer(t)

	ctx := context.Background()
	_, _, _ = msgService.Ingest(ctx, "test-device", service.IngestInput{
		MessageID:  "msg-search-1",
		Sender:     "AMAZON",
		Body:       "Your package has been delivered to your front door",
		ReceivedAt: 1000,
	})
	_, _, _ = msgService.Ingest(ctx, "test-device", service.IngestInput{
		MessageID:  "msg-search-2",
		Sender:     "UBER",
		Body:       "Your ride is arriving in 3 minutes",
		ReceivedAt: 2000,
	})

	req := &domain.RPCRequest{
		JSONRPC: "2.0",
		ID:      30,
		Method:  "tools/call",
		Params: json.RawMessage(`{
			"name": "search_messages",
			"arguments": {
				"query": "package"
			}
		}`),
	}

	resp := server.HandleRequest(ctx, req)
	toolRes := resp.Result.(domain.CallToolResult)
	var result struct {
		Messages []domain.Message `json:"messages"`
		Total    int              `json:"total"`
	}
	if err := json.Unmarshal([]byte(toolRes.Content[0].Text), &result); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}

	if len(result.Messages) != 1 {
		t.Fatalf("expected 1 search result, got %d", len(result.Messages))
	}
	if result.Messages[0].Sender != "AMAZON" {
		t.Errorf("expected sender AMAZON, got %s", result.Messages[0].Sender)
	}
}
