/* ═══════════════════════════════════════════
   TenantShield — Web Client
   WebSocket + Camera + Chat UI
   ═══════════════════════════════════════════ */

// ─── State ───────────────────────────────
let ws = null;
let cameraStream = null;
let cameraActive = false;
let frameInterval = null;
let currentComplaintData = null;

const FRAME_INTERVAL_MS = 3000;  // Send a frame every 3 seconds

// ─── DOM refs ────────────────────────────
const welcomeScreen     = document.getElementById('welcome-screen');
const inspectionScreen  = document.getElementById('inspection-screen');
const btnStart          = document.getElementById('btn-start');
const languageSelect    = document.getElementById('language-select');
const statusDot         = document.getElementById('status-dot');
const statusText        = document.getElementById('status-text');
const chatMessages      = document.getElementById('chat-messages');
const chatInput         = document.getElementById('chat-input');
const btnSend           = document.getElementById('btn-send');
const btnEmergency      = document.getElementById('btn-emergency');
const btnCameraToggle   = document.getElementById('btn-camera-toggle');
const cameraPreview     = document.getElementById('camera-preview');
const cameraCanvas      = document.getElementById('camera-canvas');
const cameraPlaceholder = document.getElementById('camera-placeholder');
const hazardBadge       = document.getElementById('hazard-badge');

// ─── Screen navigation ───────────────────
function showScreen(screen) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    screen.classList.add('active');
}

// ─── Toast notifications ─────────────────
function showToast(message, type = '') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 4000);
}

// ─── Modal helpers ───────────────────────
window.closeModal = function(id) {
    document.getElementById(id).classList.add('hidden');
};

function openModal(id) {
    document.getElementById(id).classList.remove('hidden');
}

// ═══════════════════════════════════════════
// WEBSOCKET
// ═══════════════════════════════════════════

function connectWebSocket(language) {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${location.host}/ws/inspect`;

    statusText.textContent = 'Connecting...';
    statusDot.className = '';

    ws = new WebSocket(url);

    ws.onopen = () => {
        statusDot.className = 'connected';
        statusText.textContent = 'TenantShield is listening';
        btnEmergency.classList.remove('hidden');
        enableCameraButton();

        // Send start message
        ws.send(JSON.stringify({
            type: 'start',
            language: language,
            session_id: crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(),
        }));
    };

    ws.onmessage = (event) => {
        try {
            const msg = JSON.parse(event.data);
            handleServerMessage(msg);
        } catch (e) {
            console.error('Parse error:', e);
        }
    };

    ws.onclose = () => {
        statusDot.className = '';
        statusText.textContent = 'Disconnected';
        showToast('Connection lost. Refresh to reconnect.', 'error');
    };

    ws.onerror = (err) => {
        console.error('WebSocket error:', err);
    };
}

function sendMessage(type, data) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type, ...data }));
    }
}

// ═══════════════════════════════════════════
// MESSAGE HANDLERS
// ═══════════════════════════════════════════

function handleServerMessage(msg) {
    // Remove thinking indicator when we get a real response
    removeThinking();

    switch (msg.type) {
        case 'text':
            addAIMessage(msg.content);
            break;

        case 'data':
            handleBuildingData(msg.content);
            break;

        case 'hazard':
            handleHazard(msg);
            break;

        case 'action':
            handleAction(msg);
            break;

        case 'complaint':
            handleComplaint(msg.content);
            break;

        case 'error':
            showToast(msg.message, 'error');
            addSystemMessage('Error: ' + msg.message);
            break;
    }
}

// ─── Chat message helpers ────────────────

function addAIMessage(text) {
    const div = document.createElement('div');
    div.className = 'msg ai';
    div.textContent = text;
    chatMessages.appendChild(div);
    scrollChat();
}

function addUserMessage(text) {
    const div = document.createElement('div');
    div.className = 'msg user';
    div.textContent = text;
    chatMessages.appendChild(div);
    scrollChat();
}

function addSystemMessage(text) {
    const div = document.createElement('div');
    div.className = 'msg system';
    div.textContent = text;
    chatMessages.appendChild(div);
    scrollChat();
}

function addThinking() {
    // Only one thinking indicator at a time
    if (document.querySelector('.msg.thinking')) return;
    const div = document.createElement('div');
    div.className = 'msg thinking';
    div.innerHTML = '<span class="dots"><span>.</span><span>.</span><span>.</span></span>';
    chatMessages.appendChild(div);
    scrollChat();

    // Show thinking status
    statusDot.className = 'thinking';
    statusText.textContent = 'Analyzing...';
}

function removeThinking() {
    const el = document.querySelector('.msg.thinking');
    if (el) el.remove();
    if (ws && ws.readyState === WebSocket.OPEN) {
        statusDot.className = 'connected';
        statusText.textContent = 'TenantShield is listening';
    }
}

function scrollChat() {
    requestAnimationFrame(() => {
        chatMessages.scrollTop = chatMessages.scrollHeight;
    });
}

// ─── Building data card ──────────────────

function handleBuildingData(data) {
    const card = document.createElement('div');
    card.className = 'data-card';

    // Header
    const header = document.createElement('div');
    header.className = 'data-card-header';
    header.innerHTML = `&#x1F3E2; ${data.address || 'Building Data'}`;
    card.appendChild(header);

    // Body
    const body = document.createElement('div');
    body.className = 'data-card-body';

    const rows = [];
    if (data.owner_name) rows.push(['Owner', data.owner_name]);
    if (data.llc_name)   rows.push(['LLC', data.llc_name]);
    if (data.borough)    rows.push(['Borough', data.borough]);
    if (data.bbl)        rows.push(['BBL', data.bbl]);
    if (data.manager_name) rows.push(['Manager', data.manager_name]);

    rows.forEach(([label, value]) => {
        const row = document.createElement('div');
        row.className = 'data-row';
        row.innerHTML = `<span class="data-label">${label}</span><span class="data-value">${escapeHtml(String(value))}</span>`;
        body.appendChild(row);
    });

    // Violation stats
    const violations = data.violations || {};
    const totalOpen = violations.total_open || data.total_open_violations || 0;
    const classC = violations.class_c || data.class_c_count || 0;
    const classB = violations.class_b || data.class_b_count || 0;

    if (totalOpen > 0) {
        const stats = document.createElement('div');
        stats.className = 'violation-stats';
        stats.innerHTML = `
            <div class="stat-box class-c">
                <span class="stat-number">${classC}</span>
                <span class="stat-label">Class C</span>
            </div>
            <div class="stat-box class-b">
                <span class="stat-number">${classB}</span>
                <span class="stat-label">Class B</span>
            </div>
            <div class="stat-box">
                <span class="stat-number">${totalOpen}</span>
                <span class="stat-label">Total Open</span>
            </div>
        `;
        body.appendChild(stats);
    }

    // Owner graph
    if (data.owner_graph) {
        const graph = data.owner_graph;
        if (graph.total_buildings || graph.total_violations) {
            const graphRow = document.createElement('div');
            graphRow.className = 'data-row';
            graphRow.style.marginTop = '8px';
            graphRow.innerHTML = `
                <span class="data-label">Landlord Network</span>
                <span class="data-value danger">${graph.total_buildings || '?'} buildings, ${graph.total_violations || '?'} violations</span>
            `;
            body.appendChild(graphRow);
        }

        // Property history from ACRIS Master
        const ph = graph.property_history;
        if (ph && ph.last_sale_date) {
            const amt = ph.last_sale_amount && ph.last_sale_amount !== '0'
                ? ' — $' + Number(ph.last_sale_amount).toLocaleString()
                : '';
            const saleRow = document.createElement('div');
            saleRow.className = 'data-row';
            saleRow.innerHTML = `
                <span class="data-label">Last Sale (ACRIS)</span>
                <span class="data-value">${ph.last_sale_date}${amt}</span>
            `;
            body.appendChild(saleRow);
        }
    }

    card.appendChild(body);
    chatMessages.appendChild(card);
    scrollChat();

    // Also populate and show the owner modal
    buildOwnerModal(data);
    openModal('owner-modal');
}

function buildOwnerModal(data) {
    const content = document.getElementById('owner-content');
    const violations = data.violations || {};
    const totalOpen = violations.total_open || data.total_open_violations || 0;
    const classC = violations.class_c || data.class_c_count || 0;

    let html = `<h2 style="margin-bottom:12px; font-size:18px;">${escapeHtml(data.address || 'Building')}</h2>`;

    if (data.owner_name) {
        html += `<div class="data-row"><span class="data-label">Owner</span><span class="data-value">${escapeHtml(data.owner_name)}</span></div>`;
    }
    if (data.llc_name) {
        html += `<div class="data-row"><span class="data-label">LLC</span><span class="data-value">${escapeHtml(data.llc_name)}</span></div>`;
    }
    if (totalOpen) {
        html += `<div class="data-row"><span class="data-label">Open Violations</span><span class="data-value danger">${totalOpen}</span></div>`;
    }
    if (classC) {
        html += `<div class="data-row"><span class="data-label">Class C (Hazardous)</span><span class="data-value danger">${classC}</span></div>`;
    }
    if (data.owner_graph && data.owner_graph.total_buildings) {
        html += `<div style="margin-top:12px; padding:12px; background:var(--red-dim); border-radius:var(--radius-sm); font-size:13px;">
            This landlord controls <strong>${data.owner_graph.total_buildings} buildings</strong>
            with <strong>${data.owner_graph.total_violations} total violations</strong>.
        </div>`;
    }

    // Property transaction history from ACRIS Master
    const ph = data.owner_graph?.property_history;
    if (ph) {
        html += `<div style="margin-top:12px; border-top:1px solid var(--border); padding-top:12px;">`;
        html += `<div style="font-size:12px; color:var(--text-muted); text-transform:uppercase; letter-spacing:0.5px; margin-bottom:8px;">Property Records (ACRIS)</div>`;

        if (ph.last_sale_date) {
            const amt = ph.last_sale_amount && ph.last_sale_amount !== '0'
                ? ' for $' + Number(ph.last_sale_amount).toLocaleString()
                : '';
            html += `<div class="data-row"><span class="data-label">Last Sale</span><span class="data-value">${ph.last_sale_date}${amt}</span></div>`;
        }
        if (ph.deed_buyers?.length) {
            html += `<div class="data-row"><span class="data-label">Buyer (Deed)</span><span class="data-value">${escapeHtml(ph.deed_buyers.join(', '))}</span></div>`;
        }
        if (ph.deed_sellers?.length) {
            html += `<div class="data-row"><span class="data-label">Seller (Deed)</span><span class="data-value">${escapeHtml(ph.deed_sellers.join(', '))}</span></div>`;
        }
        if (ph.latest_mortgage_amount && ph.latest_mortgage_amount !== '0') {
            html += `<div class="data-row"><span class="data-label">Latest Mortgage</span><span class="data-value">$${Number(ph.latest_mortgage_amount).toLocaleString()} (${ph.latest_mortgage_date || 'N/A'})</span></div>`;
        }
        html += `<div class="data-row"><span class="data-label">Total ACRIS Records</span><span class="data-value">${ph.total_transactions || 0}</span></div>`;
        html += `</div>`;
    }

    content.innerHTML = html;
}

// ─── Hazard handling ─────────────────────

function handleHazard(msg) {
    const hClass = msg.hazard_class || 'B';
    const hType = msg.hazard_type || '';
    const desc = msg.description || '';
    const statute = msg.statute || '';

    // Update badge on camera
    hazardBadge.classList.remove('hidden', 'class-a', 'class-b', 'class-c');
    hazardBadge.classList.add('class-' + hClass.toLowerCase());
    document.getElementById('hazard-class-text').textContent = 'Class ' + hClass;
    document.getElementById('hazard-type-text').textContent = hType || desc;

    // Add hazard card to chat
    const card = document.createElement('div');
    card.className = `hazard-card class-${hClass.toLowerCase()}`;
    card.innerHTML = `
        <div class="hazard-card-title">Class ${escapeHtml(hClass)} — ${escapeHtml(hType)}</div>
        ${statute ? `<div class="hazard-card-statute">${escapeHtml(statute)}</div>` : ''}
        ${desc ? `<div class="hazard-card-desc">${escapeHtml(desc)}</div>` : ''}
    `;
    chatMessages.appendChild(card);
    scrollChat();

    // Class C → auto-show emergency
    if (hClass === 'C') {
        showEmergencyPanel('Class C immediately hazardous: ' + (hType || desc));
    }
}

// ─── Action handling ─────────────────────

function handleAction(msg) {
    if (msg.action === 'start_camera') {
        startCamera();
        showToast('Address verified. Camera analysis started.', 'success');
    } else if (msg.action === 'show_emergency_panel') {
        showEmergencyPanel(msg.reason || '');
    }
}

// Also allow manual camera start via button — don't gate on address lookup
function enableCameraButton() {
    btnCameraToggle.classList.remove('hidden');
}

function showEmergencyPanel(reason) {
    document.getElementById('emergency-reason').textContent = reason;
    openModal('emergency-panel');
}

// ─── Complaint handling ──────────────────

function handleComplaint(data) {
    currentComplaintData = data;

    // Extract from nested structure
    const v = data.violation || {};
    const p = data.property || {};
    const hazardType = v.hazard_type || data.hazard_type || '';
    const statute = v.statute || data.statute || '';
    const complaintText = data.complaint_text || '';

    // Card in chat
    const card = document.createElement('div');
    card.className = 'complaint-card';

    // Show a preview of the complaint — first 200 chars
    const preview = complaintText
        ? complaintText.substring(0, 200) + (complaintText.length > 200 ? '...' : '')
        : 'Complaint ready for download.';

    card.innerHTML = `
        <div class="complaint-card-title">311 Complaint Generated</div>
        <div style="font-size:13px; color:var(--text-secondary); margin-bottom:4px;">
            ${escapeHtml(hazardType.replace(/_/g, ' '))} — ${escapeHtml(statute)}
        </div>
        <div style="font-size:13px; white-space:pre-line;">
            ${escapeHtml(preview)}
        </div>
        <button onclick="showComplaintModal()">View Full Complaint</button>
    `;
    chatMessages.appendChild(card);
    scrollChat();

    // Build modal
    buildComplaintModal(data);
    openModal('complaint-modal');
}

function buildComplaintModal(data) {
    const content = document.getElementById('complaint-content');

    // Pull violation details from nested structure if present
    const violation = data.violation || {};
    const sections = [
        ['Property', `${data.property?.address || data.address || ''}, ${data.borough || 'NYC'}`],
        ['Owner', data.owner?.owner_name || data.owner_name || data.llc_name || 'Unknown'],
        ['Violation', `${violation.hazard_type || data.hazard_type || ''} — Class ${violation.hazard_class || data.hazard_class || '?'}`],
        ['Statute', violation.statute || data.statute || 'N/A'],
        ['Statute Description', violation.statute_text || ''],
        ['Correction Deadline', violation.correction_deadline || ''],
        ['Evidence', violation.evidence_description || data.evidence_description || data.description || ''],
        ['BIS Disposition Codes', violation.bis_codes?.length ? violation.bis_codes.join(', ') : ''],
        ['BIS Code Reference', violation.bis_notes || ''],
        ['Complaint Text', data.complaint_text || ''],
        ['Next Steps', Array.isArray(data.next_steps) ? data.next_steps.join('\n') : (data.next_steps || '')],
    ];

    content.innerHTML = sections
        .filter(([, val]) => val)
        .map(([title, val]) => `
            <div class="complaint-section">
                <div class="complaint-section-title">${title}</div>
                <div class="complaint-section-body" style="white-space:pre-line;">${escapeHtml(String(val))}</div>
            </div>
        `).join('');
}

window.showComplaintModal = function() {
    if (currentComplaintData) openModal('complaint-modal');
};

// Download complaint as text file
document.getElementById('btn-download-complaint').addEventListener('click', () => {
    if (!currentComplaintData) return;
    const d = currentComplaintData;
    const v = d.violation || {};
    const p = d.property || {};
    const o = d.owner || {};
    const h = d.history || {};
    const nextSteps = Array.isArray(d.next_steps) ? d.next_steps.map(s => `  - ${s}`).join('\n') : (d.next_steps || '');
    const bisCodes = v.bis_codes?.length ? v.bis_codes.join(', ') : 'N/A';
    const text = [
        '╔══════════════════════════════════════════╗',
        '║     TENANTSHIELD 311 COMPLAINT           ║',
        '╚══════════════════════════════════════════╝',
        '',
        `Date: ${new Date().toLocaleDateString()}`,
        `Reference: ${d.session_id || 'N/A'}`,
        '',
        '── PROPERTY ──────────────────────────────',
        `Address:         ${p.address || 'N/A'}`,
        `Apartment:       ${p.apartment || 'N/A'}`,
        `BBL:             ${p.bbl || 'N/A'}`,
        `Registration ID: ${p.registration_id || 'N/A'}`,
        '',
        '── OWNER / MANAGEMENT ────────────────────',
        `Owner:           ${o.owner_name || 'N/A'}`,
        `Corporation:     ${o.corporation_name || 'N/A'}`,
        `Owner Phone:     ${o.owner_phone || 'N/A'}`,
        `Manager:         ${o.manager_name || 'N/A'}`,
        `Manager Phone:   ${o.manager_phone || 'N/A'}`,
        '',
        '── VIOLATION ─────────────────────────────',
        `Type:            ${(v.hazard_type || '').replace(/_/g, ' ')}`,
        `Class:           ${v.hazard_class || 'N/A'} — ${v.class_label || ''}`,
        `Statute:         ${v.statute || 'N/A'}`,
        `Description:     ${v.statute_text || ''}`,
        `Deadline:        ${v.correction_deadline || 'N/A'}`,
        '',
        '── BIS DISPOSITION CODES ─────────────────',
        `Codes:           ${bisCodes}`,
        `Reference:       ${v.bis_notes || 'N/A'}`,
        '',
        '── EVIDENCE ──────────────────────────────',
        v.evidence_description || '',
        '',
        '── BUILDING HISTORY ──────────────────────',
        `Total Violations:  ${h.total_violations || 'N/A'}`,
        `Open Violations:   ${h.open_violations || 'N/A'}`,
        `Class C Count:     ${h.class_c_count || 'N/A'}`,
        `Class B Count:     ${h.class_b_count || 'N/A'}`,
        `Days Neglected:    ${h.days_neglected || 'N/A'}`,
        `Last Inspection:   ${h.last_inspection || 'N/A'}`,
        '',
        '── COMPLAINT TEXT ────────────────────────',
        d.complaint_text || '',
        '',
        '── NEXT STEPS ────────────────────────────',
        nextSteps,
        '',
        '── RESOURCES ─────────────────────────────',
        `HPD Online:      ${d.actions?.hpd_online_url || 'https://www.nyc.gov/hpd'}`,
        `NYC 311 Portal:  ${d.actions?.nyc311_url || 'https://portal.311.nyc.gov'}`,
        '',
        '──────────────────────────────────────────',
        'Generated by TenantShield — AI Housing Inspector',
        `${new Date().toLocaleString()}`,
    ].join('\n');

    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `complaint_${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('Complaint downloaded!', 'success');
});

// ═══════════════════════════════════════════
// CAMERA
// ═══════════════════════════════════════════

async function startCamera() {
    if (cameraActive) return;

    try {
        cameraStream = await navigator.mediaDevices.getUserMedia({
            video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } },
            audio: false,
        });
        cameraPreview.srcObject = cameraStream;
        cameraPreview.classList.add('active');
        cameraPlaceholder.classList.add('hidden');
        btnCameraToggle.classList.remove('hidden');
        btnCameraToggle.classList.add('active');
        cameraActive = true;

        // Start sending frames
        frameInterval = setInterval(captureAndSendFrame, FRAME_INTERVAL_MS);

        addSystemMessage('Camera active — analyzing your apartment');
    } catch (err) {
        console.error('Camera error:', err);
        showToast('Camera access denied. You can still chat by text.', 'error');
    }
}

function stopCamera() {
    cameraActive = false;
    if (frameInterval) {
        clearInterval(frameInterval);
        frameInterval = null;
    }
    if (cameraStream) {
        cameraStream.getTracks().forEach(t => t.stop());
        cameraStream = null;
    }
    cameraPreview.classList.remove('active');
    cameraPreview.srcObject = null;
    cameraPlaceholder.classList.remove('hidden');
    btnCameraToggle.classList.remove('active');

    sendMessage('camera_toggle', { active: false });
}

function captureAndSendFrame() {
    if (!cameraActive || !ws || ws.readyState !== WebSocket.OPEN) return;
    if (!cameraPreview.videoWidth) return;

    const canvas = cameraCanvas;
    // Resize to 640px wide for bandwidth efficiency
    const scale = 640 / cameraPreview.videoWidth;
    canvas.width = 640;
    canvas.height = Math.round(cameraPreview.videoHeight * scale);

    const ctx = canvas.getContext('2d');
    ctx.drawImage(cameraPreview, 0, 0, canvas.width, canvas.height);

    // JPEG at 70% quality → base64
    const dataUrl = canvas.toDataURL('image/jpeg', 0.7);
    const base64 = dataUrl.split(',')[1];

    sendMessage('frame', { data: base64 });
}

// ═══════════════════════════════════════════
// USER INPUT
// ═══════════════════════════════════════════

function handleSend() {
    const text = chatInput.value.trim();
    if (!text) return;
    if (!ws || ws.readyState !== WebSocket.OPEN) {
        showToast('Not connected. Refresh to reconnect.', 'error');
        return;
    }

    addUserMessage(text);
    sendMessage('text', { content: text });
    chatInput.value = '';
    addThinking();
}

btnSend.addEventListener('click', handleSend);
chatInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
    }
});

// ═══════════════════════════════════════════
// EVENT WIRING
// ═══════════════════════════════════════════

// Start button
btnStart.addEventListener('click', () => {
    showScreen(inspectionScreen);
    connectWebSocket(languageSelect.value);
});

// Emergency button
btnEmergency.addEventListener('click', () => {
    showEmergencyPanel('Emergency button pressed by tenant');
});

// Camera toggle
btnCameraToggle.addEventListener('click', () => {
    if (cameraActive) {
        stopCamera();
    } else {
        startCamera();
    }
});

// ─── Utility ─────────────────────────────

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
