# Google Drive Integration

> **Status**: ✅ **Implemented** (browser-only, optional feature)

## Overview

OrcPub now supports optional Google Drive integration for importing and exporting homebrew content (.orcbrew files). This feature enables:

- **Multi-device access**: Save homebrew to Google Drive, access from any device
- **Cloud backup**: Never lose your custom content
- **Easy sharing**: Share .orcbrew files via Google Drive links
- **Browser-only**: No backend changes required, all OAuth happens client-side

## User Experience

### Setup (Administrators Only)

1. Create a Google Cloud Console project
2. Enable Google Drive API
3. Create OAuth 2.0 Client ID (Web application type)
4. Add authorized JavaScript origins:
   - `http://localhost:8890` (development)
   - `https://yourdomain.com` (production)
5. Add authorized redirect URIs:
   - `http://localhost:8890/oauth/google/callback` (development)
   - `https://yourdomain.com/oauth/google/callback` (production)
6. Set environment variable:
   ```bash
   # In profiles.clj (local development)
   {:dev {:env {:google-client-id "123456789.apps.googleusercontent.com"}}}

   # OR in docker-compose.yaml (deployment)
   GOOGLE_CLIENT_ID=123456789.apps.googleusercontent.com
   ```

### Usage (End Users)

1. **Navigate to "My Content" page**
2. **See "Cloud Storage" section** (if Google Drive is configured)
3. **Click "Connect"** to authorize Google Drive access
4. **Grant permissions** (will redirect to Google consent screen)
5. **Browse files** in your Google Drive
6. **Import**: Click "Import" button next to any .orcbrew file
7. **Export**: Click "Export to Google Drive" or "Export All to Google Drive"

### Screenshots of UI

```
┌────────────────────────────────────────────────────────┐
│ Cloud Storage                                          │
├────────────────────────────────────────────────────────┤
│                                                         │
│ Google Drive                                           │
│ ┌──────────────────────────────────────────────────┐  │
│ │ [Drive Icon]  Google Drive            ✓ Ready    │  │
│ │               Connected as user@example.com       │  │
│ │                                    [Disconnect]   │  │
│ └──────────────────────────────────────────────────┘  │
│                                                         │
│ Your .orcbrew Files                        [↻ Refresh] │
│ ┌──────────────────────────────────────────────────┐  │
│ │ Name              Size     Modified    Actions   │  │
│ │ my-spells.orcbrew  45 KB   2026-01-15  [Import]  │  │
│ │                                        [Delete]  │  │
│ │ races.orcbrew      12 KB   2026-01-10  [Import]  │  │
│ │                                        [Delete]  │  │
│ └──────────────────────────────────────────────────┘  │
│                                                         │
│ Export to Cloud                                        │
│ [↑ Export All to Google Drive]                        │
└────────────────────────────────────────────────────────┘
```

## Technical Architecture

### Components

```
src/cljs/orcpub/cloud/
├── oauth.cljs           # OAuth 2.0 + PKCE utilities
├── google_drive.cljs    # Google Drive API wrapper
├── events.cljs          # re-frame events for cloud operations
└── views.cljs           # UI components
```

### Data Flow

```
User clicks "Connect"
  ↓
Generate PKCE code verifier + challenge
  ↓
Store verifier in sessionStorage
  ↓
Redirect to Google OAuth consent screen
  ↓
User grants permissions
  ↓
Redirect back to /oauth/google/callback
  ↓
Exchange authorization code for access token (PKCE)
  ↓
Fetch user info (email, name)
  ↓
Store token in memory (NOT localStorage)
  ↓
Connected! Show file browser
```

### Security

- **OAuth 2.0 with PKCE**: Prevents authorization code interception
- **drive.file scope**: Narrow permissions (only files app creates)
- **No backend storage**: Tokens never touch the database
- **Memory-only tokens**: Access tokens stored in memory, not localStorage
- **State parameter**: CSRF protection during OAuth flow
- **Client ID is public**: Safe to expose in frontend code

### API Endpoints Used

- `https://accounts.google.com/o/oauth2/v2/auth` - OAuth authorization
- `https://oauth2.googleapis.com/token` - Token exchange
- `https://www.googleapis.com/oauth2/v2/userinfo` - User info
- `https://www.googleapis.com/drive/v3/files` - List files
- `https://www.googleapis.com/drive/v3/files/{id}?alt=media` - Download file
- `https://www.googleapis.com/upload/drive/v3/files` - Upload file

## Integration with Existing Code

### Import Reuses Existing Logic

```clojure
;; In orcpub.cloud.events
(rf/reg-event-fx
  ::download-success
  (fn [{:keys [db]} [_ filename content]]
    (let [plugin-name (str/replace filename #"\.orcbrew$" "")]
      {:db (assoc-in db [:cloud-storage :google-drive :sync-status] :idle)
       :dispatch-n [[::e5/import-plugin plugin-name content]  ; <-- Reuses existing!
                    [:show-success-message (str "Imported '" filename "' from Google Drive")]]})))
```

The cloud integration **does not duplicate** import/export logic. It:
1. Downloads file content from Google Drive
2. Passes content to existing `::e5/import-plugin` event
3. Existing validation, cleaning, and merging logic handles the rest

### Export Reuses Serialization

```clojure
;; In orcpub.cloud.events
(rf/reg-event-fx
  ::export-to-google-drive
  (fn [{:keys [db]} [_ plugin-name plugin pretty-print?]]
    (let [content (if pretty-print?
                    (with-out-str (pprint/pprint plugin))  ; <-- Reuses existing!
                    (str plugin))]
      {:fx [[:upload-to-drive {:filename (str plugin-name ".orcbrew")
                               :content content}]]})))
```

## Feature Flagging

The feature is **completely hidden** when not configured:

```clojure
;; In orcpub.cloud.google-drive
(defn enabled?
  "Check if Google Drive integration is enabled (client ID configured)."
  []
  (some? (:client-id auth-config)))

;; In UI components
(when @(rf/subscribe [::cloud-events/google-drive-enabled?])
  [cloud-storage-panel])
```

**Result**: If `GOOGLE_CLIENT_ID` is not set, users see nothing. No broken UI, no confusing buttons.

## Error Handling

All operations include comprehensive error handling:

- **OAuth failures**: Clear error messages with troubleshooting hints
- **Network errors**: Retry logic with exponential backoff (planned)
- **Invalid files**: Reuses existing import validation
- **Token expiry**: Automatic refresh before API calls
- **Missing permissions**: Helpful prompts to reconnect

## Testing

### Manual Testing Checklist

- [ ] Connect to Google Drive successfully
- [ ] Browse .orcbrew files in Drive
- [ ] Import file from Drive
- [ ] Verify imported content appears in "My Content"
- [ ] Export single plugin to Drive
- [ ] Export all plugins to Drive
- [ ] Verify exported files appear in Drive file browser
- [ ] Delete file from Drive
- [ ] Disconnect from Drive
- [ ] Reconnect to Drive (token refresh)
- [ ] Test with no GOOGLE_CLIENT_ID (should hide UI)

### Test OAuth Flow

```bash
# 1. Set up test client ID
echo '{:dev {:env {:google-client-id "YOUR-TEST-CLIENT-ID"}}}' > profiles.clj

# 2. Start dev server
lein repl
# In REPL:
(init-database)
(start-server)

# 3. Open browser to http://localhost:8890
# 4. Navigate to "My Content"
# 5. Click "Connect" in Cloud Storage section
# 6. Complete OAuth flow
# 7. Verify connection status
```

## Limitations

### Current Scope (MVP)

- ✅ Google Drive only (Dropbox planned for future)
- ✅ Homebrew content only (.orcbrew files)
- ✅ Manual sync (no auto-save)
- ✅ Single-user (no collaboration)

### Future Enhancements

- [ ] Auto-sync on changes
- [ ] Conflict resolution for simultaneous edits
- [ ] Version history browsing
- [ ] Character data sync (not just homebrew)
- [ ] Dropbox integration
- [ ] Shared folders (collaborative homebrew editing)

### Known Issues

- **Token expiry**: Tokens expire after 1 hour. Currently handled with automatic refresh, but users may need to reconnect if inactive for extended periods.
- **Large files**: Files > 5 MB may be slow to upload/download. Resumable uploads not yet implemented.
- **CORS**: If deployed on a domain not whitelisted in Google Cloud Console, OAuth will fail with CORS errors.

## Troubleshooting

### "Google Drive integration not configured"

**Problem**: `GOOGLE_CLIENT_ID` environment variable not set.

**Solution**: Set the variable in `profiles.clj` (local dev) or `docker-compose.yaml` (deployment).

### "OAuth failed: redirect_uri_mismatch"

**Problem**: Redirect URI not whitelisted in Google Cloud Console.

**Solution**: Add `http://localhost:8890/oauth/google/callback` (or your production URL) to authorized redirect URIs.

### "Not connected to Google Drive"

**Problem**: Token expired or user disconnected.

**Solution**: Click "Connect" to re-authenticate.

### "Failed to list files: 401"

**Problem**: Access token expired and refresh failed.

**Solution**: Disconnect and reconnect to obtain fresh tokens.

## Code References

| File | Purpose |
|------|---------|
| `src/cljs/orcpub/cloud/oauth.cljs` | OAuth 2.0 + PKCE utilities |
| `src/cljs/orcpub/cloud/google_drive.cljs` | Google Drive API wrapper |
| `src/cljs/orcpub/cloud/events.cljs` | re-frame events for cloud operations |
| `src/cljs/orcpub/cloud/views.cljs` | UI components (connection, file browser, etc) |
| `web/cljs/orcpub/core.cljs:71` | OAuth callback route registration |
| `src/cljs/orcpub/dnd/e5/views.cljs:7503` | Cloud storage panel in "My Content" |
| `src/cljc/orcpub/route_map.cljc:113` | Route definition for OAuth callback |
| `src/clj/orcpub/index.clj:128-134` | Client ID injection and API script loading |

## Related Documentation

- [CLOUD_DRIVE_INTEGRATION_FEASIBILITY.md](./CLOUD_DRIVE_INTEGRATION_FEASIBILITY.md) - Feasibility analysis and architecture planning
- [CONFIGURATION_PATTERN.md](./CONFIGURATION_PATTERN.md) - Environment variable configuration
- [CODEBASE.md](./CODEBASE.md) - General codebase knowledge

## Support

For issues or questions:
1. Check this documentation first
2. Verify environment variables are set correctly
3. Check browser console for JavaScript errors
4. File an issue on GitHub with:
   - Steps to reproduce
   - Browser and version
   - Console error messages
   - Screenshots (if UI-related)
