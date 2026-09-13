// RelayX Server Dashboard & Observability Client (Modern ES6)
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
  let totalLogsReceived = 0;

  // DOM Elements
  const statusDot = document.getElementById('statusDot');
  const statusText = document.getElementById('statusText');
  const logWindow = document.getElementById('logWindow');
  const logLevelFilter = document.getElementById('logLevelFilter');
  const logSearchInput = document.getElementById('logSearchInput');
  const btnPauseScroll = document.getElementById('btnPauseScroll');
  const btnClearLogs = document.getElementById('btnClearLogs');
  const logEmptyState = document.getElementById('logEmptyState');
  const logStreamCounter = document.getElementById('logStreamCounter');
  const streamThroughputMeta = document.getElementById('streamThroughputMeta');
  const btnHeaderRefresh = document.getElementById('btnHeaderRefresh');
  const toastContainer = document.getElementById('toastContainer');

  // --- Toast Notification System ---
  function showToast(message, type = 'info', duration = 3000) {
    if (!toastContainer) return;
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    let iconSvg = '';
    if (type === 'success') {
      iconSvg = '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 6L9 17l-5-5"></path></svg>';
    } else if (type === 'error') {
      iconSvg = '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>';
    } else if (type === 'warning') {
      iconSvg = '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path></svg>';
    } else {
      iconSvg = '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>';
    }

    toast.innerHTML = `${iconSvg}<span>${escapeHtml(message)}</span>`;
    toastContainer.appendChild(toast);

    setTimeout(() => {
      toast.classList.add('toast-exit');
      setTimeout(() => toast.remove(), 250);
    }, duration);
  }

  // --- Tab Navigation ---
  function switchTab(tab) {
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

    const activeBtn = document.querySelector(`.nav-btn[data-tab="${tab}"]`);
    if (activeBtn) activeBtn.classList.add('active');

    currentTab = tab;
    const targetPane = document.getElementById(`tab-${tab}`);
    if (targetPane) targetPane.classList.add('active');

    if (tab === 'overview') loadMetrics();
    if (tab === 'database') loadDatabaseTable(1);
    if (tab === 'devices') loadDevices();
  }

  document.querySelectorAll('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });

  // Shortcuts on Overview Page
  const shortcutLiveLogcat = document.getElementById('shortcutLiveLogcat');
  if (shortcutLiveLogcat) shortcutLiveLogcat.addEventListener('click', () => switchTab('logcat'));

  const shortcutBrowseDB = document.getElementById('shortcutBrowseDB');
  if (shortcutBrowseDB) shortcutBrowseDB.addEventListener('click', () => switchTab('database'));

  const shortcutManageDevices = document.getElementById('shortcutManageDevices');
  if (shortcutManageDevices) shortcutManageDevices.addEventListener('click', () => switchTab('devices'));

  const btnViewAllMessages = document.getElementById('btnViewAllMessages');
  if (btnViewAllMessages) {
    btnViewAllMessages.addEventListener('click', () => {
      switchTab('database');
      if (dbTableSelect) {
        dbTableSelect.value = 'messages';
        dbSelectedTable = 'messages';
        loadDatabaseTable(1);
      }
    });
  }

  if (btnHeaderRefresh) {
    btnHeaderRefresh.addEventListener('click', () => {
      if (currentTab === 'overview') loadMetrics();
      else if (currentTab === 'database') loadDatabaseTable(dbCurrentPage);
      else if (currentTab === 'devices') loadDevices();
      showToast('Dashboard data refreshed', 'info', 1800);
    });
  }

  // --- Keyboard Shortcuts ---
  window.addEventListener('keydown', (e) => {
    const activeEl = document.activeElement;
    const isInput = activeEl && (activeEl.tagName === 'INPUT' || activeEl.tagName === 'SELECT' || activeEl.tagName === 'TEXTAREA');

    // Escape closes active modals
    if (e.key === 'Escape') {
      const activeModal = document.querySelector('.modal-backdrop.active');
      if (activeModal) {
        activeModal.classList.remove('active');
        return;
      }
    }

    if (!isInput) {
      if (e.key === '1') switchTab('overview');
      if (e.key === '2') switchTab('logcat');
      if (e.key === '3') switchTab('database');
      if (e.key === '4') switchTab('devices');

      if (e.key === '/') {
        e.preventDefault();
        if (currentTab === 'logcat' && logSearchInput) logSearchInput.focus();
        else if (currentTab === 'database' && dbSearchInput) dbSearchInput.focus();
      }
    }
  });

  // --- SSE Log Streaming ---
  function connectSSE() {
    if (eventSource) eventSource.close();

    const minLevel = logLevelFilter ? logLevelFilter.value : 'INFO';
    const search = logSearchInput ? encodeURIComponent(logSearchInput.value.trim()) : '';
    const url = `/api/v1/dashboard/logs/stream?level=${minLevel}&search=${search}`;

    eventSource = new EventSource(url);

    eventSource.onopen = () => {
      if (statusDot) statusDot.classList.add('connected');
      if (statusText) statusText.textContent = 'Live Logcat Connected';
    };

    eventSource.onerror = () => {
      if (statusDot) statusDot.classList.remove('connected');
      if (statusText) statusText.textContent = 'Disconnected (Reconnecting...)';
    };

    eventSource.onmessage = (e) => {
      try {
        const entry = JSON.parse(e.data);
        appendLogEntry(entry);
      } catch (err) {
        // Ignore heartbeat comments
      }
    };
  }

  function appendLogEntry(entry) {
    if (logEmptyState) logEmptyState.style.display = 'none';

    currentLogs.push(entry);
    totalLogsReceived++;
    if (currentLogs.length > 1000) currentLogs.shift();

    if (logStreamCounter) logStreamCounter.textContent = `${totalLogsReceived} logs`;
    if (streamThroughputMeta) streamThroughputMeta.textContent = `Buffer: ${currentLogs.length} / 1000`;

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
      <span class="log-level ${levelClass}">${entry.level || 'INFO'}</span>
      <span class="log-component">[${escapeHtml(entry.component || 'server')}]</span>
      <span class="log-msg">${escapeHtml(entry.message || '')}</span>
      <span class="log-attrs">${escapeHtml(attrsStr)}</span>
    `;

    logWindow.appendChild(row);

    if (autoScroll) {
      logWindow.scrollTop = logWindow.scrollHeight;
    }
  }

  if (btnPauseScroll) {
    btnPauseScroll.addEventListener('click', () => {
      autoScroll = !autoScroll;
      const span = btnPauseScroll.querySelector('span');
      if (span) span.textContent = autoScroll ? 'Pause Auto-Scroll' : 'Resume Auto-Scroll';
      if (autoScroll) logWindow.scrollTop = logWindow.scrollHeight;
      showToast(autoScroll ? 'Auto-scroll enabled' : 'Auto-scroll paused', 'info', 1500);
    });
  }

  if (btnClearLogs) {
    btnClearLogs.addEventListener('click', () => {
      logWindow.innerHTML = '';
      if (logEmptyState) {
        logWindow.appendChild(logEmptyState);
        logEmptyState.style.display = 'flex';
      }
      currentLogs = [];
      if (streamThroughputMeta) streamThroughputMeta.textContent = 'Buffer: 0 / 1000';
      showToast('Live log window cleared', 'info', 1500);
    });
  }

  if (logLevelFilter) {
    logLevelFilter.addEventListener('change', () => {
      logWindow.innerHTML = '';
      currentLogs = [];
      connectSSE();
    });
  }

  let searchTimeout = null;
  if (logSearchInput) {
    logSearchInput.addEventListener('input', () => {
      clearTimeout(searchTimeout);
      searchTimeout = setTimeout(() => {
        logWindow.innerHTML = '';
        currentLogs = [];
        connectSSE();
      }, 300);
    });
  }

  // --- Metrics & Telemetry ---
  function loadMetrics() {
    fetch('/api/v1/dashboard/metrics')
      .then(res => res.json())
      .then(data => {
        const uptimeEl = document.getElementById('valUptime');
        if (uptimeEl) uptimeEl.textContent = formatSeconds(data.uptime_seconds);

        const versionEl = document.getElementById('valVersion');
        if (versionEl) versionEl.textContent = `v${data.version || '1.0.0'} (${data.go_version || 'Go'})`;

        const goroutinesEl = document.getElementById('valGoroutines');
        if (goroutinesEl) goroutinesEl.textContent = data.num_goroutine || 0;

        const memoryEl = document.getElementById('valMemory');
        if (memoryEl) memoryEl.textContent = `Heap: ${(data.alloc_bytes / 1024 / 1024).toFixed(1)} MB`;

        const dbSizeKB = ((data.database?.db_size_bytes || 0) / 1024).toFixed(1);
        const walSizeKB = ((data.database?.wal_size_bytes || 0) / 1024).toFixed(1);

        const dbSizeEl = document.getElementById('valDBSize');
        if (dbSizeEl) dbSizeEl.textContent = `${dbSizeKB} KB`;

        const walSizeEl = document.getElementById('valWALSize');
        if (walSizeEl) walSizeEl.textContent = `WAL: ${walSizeKB} KB`;

        const cnt = data.message_counters || {};
        const total = cnt.total_received || 0;
        const fwd = cnt.total_forwarded || 0;
        const flt = cnt.total_filtered || 0;
        const fld = cnt.total_failed || 0;

        const cntReceived = document.getElementById('cntReceived');
        if (cntReceived) cntReceived.textContent = total;

        const cntForwarded = document.getElementById('cntForwarded');
        if (cntForwarded) cntForwarded.textContent = fwd;

        const cntFiltered = document.getElementById('cntFiltered');
        if (cntFiltered) cntFiltered.textContent = flt;

        const cntFailed = document.getElementById('cntFailed');
        if (cntFailed) cntFailed.textContent = fld;

        const rate = total > 0 ? ((fwd / total) * 100).toFixed(1) : '100.0';
        const successRateEl = document.getElementById('valSuccessRate');
        if (successRateEl) successRateEl.textContent = `${rate}%`;

        // Update visual pipeline distribution bar
        if (total > 0) {
          const pctFwd = Math.round((fwd / total) * 100);
          const pctFlt = Math.round((flt / total) * 100);
          const pctFld = Math.round((fld / total) * 100);

          const barFwd = document.getElementById('barForwarded');
          const barFlt = document.getElementById('barFiltered');
          const barFld = document.getElementById('barFailed');

          if (barFwd) barFwd.style.width = `${pctFwd}%`;
          if (barFlt) barFlt.style.width = `${pctFlt}%`;
          if (barFld) barFld.style.width = `${pctFld}%`;

          const subFwd = document.getElementById('pctForwarded');
          if (subFwd) subFwd.textContent = `${pctFwd}% forwarded to webhooks`;

          const subFlt = document.getElementById('pctFiltered');
          if (subFlt) subFlt.textContent = `${pctFlt}% dropped by filters`;

          const subFld = document.getElementById('pctFailed');
          if (subFld) subFld.textContent = `${pctFld}% dispatch failures`;
        }
      })
      .catch(err => console.error('Failed to load metrics:', err));
  }

  // --- Database Table Browser ---
  const dbTableSelect = document.getElementById('dbTableSelect');
  const dbSearchInput = document.getElementById('dbSearchInput');
  const dbStatusFilter = document.getElementById('dbStatusFilter');
  const dbSortBySelect = document.getElementById('dbSortBySelect');
  const dbSortOrderSelect = document.getElementById('dbSortOrderSelect');
  const dbTableHeader = document.getElementById('dbTableHeader');
  const dbTableBody = document.getElementById('dbTableBody');

  if (dbTableSelect) {
    dbTableSelect.addEventListener('change', () => {
      dbSelectedTable = dbTableSelect.value;
      if (dbStatusFilter) {
        dbStatusFilter.style.display = dbSelectedTable === 'messages' ? 'inline-block' : 'none';
      }
      dbSortBy = dbSelectedTable === 'messages' ? 'created_at' : '';
      dbSortOrder = 'DESC';
      if (dbSortOrderSelect) dbSortOrderSelect.value = dbSortOrder;
      dbCurrentPage = 1;
      loadDatabaseTable(1);
    });
  }

  if (dbStatusFilter) {
    dbStatusFilter.addEventListener('change', () => loadDatabaseTable(1));
  }

  const btnRefreshTable = document.getElementById('btnRefreshTable');
  if (btnRefreshTable) {
    btnRefreshTable.addEventListener('click', () => {
      loadDatabaseTable(dbCurrentPage);
      showToast('Table refreshed', 'info', 1500);
    });
  }

  if (dbSortBySelect) {
    dbSortBySelect.addEventListener('change', () => {
      dbSortBy = dbSortBySelect.value;
      loadDatabaseTable(1);
    });
  }

  if (dbSortOrderSelect) {
    dbSortOrderSelect.addEventListener('change', () => {
      dbSortOrder = dbSortOrderSelect.value;
      loadDatabaseTable(1);
    });
  }

  let dbSearchTimeout = null;
  if (dbSearchInput) {
    dbSearchInput.addEventListener('input', () => {
      clearTimeout(dbSearchTimeout);
      dbSearchTimeout = setTimeout(() => loadDatabaseTable(1), 300);
    });
  }

  const btnPrevPage = document.getElementById('btnPrevPage');
  if (btnPrevPage) {
    btnPrevPage.addEventListener('click', () => {
      if (dbCurrentPage > 1) loadDatabaseTable(dbCurrentPage - 1);
    });
  }

  const btnNextPage = document.getElementById('btnNextPage');
  if (btnNextPage) {
    btnNextPage.addEventListener('click', () => {
      if (dbCurrentPage < dbTotalPages) loadDatabaseTable(dbCurrentPage + 1);
    });
  }

  function updateSortSelects(columns) {
    if (!dbSortBySelect || !columns || columns.length === 0) return;
    const existingOptions = Array.from(dbSortBySelect.options).map(o => o.value);
    const same = existingOptions.length === columns.length && existingOptions.every((v, i) => v === columns[i]);
    if (!same) {
      dbSortBySelect.innerHTML = columns.map(c => `<option value="${c}">${c}</option>`).join('');
    }
    if (dbSortBy && columns.includes(dbSortBy)) {
      dbSortBySelect.value = dbSortBy;
    } else if (columns.length > 0) {
      dbSortBy = columns[0];
      dbSortBySelect.value = dbSortBy;
    }
    if (dbSortOrderSelect) {
      dbSortOrderSelect.value = dbSortOrder;
    }
  }

  function loadDatabaseTable(page) {
    dbCurrentPage = page;
    const status = dbStatusFilter ? dbStatusFilter.value : '';
    const search = dbSearchInput ? encodeURIComponent(dbSearchInput.value.trim()) : '';
    const sortByParam = dbSortBy ? `&sort_by=${dbSortBy}&sort_order=${dbSortOrder}` : '';
    const statusParam = status ? `&status=${status}` : '';
    const searchParam = search ? `&search=${search}` : '';

    const url = `/api/v1/dashboard/database/tables/${dbSelectedTable}?page=${page}&page_size=25${sortByParam}${statusParam}${searchParam}`;

    fetch(url)
      .then(res => res.json())
      .then(data => {
        dbTotalPages = data.total_pages || 1;
        const curPageEl = document.getElementById('dbCurrentPage');
        const totPageEl = document.getElementById('dbTotalPages');
        const totCountEl = document.getElementById('dbTotalCount');

        if (curPageEl) curPageEl.textContent = data.page;
        if (totPageEl) totPageEl.textContent = dbTotalPages;
        if (totCountEl) totCountEl.textContent = `Total: ${data.total_rows}`;

        if (data.sort_by) dbSortBy = data.sort_by;
        if (data.sort_order) dbSortOrder = data.sort_order;

        updateSortSelects(data.columns);
        renderTable(data.columns, data.rows);
      })
      .catch(err => {
        if (dbTableBody) {
          dbTableBody.innerHTML = `<tr><td colspan="10" style="color:var(--danger); padding: 1.5rem; text-align: center">Error loading table: ${escapeHtml(err.message)}</td></tr>`;
        }
      });
  }

  function renderTable(columns, rows) {
    if (!columns || columns.length === 0) {
      if (dbTableHeader) dbTableHeader.innerHTML = '';
      if (dbTableBody) dbTableBody.innerHTML = '<tr><td style="padding: 1.5rem; text-align:center; color:var(--text-muted)">No columns found</td></tr>';
      return;
    }

    let headerHtml = '<tr>';
    columns.forEach(col => {
      const isSorted = dbSortBy === col;
      const sortClass = isSorted ? `sortable sorted-${dbSortOrder.toLowerCase()}` : 'sortable';
      let icon = '<span class="sort-icon sort-icon-idle">&#8645;</span>';
      let title = `Click to sort by ${col} (descending)`;
      if (isSorted) {
        if (dbSortOrder === 'ASC') {
          icon = '<span class="sort-icon">&uarr;</span>';
          title = `Sorted ascending. Click to sort descending.`;
        } else {
          icon = '<span class="sort-icon">&darr;</span>';
          title = `Sorted descending. Click to sort ascending.`;
        }
      }
      headerHtml += `<th class="${sortClass}" data-col="${col}" title="${title}">${col} ${icon}</th>`;
    });
    headerHtml += '</tr>';
    dbTableHeader.innerHTML = headerHtml;

    // Attach click listeners to sortable column headers
    dbTableHeader.querySelectorAll('th.sortable').forEach(th => {
      th.addEventListener('click', () => {
        const col = th.dataset.col;
        if (dbSortBy === col) {
          dbSortOrder = dbSortOrder === 'DESC' ? 'ASC' : 'DESC';
        } else {
          dbSortBy = col;
          dbSortOrder = 'DESC';
        }
        if (dbSortOrderSelect) dbSortOrderSelect.value = dbSortOrder;
        if (dbSortBySelect) dbSortBySelect.value = dbSortBy;
        loadDatabaseTable(1);
      });
    });

    if (!rows || rows.length === 0) {
      dbTableBody.innerHTML = `<tr><td colspan="${columns.length}" style="text-align:center; padding: 2rem; color:var(--text-muted)">No records found matching criteria</td></tr>`;
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
    if (val === null || val === undefined) return '<span style="color:var(--text-dim)">null</span>';

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
  const btnCopyRecordJson = document.getElementById('btnCopyRecordJson');

  function showRecordModal(data) {
    if (!recordModal || !modalJsonContent) return;
    const jsonStr = JSON.stringify(data, null, 2);
    modalJsonContent.textContent = jsonStr;
    recordModal.classList.add('active');
  }

  if (btnCopyRecordJson) {
    btnCopyRecordJson.addEventListener('click', () => {
      if (modalJsonContent) {
        navigator.clipboard.writeText(modalJsonContent.textContent).then(() => {
          showToast('JSON payload copied to clipboard', 'success', 2000);
        });
      }
    });
  }

  const btnCloseModal = document.getElementById('btnCloseModal');
  if (btnCloseModal) btnCloseModal.addEventListener('click', () => recordModal.classList.remove('active'));

  const btnCloseModalBtn = document.getElementById('btnCloseModalBtn');
  if (btnCloseModalBtn) btnCloseModalBtn.addEventListener('click', () => recordModal.classList.remove('active'));

  if (recordModal) {
    recordModal.addEventListener('click', (e) => {
      if (e.target === recordModal) recordModal.classList.remove('active');
    });
  }

  // --- Devices View ---
  const deviceTableBody = document.getElementById('deviceTableBody');
  const registerDeviceModal = document.getElementById('registerDeviceModal');

  function loadDevices() {
    fetch('/api/v1/dashboard/devices')
      .then(res => res.json())
      .then(devices => {
        if (!deviceTableBody) return;
        if (!devices || devices.length === 0) {
          deviceTableBody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 2.5rem; color:var(--text-muted)">No registered gateway devices found</td></tr>';
          return;
        }

        let html = '';
        devices.forEach(d => {
          const registered = d.created_at ? new Date(d.created_at).toLocaleString() : '-';
          const lastSeen = d.last_seen_at ? timeAgo(new Date(d.last_seen_at)) : '<span style="color:var(--text-dim)">Never</span>';
          html += `
            <tr>
              <td><span class="mono-cell">${escapeHtml(d.id)}</span></td>
              <td><strong>${escapeHtml(d.name)}</strong></td>
              <td><span class="mono-cell">${escapeHtml(d.token_fingerprint || '')}</span></td>
              <td style="color:var(--text-secondary)">${registered}</td>
              <td>${lastSeen}</td>
              <td style="text-align: right">
                <button class="btn btn-danger-soft btn-sm" onclick="revokeDevice('${d.id}', '${escapeHtml(d.name)}')">Revoke</button>
              </td>
            </tr>
          `;
        });
        deviceTableBody.innerHTML = html;
      })
      .catch(err => {
        if (deviceTableBody) {
          deviceTableBody.innerHTML = `<tr><td colspan="6" style="color:var(--danger); padding: 1.5rem; text-align: center">Error: ${escapeHtml(err.message)}</td></tr>`;
        }
      });
  }

  window.revokeDevice = (id, name) => {
    if (!confirm(`Are you sure you want to revoke gateway device "${name}" (${id})? It will immediately lose access to ingest SMS messages.`)) {
      return;
    }

    fetch(`/api/v1/dashboard/devices/${id}`, { method: 'DELETE' })
      .then(res => res.json())
      .then(() => {
        showToast(`Device "${name}" has been revoked`, 'warning', 2500);
        loadDevices();
      })
      .catch(err => showToast(`Failed to revoke device: ${err.message}`, 'error', 3000));
  };

  const btnOpenRegisterDevice = document.getElementById('btnOpenRegisterDevice');
  if (btnOpenRegisterDevice) {
    btnOpenRegisterDevice.addEventListener('click', () => {
      const form = document.getElementById('registerForm');
      const res = document.getElementById('registerResult');
      const input = document.getElementById('newDeviceName');
      const submitBtn = document.getElementById('btnSubmitRegister');

      if (form) form.style.display = 'block';
      if (res) res.style.display = 'none';
      if (input) {
        input.value = '';
        setTimeout(() => input.focus(), 150);
      }
      if (submitBtn) submitBtn.style.display = 'inline-flex';
      if (registerDeviceModal) registerDeviceModal.classList.add('active');
    });
  }

  const btnCloseRegisterModal = document.getElementById('btnCloseRegisterModal');
  if (btnCloseRegisterModal) btnCloseRegisterModal.addEventListener('click', () => registerDeviceModal.classList.remove('active'));

  const btnCancelRegister = document.getElementById('btnCancelRegister');
  if (btnCancelRegister) btnCancelRegister.addEventListener('click', () => registerDeviceModal.classList.remove('active'));

  const btnSubmitRegister = document.getElementById('btnSubmitRegister');
  if (btnSubmitRegister) {
    btnSubmitRegister.addEventListener('click', () => {
      const input = document.getElementById('newDeviceName');
      const name = input ? input.value.trim() : '';
      fetch('/api/v1/dashboard/devices', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name || 'Gateway Android Device' })
      })
        .then(res => res.json())
        .then(data => {
          const form = document.getElementById('registerForm');
          const res = document.getElementById('registerResult');
          const tokenInput = document.getElementById('generatedTokenText');

          if (form) form.style.display = 'none';
          if (res) res.style.display = 'block';
          if (tokenInput) tokenInput.value = data.token;
          btnSubmitRegister.style.display = 'none';

          showToast('Device registered! Copy your token.', 'success', 3000);
          loadDevices();
        })
        .catch(err => showToast(`Failed to register device: ${err.message}`, 'error', 3000));
    });
  }

  const btnCopyToken = document.getElementById('btnCopyToken');
  if (btnCopyToken) {
    btnCopyToken.addEventListener('click', () => {
      const tokenInput = document.getElementById('generatedTokenText');
      const copyTextSpan = document.getElementById('btnCopyTokenText');
      if (tokenInput && tokenInput.value) {
        navigator.clipboard.writeText(tokenInput.value).then(() => {
          if (copyTextSpan) copyTextSpan.textContent = 'Copied!';
          showToast('Bearer token copied to clipboard', 'success', 2000);
          setTimeout(() => {
            if (copyTextSpan) copyTextSpan.textContent = 'Copy';
          }, 2000);
        });
      }
    });
  }

  // --- Utility Helpers ---
  function formatSeconds(secs) {
    if (!secs) return '0s';
    const d = Math.floor(secs / 86400);
    const h = Math.floor((secs % 86400) / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = Math.floor(secs % 60);
    if (d > 0) return `${d}d ${h}h ${m}m`;
    if (h > 0) return `${h}h ${m}s`;
    if (m > 0) return `${m}m ${s}s`;
    return `${s}s`;
  }

  function timeAgo(date) {
    const seconds = Math.floor((new Date() - date) / 1000);
    if (seconds < 60) return `${seconds}s ago`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
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

  // Initial startup
  connectSSE();
  loadMetrics();
});
