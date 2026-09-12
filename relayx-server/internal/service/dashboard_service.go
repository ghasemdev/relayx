package service

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"fmt"
	"runtime"
	"time"

	"github.com/google/uuid"
	"relayx-server/internal/domain"
	"relayx-server/internal/storage"
)

// DashboardService coordinates observability, database browsing, and device management.
type DashboardService struct {
	db         *sql.DB
	deviceRepo domain.DeviceRepository
	dbPath     string
	startTime  time.Time
	version    string
}

// NewDashboardService creates a new DashboardService instance.
func NewDashboardService(db *sql.DB, deviceRepo domain.DeviceRepository, dbPath, version string) *DashboardService {
	return &DashboardService{
		db:         db,
		deviceRepo: deviceRepo,
		dbPath:     dbPath,
		startTime:  time.Now(),
		version:    version,
	}
}

// GetMetrics returns real-time process, memory, database, and throughput metrics.
func (s *DashboardService) GetMetrics(ctx context.Context) (*domain.SystemMetrics, error) {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	dbMetrics := storage.GetDatabaseStats(s.dbPath)
	msgCounters, err := storage.GetMessageCounts(ctx, s.db)
	if err != nil {
		msgCounters = domain.MessageCounters{}
	}

	return &domain.SystemMetrics{
		UptimeSeconds:   int64(time.Since(s.startTime).Seconds()),
		Version:         s.version,
		GoVersion:       runtime.Version(),
		NumGoroutine:    runtime.NumGoroutine(),
		AllocBytes:      m.Alloc,
		TotalAllocBytes: m.TotalAlloc,
		SysBytes:        m.Sys,
		Database:        dbMetrics,
		MessageCounters: msgCounters,
	}, nil
}

// ListTables returns metadata for all browsable SQLite tables.
func (s *DashboardService) ListTables(ctx context.Context) ([]domain.TableMetadata, error) {
	return storage.ListTables(ctx, s.db)
}

// QueryTable executes a paginated, sorted query against an allowed table.
func (s *DashboardService) QueryTable(ctx context.Context, name string, page, pageSize int, sortBy, sortOrder, status, sender, search string) (*domain.TablePage, error) {
	return storage.QueryTable(ctx, s.db, name, page, pageSize, sortBy, sortOrder, status, sender, search)
}

// ListDevices returns summaries of all registered gateway devices.
func (s *DashboardService) ListDevices(ctx context.Context) ([]domain.DeviceSummary, error) {
	devices, err := s.deviceRepo.List(ctx)
	if err != nil {
		return nil, fmt.Errorf("listing devices: %w", err)
	}

	summaries := make([]domain.DeviceSummary, len(devices))
	for i, d := range devices {
		fp := ""
		if len(d.TokenHash) >= 8 {
			fp = d.TokenHash[:8] + "..."
		}
		summaries[i] = domain.DeviceSummary{
			ID:               d.ID,
			Name:             d.Name,
			TokenFingerprint: fp,
			CreatedAt:        d.CreatedAt,
			LastSeenAt:       d.LastSeenAt,
		}
	}
	return summaries, nil
}

// RegisterDevice creates a new device and returns its summary along with the plaintext token.
func (s *DashboardService) RegisterDevice(ctx context.Context, name string) (*domain.DeviceSummary, string, error) {
	if name == "" {
		name = "Gateway Device"
	}

	rawToken := "rx-" + uuid.NewString()
	hash := sha256.Sum256([]byte(rawToken))
	tokenHash := hex.EncodeToString(hash[:])

	dev := &domain.Device{
		ID:        "dev_" + uuid.NewString(),
		Name:      name,
		TokenHash: tokenHash,
		CreatedAt: time.Now().UTC(),
	}

	if err := s.deviceRepo.Upsert(ctx, dev); err != nil {
		return nil, "", fmt.Errorf("persisting device: %w", err)
	}

	fp := ""
	if len(tokenHash) >= 8 {
		fp = tokenHash[:8] + "..."
	}

	summary := &domain.DeviceSummary{
		ID:               dev.ID,
		Name:             dev.Name,
		TokenFingerprint: fp,
		CreatedAt:        dev.CreatedAt,
	}

	return summary, rawToken, nil
}

// RevokeDevice deletes a device credential from storage.
func (s *DashboardService) RevokeDevice(ctx context.Context, id string) error {
	return s.deviceRepo.Delete(ctx, id)
}
