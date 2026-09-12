// RelayX Server Dashboard & Observability Client (Vanilla ES6)
document.addEventListener('DOMContentLoaded', () => {
  // State
  let currentTab = 'overview';
  let eventSource = null;
  let autoScroll = true;
  let currentLogs = [];
  let dbCurrentPage = 1;
  let dbTotalPages = 1;
  let dbSelectedTable = 'messages';
  let dbSortBy = 'created_at';
  let dbSortOrder = 'DESC';

  // Elements
  const statusDot = document.getElementById('statusDot');
  const statusText = document.getElementById('statusText');
  const logWindow = document.getElementById('logWindow');
  const logLevelFilter = document.getElementById('logLevelFilter');
  const logSearchInput = document.getElementById('logSearchInput');
  const btnPauseScroll = document.getElementById('btnPauseScroll');
  const btnClearLogs = document.getElementById('btnClearLogs');

  // Navigation
  document.querySelectorAll('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

      btn.classList.add('active');
      const tab = btn.dataset.tab;
      currentTab = tab;
      const targetPane = document.getElementById(`tab-${tab}`);
      if (targetPane) targetPane.classList.add('active');

      if (tab === 'overview') loadMetrics();
      if (tab === 'database') loadDatabaseTable(1);
      if (tab === 'devices') loadDevices();
    });
  });

  // --- SSE Log Streaming ---
  function connectSSE() {
    if (eventSource) eventSource.close();

    const minLevel = logLevelFilter.value;
    const search = encodeURIComponent(logSearchInput.value.trim());
    const url = `/api/v1/dashboard/logs/stream?level=${minLevel}&search=${search}`;

    eventSource = new EventSource(url);

    eventSource.onopen = () => {
      statusDot.classList.add('connected');
      statusText.textContent = 'Live Logcat Connected';
    };

    eventSource.onerror = () => {
      statusDot.classList.remove('connected');
      statusText.textContent = 'Disconnected (Reconnecting...)';
    };

    eventSource.onmessage = (e) => {
      try {
        const entry = JSON.parse(e.data);
        appendLogEntry(entry);
      } catch (err) {
        // Ignore parse errors on heartbeat comments
      }
    };
  }

  function appendLogEntry(entry) {
    currentLogs.push(entry);
    if (currentLogs.length > 1000) currentLogs.shift();

    const row = document.createElement('div');
    row.className = 'log-row';

    const timeStr = entry.timestamp ? new Date(entry.timestamp).toLocaleTimeString() : '';
    const levelClass = `level-${entry.level || 'INFO'}`;

    let attrsStr = '';
    if (entry.attributes && Object.keys(entry.attributes).length > 0) {
      attrsStr = Object.entries(entry.attributes)
        .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(v) : v}`)
        .join(' ');
    }

    row.innerHTML = `
      <span class="log-time">${timeStr}</span>
      <span class="log-level ${levelClass}">${entry.level}</span>
      <span class="log-component">[${entry.component || 'server'}]</span>
      <span class="log-msg">${escapeHtml(entry.message)}</span>
      <span class="log-attrs">${escapeHtml(attrsStr)}</span>
    `;

    logWindow.appendChild(row);

    if (autoScroll) {
      logWindow.scrollTop = logWindow.scrollHeight;
    }
  }

  btnPauseScroll.addEventListener('click', () => {
    autoScroll = !autoScroll;
    btnPauseScroll.textContent = autoScroll ? 'Pause Auto-Scroll' : 'Resume Auto-Scroll';
    if (autoScroll) logWindow.scrollTop = logWindow.scrollHeight;
  });

  btnClearLogs.addEventListener('click', () => {
    logWindow.innerHTML = '';
    currentLogs = [];
  });

  logLevelFilter.addEventListener('change', () => {
    logWindow.innerHTML = '';
    connectSSE();
  });

  let searchTimeout = null;
  logSearchInput.addEventListener('input', () => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
      logWindow.innerHTML = '';
      connectSSE();
    }, 300);
  });

  // --- Metrics ---
  function loadMetrics() {
    fetch('/api/v1/dashboard/metrics')
      .then(res => res.json())
      .then(data => {
        document.getElementById('valUptime').textContent = formatSeconds(data.uptime_seconds);
        document.getElementById('valVersion').textContent = `Version: ${data.version || '1.0.0'} (${data.go_version || ''})`;
        document.getElementById('valGoroutines').textContent = data.num_goroutine || 0;
        document.getElementById('valMemory').textContent = `Memory: ${(data.alloc_bytes / 1024 / 1024).toFixed(1)} MB`;

        const dbSizeMB = (data.database.db_size_bytes / 1024).toFixed(1);
        const walSizeKB = (data.database.wal_size_bytes / 1024).toFixed(1);
        document.getElementById('valDBSize').textContent = `${dbSizeMB} KB`;
        document.getElementById('valWALSize').textContent = `WAL: ${walSizeKB} KB`;

        const cnt = data.message_counters || {};
        const total = cnt.total_received || 0;
        const fwd = cnt.total_forwarded || 0;
        document.getElementById('cntReceived').textContent = total;
        document.getElementById('cntForwarded').textContent = fwd;
        document.getElementById('cntFiltered').textContent = cnt.total_filtered || 0;
        document.getElementById('cntFailed').textContent = cnt.total_failed || 0;

        const rate = total > 0 ? ((fwd / total) * 100).toFixed(1) : '100.0';
        document.getElementById('valSuccessRate').textContent = `${rate}%`;
      })
      .catch(err => console.error('Failed to load metrics:', err));
  }

  // --- Database Table Browser ---
  const dbTableSelect = document.getElementById('dbTableSelect');
  const dbSearchInput = document.getElementById('dbSearchInput');
  const dbStatusFilter = document.getElementById('dbStatusFilter');
  const dbTableHeader = document.getElementById('dbTableHeader');
  const dbTableBody = document.getElementById('dbTableBody');

  dbTableSelect.addEventListener('change', () => {
    dbSelectedTable = dbTableSelect.value;
    dbStatusFilter.style.display = dbSelectedTable === 'messages' ? 'inline-block' : 'none';
    dbSortBy = dbSelectedTable === 'messages' ? 'created_at' : '';
    dbCurrentPage = 1;
    loadDatabaseTable(1);
  });

  dbStatusFilter.addEventListener('change', () => loadDatabaseTable(1));
  document.getElementById('btnRefreshTable').addEventListener('click', () => loadDatabaseTable(dbCurrentPage));

  let dbSearchTimeout = null;
  dbSearchInput.addEventListener('input', () => {
    clearTimeout(dbSearchTimeout);
    dbSearchTimeout = setTimeout(() => loadDatabaseTable(1), 300);
  });

  document.getElementById('btnPrevPage').addEventListener('click', () => {
    if (dbCurrentPage > 1) loadDatabaseTable(dbCurrentPage - 1);
  });

  document.getElementById('btnNextPage').addEventListener('click', () => {
    if (dbCurrentPage < dbTotalPages) loadDatabaseTable(dbCurrentPage + 1);
  });

  function loadDatabaseTable(page) {
    dbCurrentPage = page;
    const status = dbStatusFilter.value;
    const search = encodeURIComponent(dbSearchInput.value.trim());
    const sortByParam = dbSortBy ? `&sort_by=${dbSortBy}&sort_order=${dbSortOrder}` : '';
    const statusParam = status ? `&status=${status}` : '';
    const searchParam = search ? `&search=${search}` : '';

    const url = `/api/v1/dashboard/database/tables/${dbSelectedTable}?page=${page}&page_size=25${sortByParam}${statusParam}${searchParam}`;

    fetch(url)
      .then(res => res.json())
      .then(data => {
        dbTotalPages = data.total_pages || 1;
        document.getElementById('dbCurrentPage').textContent = data.page;
        document.getElementById('dbTotalPages').textContent = dbTotalPages;
        document.getElementById('dbTotalCount').textContent = `Total: ${data.total_rows}`;

        renderTable(data.columns, data.rows);
      })
      .catch(err => {
        dbTableBody.innerHTML = `<tr><td colspan="10" style="color:var(--danger)">Error loading table: ${err.message}</td></tr>`;
      });
  }

  function renderTable(columns, rows) {
    if (!columns || columns.length === 0) {
      dbTableHeader.innerHTML = '';
      dbTableBody.innerHTML = '<tr><td>No columns found</td></tr>';
      return;
    }

    let headerHtml = '<tr>';
    columns.forEach(col => {
      headerHtml += `<th>${col}</th>`;
    });
    headerHtml += '</tr>';
    dbTableHeader.innerHTML = headerHtml;

    if (!rows || rows.length === 0) {
      dbTableBody.innerHTML = `<tr><td colspan="${columns.length}" style="text-align:center; color:var(--text-muted)">No records found</td></tr>`;
      return;
    }

    let bodyHtml = '';
    rows.forEach(row => {
      bodyHtml += '<tr>';
      columns.forEach(col => {
        const val = row[col];
        bodyHtml += `<td>${renderCell(col, val, row)}</td>`;
      });
      bodyHtml += '</tr>';
    });
    dbTableBody.innerHTML = bodyHtml;

    // Attach click listener for modal details
    dbTableBody.querySelectorAll('tr').forEach((tr, idx) => {
      tr.addEventListener('click', (e) => {
        // Prevent modal if user clicked eye reveal button
        if (e.target.closest('.eye-btn')) return;
        showRecordModal(rows[idx]);
      });
    });

    // Attach eye reveal toggle listeners
    dbTableBody.querySelectorAll('.eye-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const span = btn.previousElementSibling;
        const rawText = btn.dataset.raw;
        if (span.classList.contains('masked')) {
          span.textContent = rawText;
          span.classList.remove('masked');
          btn.textContent = '👁️';
          btn.title = 'Mask payload';
        } else {
          span.textContent = '••••••••••••';
          span.classList.add('masked');
          btn.textContent = '🔒';
          btn.title = 'Reveal payload';
        }
      });
    });
  }

  function renderCell(col, val, row) {
    if (val === null || val === undefined) return '<span style="color:var(--text-muted)">null</span>';

    if (col === 'status') {
      return `<span class="badge badge-${val}">${val}</span>`;
    }

    if (col === 'id' || col === 'message_id' || col === 'device_id') {
      const displayId = String(val).length > 16 ? String(val).substring(0, 16) + '...' : val;
      return `<span class="mono-cell" title="${escapeHtml(String(val))}">${escapeHtml(displayId)}</span>`;
    }

    if (col === 'body') {
      return `
        <span class="masked-preview masked">••••••••••••</span>
        <button class="eye-btn" data-raw="${escapeHtml(String(val))}" title="Reveal payload">🔒</button>
      `;
    }

    if (col.endsWith('_at')) {
      return `<span style="color:var(--text-secondary)">${new Date(val).toLocaleString()}</span>`;
    }

    return escapeHtml(String(val));
  }

  // --- Record Modal ---
  const recordModal = document.getElementById('recordModal');
  const modalJsonContent = document.getElementById('modalJsonContent');

  function showRecordModal(data) {
    modalJsonContent.textContent = JSON.stringify(data, null, 2);
    recordModal.classList.add('active');
  }

  document.getElementById('btnCloseModal').addEventListener('click', () => recordModal.classList.remove('active'));
  document.getElementById('btnCloseModalBtn').addEventListener('click', () => recordModal.classList.remove('active'));
  recordModal.addEventListener('click', (e) => {
    if (e.target === recordModal) recordModal.classList.remove('active');
  });

  // --- Devices View ---
  const deviceTableBody = document.getElementById('deviceTableBody');
  const registerDeviceModal = document.getElementById('registerDeviceModal');

  function loadDevices() {
    fetch('/api/v1/dashboard/devices')
      .then(res => res.json())
      .then(devices => {
        if (!devices || devices.length === 0) {
          deviceTableBody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted)">No registered devices</td></tr>';
          return;
        }

        let html = '';
        devices.forEach(d => {
          const registered = d.created_at ? new Date(d.created_at).toLocaleString() : '-';
          const lastSeen = d.last_seen_at ? new Date(d.last_seen_at).toLocaleString() : 'Never';
          html += `
            <tr>
              <td class="mono-cell">${escapeHtml(d.id)}</td>
              <td><strong>${escapeHtml(d.name)}</strong></td>
              <td class="mono-cell">${escapeHtml(d.token_fingerprint || '')}</td>
              <td style="color:var(--text-secondary)">${registered}</td>
              <td style="color:var(--text-secondary)">${lastSeen}</td>
              <td>
                <button class="btn btn-danger" onclick="revokeDevice('${d.id}', '${escapeHtml(d.name)}')">Revoke</button>
              </td>
            </tr>
          `;
        });
        deviceTableBody.innerHTML = html;
      })
      .catch(err => {
        deviceTableBody.innerHTML = `<tr><td colspan="6" style="color:var(--danger)">Error: ${err.message}</td></tr>`;
      });
  }

  window.revokeDevice = (id, name) => {
    if (!confirm(`Are you sure you want to revoke device "${name}" (${id})? It will no longer be able to ingest SMS messages.`)) {
      return;
    }

    fetch(`/api/v1/dashboard/devices/${id}`, { method: 'DELETE' })
      .then(res => res.json())
      .then(() => {
        alert(`Device "${name}" revoked.`);
        loadDevices();
      })
      .catch(err => alert(`Failed to revoke device: ${err.message}`));
  };

  document.getElementById('btnOpenRegisterDevice').addEventListener('click', () => {
    document.getElementById('registerForm').style.display = 'block';
    document.getElementById('registerResult').style.display = 'none';
    document.getElementById('newDeviceName').value = '';
    document.getElementById('btnSubmitRegister').style.display = 'inline-block';
    registerDeviceModal.classList.add('active');
  });

  document.getElementById('btnCloseRegisterModal').addEventListener('click', () => registerDeviceModal.classList.remove('active'));
  document.getElementById('btnCancelRegister').addEventListener('click', () => registerDeviceModal.classList.remove('active'));

  document.getElementById('btnSubmitRegister').addEventListener('click', () => {
    const name = document.getElementById('newDeviceName').value.trim();
    fetch('/api/v1/dashboard/devices', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name || 'Gateway Device' })
    })
      .then(res => res.json())
      .then(data => {
        document.getElementById('registerForm').style.display = 'none';
        document.getElementById('registerResult').style.display = 'block';
        document.getElementById('generatedTokenText').value = data.token;
        document.getElementById('btnSubmitRegister').style.display = 'none';
        loadDevices();
      })
      .catch(err => alert(`Failed to register device: ${err.message}`));
  });

  document.getElementById('btnCopyToken').addEventListener('click', () => {
    const token = document.getElementById('generatedTokenText').value;
    navigator.clipboard.writeText(token).then(() => {
      document.getElementById('btnCopyToken').textContent = 'Copied!';
      setTimeout(() => document.getElementById('btnCopyToken').textContent = 'Copy', 2000);
    });
  });

  // Helpers
  function formatSeconds(secs) {
    if (!secs) return '0s';
    const d = Math.floor(secs / 86400);
    const h = Math.floor((secs % 86400) / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = Math.floor(secs % 60);
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${m}m ${s}s`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // Periodic metrics refresh (every 5 seconds)
  setInterval(() => {
    if (currentTab === 'overview') loadMetrics();
  }, 5000);

  // Initial setup
  connectSSE();
  loadMetrics();
});
