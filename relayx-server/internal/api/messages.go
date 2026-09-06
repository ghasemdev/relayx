package api

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"relayx-server/internal/domain"
	"relayx-server/internal/service"
)

type MessageHandler struct {
	msgService *service.MessageService
}

func NewMessageHandler(msgService *service.MessageService) *MessageHandler {
	return &MessageHandler{msgService: msgService}
}

// IngestMessage handles POST /api/v1/messages.
func (h *MessageHandler) IngestMessage(w http.ResponseWriter, r *http.Request) {
	device := DeviceFromContext(r.Context())
	if device == nil {
		writeJSONError(w, http.StatusUnauthorized, "unauthorized device")
		return
	}

	// Limit request body to 1 MB to prevent memory exhaustion DoS (TASK-SEC-001)
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)

	var input service.IngestInput
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		var maxBytesErr *http.MaxBytesError
		if errors.As(err, &maxBytesErr) {
			writeJSONError(w, http.StatusRequestEntityTooLarge, "request body exceeds 1 MB limit")
			return
		}
		writeJSONError(w, http.StatusBadRequest, "malformed JSON request body")
		return
	}

	msg, isCreated, err := h.msgService.Ingest(r.Context(), device.ID, input)
	if err != nil {
		if errors.Is(err, service.ErrInvalidMessage) {
			writeJSONError(w, http.StatusBadRequest, err.Error())
			return
		}
		writeJSONError(w, http.StatusInternalServerError, "failed to ingest message")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	if isCreated {
		w.WriteHeader(http.StatusCreated)
	} else {
		w.WriteHeader(http.StatusOK) // Deduplicated / Idempotent retry
	}

	_ = json.NewEncoder(w).Encode(map[string]any{
		"id":        msg.ID,
		"messageId": msg.MessageID,
		"status":    string(msg.Status),
	})
}

// ListMessages handles GET /api/v1/messages with optional sender, status, limit, offset query params.
func (h *MessageHandler) ListMessages(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	sender := q.Get("sender")
	statusStr := q.Get("status")

	limit := 50
	if l := q.Get("limit"); l != "" {
		if val, err := strconv.Atoi(l); err == nil && val > 0 {
			limit = val
		}
	}

	offset := 0
	if o := q.Get("offset"); o != "" {
		if val, err := strconv.Atoi(o); err == nil && val >= 0 {
			offset = val
		}
	}

	filter := domain.MessageFilter{
		Sender: sender,
		Status: domain.MessageStatus(statusStr),
		Limit:  limit,
		Offset: offset,
	}

	messages, total, err := h.msgService.List(r.Context(), filter)
	if err != nil {
		writeJSONError(w, http.StatusInternalServerError, "failed to query messages")
		return
	}

	if messages == nil {
		messages = []domain.Message{}
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"messages": messages,
		"total":    total,
		"limit":    limit,
		"offset":   offset,
	})
}

// GetLatestMessage handles GET /api/v1/messages/latest.
func (h *MessageHandler) GetLatestMessage(w http.ResponseWriter, r *http.Request) {
	msg, err := h.msgService.GetLatest(r.Context())
	if err != nil {
		writeJSONError(w, http.StatusInternalServerError, "failed to retrieve latest message")
		return
	}

	if msg == nil {
		writeJSONError(w, http.StatusNotFound, "no messages found")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(msg)
}

// GetMessageByID handles GET /api/v1/messages/{id}.
func (h *MessageHandler) GetMessageByID(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		writeJSONError(w, http.StatusBadRequest, "message id required")
		return
	}

	msg, err := h.msgService.GetByID(r.Context(), id)
	if err != nil {
		writeJSONError(w, http.StatusInternalServerError, "failed to retrieve message")
		return
	}

	if msg == nil {
		writeJSONError(w, http.StatusNotFound, "message not found")
		return
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(msg)
}
