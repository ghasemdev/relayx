package logging_test

import (
	"fmt"
	"testing"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/logging"
)

func TestLogBroadcaster_RingBuffer(t *testing.T) {
	b := logging.NewLogBroadcaster(3)

	// Send 4 entries to verify ring overflow
	for i := 1; i <= 4; i++ {
		b.Broadcast(domain.LogEntry{
			Timestamp: time.Now(),
			Level:     "INFO",
			Component: "test",
			Message:   fmt.Sprintf("msg-%d", i),
		})
	}

	ch, recent := b.Subscribe(10)
	defer b.Unsubscribe(ch)

	if len(recent) != 3 {
		t.Fatalf("expected 3 recent entries, got %d", len(recent))
	}

	// Should contain msg-2, msg-3, msg-4
	if recent[0].Message != "msg-2" || recent[1].Message != "msg-3" || recent[2].Message != "msg-4" {
		t.Errorf("unexpected recent entries: %+v", recent)
	}
}

func TestLogBroadcaster_SubscribeBroadcast(t *testing.T) {
	b := logging.NewLogBroadcaster(10)

	ch, recent := b.Subscribe(10)
	defer b.Unsubscribe(ch)

	if len(recent) != 0 {
		t.Fatalf("expected 0 recent entries initially, got %d", len(recent))
	}

	testEntry := domain.LogEntry{
		Timestamp: time.Now(),
		Level:     "WARN",
		Component: "api",
		Message:   "rate limit approaching",
	}

	b.Broadcast(testEntry)

	select {
	case received := <-ch:
		if received.Message != testEntry.Message {
			t.Errorf("expected message %s, got %s", testEntry.Message, received.Message)
		}
		if received.Level != testEntry.Level {
			t.Errorf("expected level %s, got %s", testEntry.Level, received.Level)
		}
	case <-time.After(500 * time.Millisecond):
		t.Fatal("timed out waiting for broadcast event")
	}
}

func TestLogBroadcaster_NonBlockingSlowConsumer(t *testing.T) {
	b := logging.NewLogBroadcaster(10)

	// Small channel buffer of 1
	slowCh, _ := b.Subscribe(1)
	defer b.Unsubscribe(slowCh)

	// Fast channel buffer of 10
	fastCh, _ := b.Subscribe(10)
	defer b.Unsubscribe(fastCh)

	// Send 5 entries without reading from slowCh
	for i := 1; i <= 5; i++ {
		b.Broadcast(domain.LogEntry{
			Timestamp: time.Now(),
			Level:     "INFO",
			Component: "test",
			Message:   fmt.Sprintf("fast-%d", i),
		})
	}

	// slowCh should have 1 entry (buffer full, others dropped)
	if len(slowCh) != 1 {
		t.Errorf("expected slowCh to buffer exactly 1 entry, got %d", len(slowCh))
	}

	// fastCh should have received all 5 entries
	if len(fastCh) != 5 {
		t.Errorf("expected fastCh to receive all 5 entries, got %d", len(fastCh))
	}
}
