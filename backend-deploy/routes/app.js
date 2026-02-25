/**
 * BDCLOUD — Backend Routes for App Updates & Notifications
 * ────────────────────────────────────────────────────────
 * 
 * Add these routes to your Express.js backend.
 * 
 * SETUP:
 * 1. Copy this file to your backend project
 * 2. Import and use in your Express app:
 *    const appRoutes = require('./routes/app');
 *    app.use('/software-api/app', appRoutes);
 * 3. Create the data files (see below)
 * 4. Restart your server
 */

const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');

// ── Data files (create these JSON files on your server) ──

const VERSION_FILE = path.join(__dirname, '../data/app_version.json');
const NOTIFICATIONS_FILE = path.join(__dirname, '../data/app_notifications.json');

// ── GET /app/version ─────────────────────────────────────
// Returns the latest app version info

router.get('/version', (req, res) => {
    try {
        if (!fs.existsSync(VERSION_FILE)) {
            // Create default version file
            const defaultVersion = {
                latest_version: "2.0.0",
                version_code: 1,
                download_url: "https://new.bdcloud.eu.org/downloads/bdcloud.apk",
                release_notes: "Initial release",
                force_update: false
            };
            fs.mkdirSync(path.dirname(VERSION_FILE), { recursive: true });
            fs.writeFileSync(VERSION_FILE, JSON.stringify(defaultVersion, null, 2));
        }
        const versionData = JSON.parse(fs.readFileSync(VERSION_FILE, 'utf8'));
        res.json({ success: true, ...versionData });
    } catch (err) {
        res.json({ success: false, error: { message: err.message } });
    }
});

// ── GET /app/notifications ───────────────────────────────
// Returns active notifications to show users

router.get('/notifications', (req, res) => {
    try {
        if (!fs.existsSync(NOTIFICATIONS_FILE)) {
            // Create default empty notifications
            const defaultNotifs = { notifications: [] };
            fs.mkdirSync(path.dirname(NOTIFICATIONS_FILE), { recursive: true });
            fs.writeFileSync(NOTIFICATIONS_FILE, JSON.stringify(defaultNotifs, null, 2));
        }
        const notifData = JSON.parse(fs.readFileSync(NOTIFICATIONS_FILE, 'utf8'));
        res.json({ success: true, ...notifData });
    } catch (err) {
        res.json({ success: false, error: { message: err.message } });
    }
});

module.exports = router;
