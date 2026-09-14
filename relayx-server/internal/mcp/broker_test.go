package mcp_test

import (
	"sync"
	"testing"
	"time"

	"relayx-server/internal/domain"
	"relayx-server/internal/mcp"
)

func TestBrokerSubscribeAndPublish(t *testing.T) {
	broker := mcp.NewMemoryBroker()

	now := time.Now().UTC()
	subBank := broker.Subscribe("BANK", now.Add(-1*time.Minute))
	defer broker.Unsubscribe(subBank)

	subAny := broker.Subscribe("", now.Add(-1*time.Minute))
	defer broker.Unsubscribe(subAny)

	subGoogle := broker.Subscribe("GOOGLE", now.Add(-1*time.Minute))
	defer broker.Unsubscribe(subGoogle)

	if broker.ActiveSubscribers() != 3 {
		t.Fatalf("expected 3 active subscribers, got %d", broker.ActiveSubscribers())
	}

	msgBank := &domain.Message{
		ID:         "msg-001",
		Sender:     "BANK",
		Body:       "Code: 123456",
		ReceivedAt: now,
	}

	broker.Publish(msgBank)

	// subBank should receive
	select {
	case received := <-subBank.Channel():
		if received.ID != "msg-001" {
			t.Errorf("expected msg-001, got %s", received.ID)
		}
	case <-time.After(100 * time.Millisecond):
		t.Fatal("subBank timed out waiting for message")
	}

	// subAny should receive
	select {
	case received := <-subAny.Channel():
		if received.ID != "msg-001" {
			t.Errorf("expected msg-001, got %s", received.ID)
		}
	case <-time.After(100 * time.Millisecond):
		t.Fatal("subAny timed out waiting for message")
	}

	// subGoogle should NOT receive
	select {
	case <-subGoogle.Channel():
		t.Fatal("subGoogle unexpectedly received bank message")
	default:
		// OK
	}

	// Unsubscribe and check active count
	broker.Unsubscribe(subBank)
	if broker.ActiveSubscribers() != 2 {
		t.Errorf("expected 2 active subscribers after unsubscribe, got %d", broker.ActiveSubscribers())
	}
}

func TestBrokerTimestampFiltering(t *testing.T) {
	broker := mcp.NewMemoryBroker()

	t1 := time.Now().UTC()
	t2 := t1.Add(5 * time.Second)

	// sub requires message after t2
	subFuture := broker.Subscribe("TEST", t2)
	defer broker.Unsubscribe(subFuture)

	// sub requires message after t1 - 10s
	subPast := broker.Subscribe("TEST", t1.Add(-10*time.Second))
	defer broker.Unsubscribe(subPast)

	msgPast := &domain.Message{
		ID:         "msg-old",
		Sender:     "TEST",
		Body:       "Old",
		ReceivedAt: t1,
	}

	broker.Publish(msgPast)

	// subPast should receive
	select {
	case <-subPast.Channel():
		// OK
	case <-time.After(100 * time.Millisecond):
		t.Fatal("subPast timed out")
	}

	// subFuture should NOT receive
	select {
	case <-subFuture.Channel():
		t.Fatal("subFuture should not have received old message")
	default:
		// OK
	}
}

func TestBrokerConcurrentPublishAndSubscribe(t *testing.T) {
	broker := mcp.NewMemoryBroker()
	var wg sync.WaitGroup

	numSubscribers := 25
	numPublishers := 10

	for i := 0; i < numSubscribers; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			sub := broker.Subscribe("CHASE", time.Time{})
			defer broker.Unsubscribe(sub)
			time.Sleep(10 * time.Millisecond)
		}(i)
	}

	for i := 0; i < numPublishers; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			broker.Publish(&domain.Message{
				ID:         "concurrent-msg",
				Sender:     "CHASE",
				Body:       "Test",
				ReceivedAt: time.Now().UTC(),
			})
		}(i)
	}

	wg.Wait()
}
