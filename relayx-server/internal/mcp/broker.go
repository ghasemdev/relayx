package mcp

import (
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"relayx-server/internal/domain"
)

// Subscription represents an active listener waiting for a matching message.
type Subscription struct {
	ID      string
	Sender  string
	After   time.Time
	ch      chan *domain.Message
	done    chan struct{}
	once    sync.Once
}

// Channel returns the receive-only message channel.
func (s *Subscription) Channel() <-chan *domain.Message {
	return s.ch
}

// Done returns a channel that signals when the subscription is closed.
func (s *Subscription) Done() <-chan struct{} {
	return s.done
}

func (s *Subscription) close() {
	s.once.Do(func() {
		close(s.done)
	})
}

// EventBroker coordinates message event distribution to waiting listeners.
type EventBroker interface {
	Subscribe(sender string, after time.Time) *Subscription
	Unsubscribe(sub *Subscription)
	Publish(msg *domain.Message)
	ActiveSubscribers() int
}

// MemoryBroker implements an in-memory pub/sub broker with Go channels.
type MemoryBroker struct {
	mu   sync.RWMutex
	subs map[string]*Subscription
}

// NewMemoryBroker creates an initialized MemoryBroker.
func NewMemoryBroker() *MemoryBroker {
	return &MemoryBroker{
		subs: make(map[string]*Subscription),
	}
}

// Subscribe registers a new message listener with optional sender and timestamp filters.
func (b *MemoryBroker) Subscribe(sender string, after time.Time) *Subscription {
	sub := &Subscription{
		ID:     uuid.NewString(),
		Sender: strings.TrimSpace(sender),
		After:  after,
		ch:     make(chan *domain.Message, 1),
		done:   make(chan struct{}),
	}

	b.mu.Lock()
	defer b.mu.Unlock()
	b.subs[sub.ID] = sub
	return sub
}

// Unsubscribe cleanly removes a listener and closes its done channel.
func (b *MemoryBroker) Unsubscribe(sub *Subscription) {
	if sub == nil {
		return
	}

	b.mu.Lock()
	defer b.mu.Unlock()
	if _, exists := b.subs[sub.ID]; exists {
		delete(b.subs, sub.ID)
		sub.close()
	}
}

// Publish distributes an ingested message to all matching subscribers.
func (b *MemoryBroker) Publish(msg *domain.Message) {
	if msg == nil {
		return
	}

	b.mu.RLock()
	defer b.mu.RUnlock()

	for _, sub := range b.subs {
		// Sender match: if filter specified, case-insensitive match
		if sub.Sender != "" && !strings.EqualFold(sub.Sender, msg.Sender) {
			continue
		}

		// Timestamp filter: message must have arrived after sub.After
		if !sub.After.IsZero() && !msg.ReceivedAt.After(sub.After) {
			continue
		}

		// Non-blocking send to avoid holding up the publisher
		select {
		case sub.ch <- msg:
		default:
			// Buffer full or listener already got a message
		}
	}
}

// ActiveSubscribers returns the current count of registered subscribers.
func (b *MemoryBroker) ActiveSubscribers() int {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return len(b.subs)
}
