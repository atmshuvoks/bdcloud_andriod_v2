# Backend Deploy Guide — App Updates & Notifications

## Quick Setup (3 steps)

### 1. Add the route to your Express app
Copy `routes/app.js` to your backend project and add:

```js
const appRoutes = require('./routes/app');
app.use('/software-api/app', appRoutes);
```

### 2. Create data directory
Copy the `data/` folder to your backend project root:
- `data/app_version.json` — version config
- `data/app_notifications.json` — notifications to send

### 3. Restart your server

---

## How to Push an Update

1. Build new APK in Android Studio
2. Upload APK to your server (e.g. `/var/www/downloads/bdcloud.apk`)
3. Edit `data/app_version.json`:

```json
{
    "latest_version": "2.1.0",
    "version_code": 2,
    "download_url": "https://new.bdcloud.eu.org/downloads/bdcloud.apk",
    "release_notes": "New features and bug fixes",
    "force_update": false
}
```

Set `force_update: true` if users MUST update (security fix, etc.)

## How to Send Notifications

Edit `data/app_notifications.json`:

```json
{
    "notifications": [
        {
            "id": "unique-id-here",
            "title": "Hello Users!",
            "message": "Your message here. This will show as an Android notification.",
            "type": "info",
            "created_at": "2026-02-26T00:00:00Z"
        }
    ]
}
```

**Important**: Each notification needs a unique `id`. Users only see each `id` once.
To send a new message, add a new entry with a new `id`.
