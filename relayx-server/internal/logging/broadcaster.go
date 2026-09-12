package logging

import (
	"sync"

	"relayx-server/internal/domain"
)

// LogBroadcaster manages real-time broadcast of LogEntry items to subscriber channels
// and maintains a ring buffer of the most recent entries.
type LogBroadcaster struct {
	mu          sync.RWMutex
	subscribers map[chan domain.LogEntry]struct{}
	ringBuffer  []domain.LogEntry
	ringSize    int
	ringStart   int
	ringCount   int
}

// GlobalBroadcaster is the package-level broadcaster connected to the default logger.
var GlobalBroadcaster = NewLogBroadcaster(500)

// NewLogBroadcaster creates a LogBroadcaster with the specified ring buffer capacity.
func NewLogBroadcaster(bufferCapacity int) *LogBroadcaster {
	if bufferCapacity <= 0 {
		bufferCapacity = 200
	}
	return &LogBroadcaster{
		subscribers: make(map[chan domain.LogEntry]struct{}),
		ringBuffer:  make([]domain.LogEntry, bufferCapacity),
		ringSize:    bufferCapacity,
	}
}

// Subscribe registers a new subscriber channel with a buffer.
// It immediately returns the channel and a copy of the recent log entries in chronological order.
func (b *LogBroadcaster) Subscribe(bufferSize int) (chan domain.LogEntry, []domain.LogEntry) {
	if bufferSize <= 0 {
		bufferSize = 100
	}
	ch := make(chan domain.LogEntry, bufferSize)

	b.mu.Lock()
	defer b.mu.Unlock()

	b.subscribers[ch] = struct{}{}
	recent := b.getRecentLocked()
	return ch, recent
}

// Unsubscribe removes and closes a subscriber channel.
func (b *LogBroadcaster) Unsubscribe(ch chan domain.LogEntry) {
	b.mu.Lock()
	defer b.mu.Unlock()

	if _, ok := b.subscribers[ch]; ok {
		delete(b.subscribers, ch)
		close(ch)
	}
}

// SubscriberCount returns the current number of active subscribers.
func (b *LogBroadcaster) SubscriberCount() int {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return len(b.subscribers)
}

// Broadcast sends a LogEntry to all active subscribers without blocking
// and records it into the ring buffer.
func (b *LogBroadcaster) Broadcast(entry domain.LogEntry) {
	b.mu.Lock()
	defer b.mu.Unlock()

	// Append to ring buffer
	idx := (b.ringStart + b.ringCount) % b.ringSize
	b.ringBuffer[idx] = entry
	if b.ringCount < b.ringSize {
		b.ringCount++
	} else {
		b.ringStart = (b.ringStart + 1) % b.ringSize
	}

	// Fan out non-blockingly to all subscribers
	for ch := range b.subscribers {
		select {
		case ch <- entry:
		default:
			// Drop entry if channel buffer is full to avoid blocking server execution
		}
	}
}

func (b *LogBroadcaster) getRecentLocked() []domain.LogEntry {
	result := make([]domain.LogEntry, b.ringCount)
	for i := 0; i < b.ringCount; i++ {
		idx := (b.ringStart + i) % b.ringSize
		result[i] = b.ringBuffer[idx]
	}
	return result
}
