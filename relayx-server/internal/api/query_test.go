package api_test

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestMessageQueryingAndFiltering(t *testing.T) {
	server, token := setupTestServer(t)

	// Seed 3 messages: 2 from BANK, 1 from GOOGLE
	seedMessage := func(msgID, sender, body string, receivedAt time.Time) string {
		payload := map[string]any{
			"messageId":  msgID,
			"sender":     sender,
			"body":       body,
			"receivedAt": receivedAt.UnixMilli(),
		}
		b, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/messages", bytes.NewReader(b))
		req.Header.Set("Authorization", "Bearer "+token)
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)
		if rec.Code != http.StatusCreated {
			t.Fatalf("failed to seed message: %s", rec.Body.String())
		}
		var r map[string]any
		_ = json.NewDecoder(rec.Body).Decode(&r)
		return r["id"].(string)
	}

	t1 := time.Now().Add(-10 * time.Minute)
	t2 := time.Now().Add(-5 * time.Minute)
	t3 := time.Now()

	id1 := seedMessage("msg-1", "BANK", "Bank alert 1", t1)
	_ = seedMessage("msg-2", "GOOGLE", "Google code 2", t2)
	id3 := seedMessage("msg-3", "BANK", "Bank alert 3", t3)

	// 1. Query latest message -> Should be msg-3
	{
		req := httptest.NewRequest(http.MethodGet, "/api/v1/messages/latest", nil)
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200 for latest message, got %d", rec.Code)
		}
		var msg map[string]any
		_ = json.NewDecoder(rec.Body).Decode(&msg)
		if msg["id"] != id3 {
			t.Errorf("expected latest message to be id3 %s, got %v", id3, msg["id"])
		}
	}

	// 2. Query by ID -> id1
	{
		req := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/v1/messages/%s", id1), nil)
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200 for query by id, got %d", rec.Code)
		}
		var msg map[string]any
		_ = json.NewDecoder(rec.Body).Decode(&msg)
		if msg["sender"] != "BANK" {
			t.Errorf("expected sender BANK, got %v", msg["sender"])
		}
	}

	// 3. Filter by sender=BANK -> exactly 2 messages
	{
		req := httptest.NewRequest(http.MethodGet, "/api/v1/messages?sender=BANK", nil)
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200 for filter by sender, got %d", rec.Code)
		}
		var resp map[string]any
		_ = json.NewDecoder(rec.Body).Decode(&resp)

		total := int(resp["total"].(float64))
		if total != 2 {
			t.Errorf("expected total 2 for BANK, got %d", total)
		}
		items := resp["messages"].([]any)
		if len(items) != 2 {
			t.Errorf("expected 2 items returned, got %d", len(items))
		}
	}

	// 4. Pagination: limit=1, offset=0 -> 1 item returned, total=3
	{
		req := httptest.NewRequest(http.MethodGet, "/api/v1/messages?limit=1&offset=0", nil)
		rec := httptest.NewRecorder()
		server.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200 for pagination, got %d", rec.Code)
		}
		var resp map[string]any
		_ = json.NewDecoder(rec.Body).Decode(&resp)
		total := int(resp["total"].(float64))
		if total != 3 {
			t.Errorf("expected total 3, got %d", total)
		}
		items := resp["messages"].([]any)
		if len(items) != 1 {
			t.Errorf("expected 1 item returned, got %d", len(items))
		}
	}
}
