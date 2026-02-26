/**
 * BDCLOUD Admin Dashboard — Notification & Update Manager
 * ────────────────────────────────────────────────────────
 * 
 * Serves a web admin panel + API for:
 *   - Sending notifications to all app users
 *   - Managing app version/update info
 * 
 * SETUP:
 *   const adminRoutes = require('./routes/admin');
 *   app.use('/api/admin', adminRoutes);
 * 
 * ACCESS:
 *   https://new.bdcloud.eu.org/software-api/admin?key=YOUR_SECRET_KEY
 */

const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');

// ══════════════════════════════════════════════════════════
// ⚠️  CHANGE THIS SECRET KEY — only you should know it
// ══════════════════════════════════════════════════════════
const ADMIN_KEY = 'bdcloud-admin-2026';

const NOTIFICATIONS_FILE = path.join(__dirname, '../data/app_notifications.json');
const VERSION_FILE = path.join(__dirname, '../data/app_version.json');

// ── Middleware: check admin key ──
function requireAuth(req, res, next) {
    const key = req.query.key || req.body?.key || req.headers['x-admin-key'];
    if (key !== ADMIN_KEY) {
        return res.status(403).json({ error: 'Unauthorized' });
    }
    next();
}

// ── Helper: read/write JSON safely ──
function readJson(filepath, fallback) {
    try {
        if (fs.existsSync(filepath)) {
            return JSON.parse(fs.readFileSync(filepath, 'utf8'));
        }
    } catch (e) { }
    return fallback;
}

function writeJson(filepath, data) {
    fs.mkdirSync(path.dirname(filepath), { recursive: true });
    fs.writeFileSync(filepath, JSON.stringify(data, null, 2));
}

// ══════════════════════════════════════════════════════════
// API ENDPOINTS
// ══════════════════════════════════════════════════════════

// POST /api/admin/send — Send a notification
router.post('/send', express.json(), requireAuth, (req, res) => {
    try {
        const { title, message, type } = req.body;
        if (!title || !message) {
            return res.status(400).json({ error: 'Title and message required' });
        }
        const data = readJson(NOTIFICATIONS_FILE, { notifications: [] });
        const newNotif = {
            id: `msg-${Date.now()}`,
            title,
            message,
            type: type || 'info',
            created_at: new Date().toISOString()
        };
        data.notifications.unshift(newNotif); // newest first
        // Keep max 50 notifications
        data.notifications = data.notifications.slice(0, 50);
        writeJson(NOTIFICATIONS_FILE, data);
        res.json({ success: true, notification: newNotif });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// DELETE /api/admin/notification/:id — Delete a notification
router.delete('/notification/:id', requireAuth, (req, res) => {
    try {
        const data = readJson(NOTIFICATIONS_FILE, { notifications: [] });
        data.notifications = data.notifications.filter(n => n.id !== req.params.id);
        writeJson(NOTIFICATIONS_FILE, data);
        res.json({ success: true });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// GET /api/admin/notifications — List all notifications
router.get('/notifications', requireAuth, (req, res) => {
    const data = readJson(NOTIFICATIONS_FILE, { notifications: [] });
    res.json(data);
});

// POST /api/admin/version — Update app version info
router.post('/version', express.json(), requireAuth, (req, res) => {
    try {
        const { latest_version, version_code, download_url, release_notes, force_update } = req.body;
        const data = {
            latest_version: latest_version || '2.0.0',
            version_code: version_code || 1,
            download_url: download_url || '',
            release_notes: release_notes || '',
            force_update: force_update || false
        };
        writeJson(VERSION_FILE, data);
        res.json({ success: true, version: data });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// GET /api/admin/version — Get current version info
router.get('/version', requireAuth, (req, res) => {
    const data = readJson(VERSION_FILE, { latest_version: '2.0.0', version_code: 1 });
    res.json(data);
});

// ══════════════════════════════════════════════════════════
// ADMIN DASHBOARD HTML
// ══════════════════════════════════════════════════════════

router.get('/', requireAuth, (req, res) => {
    const key = req.query.key;
    res.send(getAdminHTML(key));
});

function getAdminHTML(key) {
    return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>BDCLOUD Admin</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
    font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
    background: linear-gradient(135deg, #0a0e1a 0%, #0d1529 50%, #0a1628 100%);
    color: #e0e6f0;
    min-height: 100vh;
    padding: 20px;
}
.container { max-width: 700px; margin: 0 auto; }
h1 {
    text-align: center;
    font-size: 28px;
    margin-bottom: 8px;
    background: linear-gradient(90deg, #00e5ff, #76ff03);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
}
.subtitle {
    text-align: center;
    color: #667eaa;
    font-size: 14px;
    margin-bottom: 32px;
}
.card {
    background: rgba(22, 33, 62, 0.7);
    border: 1px solid rgba(0, 229, 255, 0.15);
    border-radius: 16px;
    padding: 24px;
    margin-bottom: 20px;
    backdrop-filter: blur(10px);
}
.card h2 {
    font-size: 16px;
    color: #00e5ff;
    margin-bottom: 16px;
    text-transform: uppercase;
    letter-spacing: 1px;
    font-weight: 600;
}
label {
    display: block;
    font-size: 13px;
    color: #8899bb;
    margin-bottom: 6px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}
input, textarea, select {
    width: 100%;
    padding: 12px 16px;
    background: rgba(10, 14, 26, 0.8);
    border: 1px solid rgba(0, 229, 255, 0.2);
    border-radius: 10px;
    color: #e0e6f0;
    font-size: 15px;
    margin-bottom: 16px;
    outline: none;
    transition: border-color 0.3s;
}
input:focus, textarea:focus {
    border-color: #00e5ff;
    box-shadow: 0 0 0 3px rgba(0, 229, 255, 0.1);
}
textarea { resize: vertical; min-height: 80px; font-family: inherit; }
.btn {
    background: linear-gradient(135deg, #00e5ff, #00b8d4);
    color: #0a0e1a;
    border: none;
    padding: 14px 32px;
    border-radius: 12px;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    width: 100%;
    transition: transform 0.2s, box-shadow 0.2s;
    text-transform: uppercase;
    letter-spacing: 1px;
}
.btn:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 25px rgba(0, 229, 255, 0.3);
}
.btn:active { transform: translateY(0); }
.btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;
}
.btn-danger {
    background: linear-gradient(135deg, #ff5252, #d32f2f);
    color: white;
    padding: 8px 16px;
    font-size: 12px;
    width: auto;
}
.toast {
    position: fixed;
    top: 20px;
    right: 20px;
    padding: 16px 24px;
    border-radius: 12px;
    font-weight: 600;
    z-index: 100;
    animation: slideIn 0.3s ease;
    display: none;
}
.toast.success { background: #00c853; color: #0a0e1a; }
.toast.error { background: #ff5252; color: white; }
@keyframes slideIn {
    from { transform: translateX(100px); opacity: 0; }
    to { transform: translateX(0); opacity: 1; }
}
.notif-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    background: rgba(10, 14, 26, 0.5);
    border-radius: 10px;
    margin-bottom: 8px;
    border-left: 3px solid #00e5ff;
}
.notif-item .title { font-weight: 600; font-size: 14px; }
.notif-item .msg { font-size: 12px; color: #8899bb; margin-top: 2px; }
.notif-item .time { font-size: 11px; color: #556688; }
.empty { text-align: center; color: #556688; padding: 20px; font-style: italic; }
.stats {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
}
.stat {
    flex: 1;
    background: rgba(10, 14, 26, 0.5);
    border-radius: 10px;
    padding: 16px;
    text-align: center;
}
.stat .num {
    font-size: 28px;
    font-weight: 700;
    color: #00e5ff;
}
.stat .lbl {
    font-size: 11px;
    color: #667eaa;
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-top: 4px;
}
</style>
</head>
<body>
<div class="container">
    <h1>⚡ BDCLOUD Admin</h1>
    <p class="subtitle">Notification & Update Manager</p>

    <!-- Send Notification -->
    <div class="card">
        <h2>📢 Send Notification</h2>
        <label>Title</label>
        <input type="text" id="notifTitle" placeholder="e.g. 🔥 New Servers Added!" />
        <label>Message</label>
        <textarea id="notifMessage" placeholder="Write your message here..."></textarea>
        <label>Type</label>
        <select id="notifType">
            <option value="info">ℹ️ Info</option>
            <option value="warning">⚠️ Warning</option>
            <option value="promo">🎉 Promo</option>
        </select>
        <button class="btn" id="btnSend" onclick="sendNotification()">
            🚀 Send to All Users
        </button>
    </div>

    <!-- Active Notifications -->
    <div class="card">
        <h2>📋 Active Notifications</h2>
        <div id="notifList"><div class="empty">Loading...</div></div>
    </div>

    <!-- App Version -->
    <div class="card">
        <h2>📦 App Version</h2>
        <div class="stats">
            <div class="stat">
                <div class="num" id="currentVersion">—</div>
                <div class="lbl">Current Version</div>
            </div>
            <div class="stat">
                <div class="num" id="notifCount">—</div>
                <div class="lbl">Active Notifs</div>
            </div>
        </div>
        <label>Version Number</label>
        <input type="text" id="verNum" placeholder="e.g. 2.1.0" />
        <label>APK Download URL</label>
        <input type="text" id="verUrl" placeholder="https://your-server.com/bdcloud.apk" />
        <label>Release Notes</label>
        <textarea id="verNotes" placeholder="What's new in this version..."></textarea>
        <label>
            <input type="checkbox" id="verForce" style="width:auto;margin-right:8px" />
            Force Update (users MUST update)
        </label>
        <br><br>
        <button class="btn" onclick="updateVersion()">💾 Save Version Info</button>
    </div>
</div>

<div class="toast" id="toast"></div>

<script>
const KEY = '${key}';
const BASE = window.location.origin + '/software-api/admin';

function api(method, endpoint, body) {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify({ ...body, key: KEY });
    const sep = endpoint.includes('?') ? '&' : '?';
    return fetch(BASE + endpoint + sep + 'key=' + KEY, opts).then(r => r.json());
}

function showToast(msg, type) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.className = 'toast ' + type;
    t.style.display = 'block';
    setTimeout(() => t.style.display = 'none', 3000);
}

async function sendNotification() {
    const title = document.getElementById('notifTitle').value.trim();
    const message = document.getElementById('notifMessage').value.trim();
    const type = document.getElementById('notifType').value;
    if (!title || !message) return showToast('Fill in title and message!', 'error');

    document.getElementById('btnSend').disabled = true;
    document.getElementById('btnSend').textContent = 'Sending...';
    
    try {
        const res = await api('POST', '/send', { title, message, type });
        if (res.success) {
            showToast('✅ Notification sent to all users!', 'success');
            document.getElementById('notifTitle').value = '';
            document.getElementById('notifMessage').value = '';
            loadNotifications();
        } else {
            showToast('❌ ' + (res.error || 'Failed'), 'error');
        }
    } catch (e) {
        showToast('❌ Network error', 'error');
    }
    document.getElementById('btnSend').disabled = false;
    document.getElementById('btnSend').textContent = '🚀 Send to All Users';
}

async function deleteNotif(id) {
    if (!confirm('Delete this notification?')) return;
    await api('DELETE', '/notification/' + id);
    showToast('Deleted', 'success');
    loadNotifications();
}

async function loadNotifications() {
    try {
        const data = await api('GET', '/notifications');
        const list = document.getElementById('notifList');
        const notifs = data.notifications || [];
        document.getElementById('notifCount').textContent = notifs.length;
        
        if (notifs.length === 0) {
            list.innerHTML = '<div class="empty">No active notifications</div>';
            return;
        }
        list.innerHTML = notifs.map(n => \`
            <div class="notif-item">
                <div>
                    <div class="title">\${n.title}</div>
                    <div class="msg">\${n.message}</div>
                    <div class="time">\${n.created_at ? new Date(n.created_at).toLocaleString() : ''}</div>
                </div>
                <button class="btn btn-danger" onclick="deleteNotif('\${n.id}')">🗑</button>
            </div>
        \`).join('');
    } catch (e) {
        document.getElementById('notifList').innerHTML = '<div class="empty">Failed to load</div>';
    }
}

async function loadVersion() {
    try {
        const data = await api('GET', '/version');
        document.getElementById('currentVersion').textContent = data.latest_version || '—';
        document.getElementById('verNum').value = data.latest_version || '';
        document.getElementById('verUrl').value = data.download_url || '';
        document.getElementById('verNotes').value = data.release_notes || '';
        document.getElementById('verForce').checked = data.force_update || false;
    } catch (e) { }
}

async function updateVersion() {
    const data = {
        latest_version: document.getElementById('verNum').value,
        download_url: document.getElementById('verUrl').value,
        release_notes: document.getElementById('verNotes').value,
        force_update: document.getElementById('verForce').checked
    };
    try {
        const res = await api('POST', '/version', data);
        if (res.success) {
            showToast('✅ Version info saved!', 'success');
            loadVersion();
        }
    } catch (e) {
        showToast('❌ Failed', 'error');
    }
}

// Load on startup
loadNotifications();
loadVersion();
</script>
</body>
</html>`;
}

module.exports = router;
