# Cloud Drive Integration Feasibility Analysis

## Executive Summary

This document analyzes the feasibility of integrating browser-based cloud storage (Google Drive, Dropbox, iCloud) for importing/exporting homebrew data, characters, and items in OrcPub without touching the database.

**Date**: 2026-01-14
**Status**: Feasibility Analysis
**Branch**: `claude/cloud-drive-integration-SC31k`

---

## Current State Analysis

### Existing Data Formats

**Homebrew Content**:
- Format: `.orcbrew` files containing EDN (Extensible Data Notation)
- Structure: Clojure data structures with namespaced keywords (`:orcpub.dnd.e5/races`, `:orcpub.dnd.e5/spells`, etc.)
- Storage: Browser localStorage + optional file export/import
- Size: Typically small (< 1 MB for most homebrew plugins)

**Characters**:
- Format: EDN entity structures with options and values
- Database: Stored in Datomic backend when saved
- Size: Small (< 100 KB per character typically)

**Current Import/Export**:
- File operations use FileSaver.js library (already included: `cljsjs/filesaverjs`)
- Export: Creates blob and triggers browser download
- Import: File picker → read as text → parse EDN → validate → merge into state
- Location: `/home/user/orcpub/src/cljs/orcpub/dnd/e5/events.cljs:3162-3247`

### Technology Stack

**Frontend**:
- ClojureScript 1.10.439
- React 16.6.0 (via Reagent)
- re-frame for state management
- FileSaver.js for downloads

**Backend**:
- Clojure 1.10.0
- Pedestal web framework
- Datomic database
- No existing OAuth infrastructure

---

## Cloud Storage API Analysis

### 1. Google Drive API

**Availability**: ✅ Excellent browser support
**Documentation**: https://developers.google.com/drive/api/quickstart/js

**Capabilities**:
- JavaScript SDK for browser applications
- File picker UI component
- Upload/download up to 5 GB (resumable for > 5 MB)
- OAuth 2.0 with Authorization Code + PKCE flow

**Implementation Requirements**:
- Google Cloud Console project
- OAuth 2.0 client ID configuration
- Authorized JavaScript origins (domain whitelist)
- API key for public API access

**Scopes Needed**:
- `drive.file` - Per-file access (narrow scope, no verification needed)
- OR `drive.appdata` - App-specific folder access

**Libraries**:
- Google Identity Services (GIS) library for auth
- Google API Client Library for file operations

**Pros**:
- Most mature browser API
- Official JavaScript SDK
- Good documentation and examples
- File picker UI component included
- No backend server required for basic operations

**Cons**:
- Requires Google account
- OAuth setup complexity
- Third-party cookies must be enabled
- Domain must be registered in Google Cloud Console

---

### 2. Dropbox API

**Availability**: ✅ Good browser support
**Documentation**: https://www.dropbox.com/developers/documentation/javascript

**Capabilities**:
- Official JavaScript SDK (`dropbox-sdk-js`)
- Dropbox Chooser (file picker component)
- Upload/download files
- OAuth 2.0 authentication

**Implementation Requirements**:
- Dropbox App Console configuration
- App key and secret
- Redirect URI configuration

**Libraries**:
- `dropbox-sdk-js` - Official SDK
- Dropbox Chooser - Pre-built file picker

**Pros**:
- Simple integration with Chooser component
- Good JavaScript SDK
- Pre-built UI for file selection
- Works well for small files

**Cons**:
- Requires Dropbox account
- Less comprehensive than Google Drive API
- Chooser requires specific browser support check

---

### 3. iCloud Drive

**Availability**: ❌ Limited/No browser API
**Documentation**: https://developer.apple.com/documentation/cloudkitjs

**Capabilities**:
- CloudKit JS for app data (NOT iCloud Drive files)
- Cannot access user's existing iCloud Drive files
- Only works with app-specific CloudKit containers

**Implementation Requirements**:
- Apple Developer account
- CloudKit container setup
- Does NOT provide iCloud Drive file access

**Conclusion**:
❌ **Not feasible** - Apple does not provide a public web API for accessing iCloud Drive files from browsers. CloudKit JS only allows apps to store their own data, not access users' file storage.

**Recommendation**: Skip iCloud Drive integration for now.

---

## Security & Authentication Analysis

### OAuth 2.0 Best Practices (2026)

**Required Flow**: Authorization Code with PKCE (Proof Key for Code Exchange)
- PKCE is mandatory for public clients in OAuth 2.1
- Protects against authorization code interception
- No client secret needed (safe for browser apps)

**Security Measures**:
1. **State Parameter**: CSRF protection
2. **PKCE Challenge**: Code verifier + code challenge
3. **Redirect URI Whitelist**: Prevent open redirects
4. **Short-lived Tokens**: Access tokens with limited lifetime
5. **Refresh Token Rotation**: For maintaining access

**Implementation Considerations**:
- No secrets stored in browser code
- All OAuth flows client-side
- Tokens stored in memory or sessionStorage (NOT localStorage for security)
- Domain must be HTTPS in production

### Privacy & Data Access

**Key Principles**:
1. **Minimal Scopes**: Use narrowest possible API scopes
   - Google: `drive.file` (only files created by app)
   - Dropbox: App folder access
2. **No Server Storage**: Files never touch OrcPub database
3. **User Control**: Users choose what to sync
4. **Explicit Consent**: OAuth consent screen shows exact permissions

---

## Technical Feasibility Assessment

### ✅ Highly Feasible

**Google Drive Integration**:
- **Effort**: Medium (2-3 weeks development)
- **Complexity**: Moderate
- **Risk**: Low
- **Browser Compatibility**: Excellent (Chrome, Firefox, Safari, Edge)
- **Library Support**: Official SDK with good docs

**Dropbox Integration**:
- **Effort**: Low-Medium (1-2 weeks development)
- **Complexity**: Low-Moderate
- **Risk**: Low
- **Browser Compatibility**: Good (with browser support check)
- **Library Support**: Official SDK + Chooser component

### ❌ Not Feasible

**iCloud Drive Integration**:
- No public browser API available
- CloudKit JS doesn't provide file access
- Would require native iOS/macOS app

---

## Implementation Complexity Analysis

### Phase 1: Google Drive Integration (Recommended First)

**Components Needed**:

1. **OAuth Module** (ClojureScript)
   - Initialize Google Identity Services
   - Handle authorization flow
   - Manage token lifecycle
   - Store tokens securely (memory/sessionStorage)
   - **Complexity**: Medium
   - **LOC Estimate**: ~300-400 lines

2. **File Operations Module**
   - Upload .orcbrew files
   - Download .orcbrew files
   - List user's .orcbrew files
   - File picker integration
   - **Complexity**: Low-Medium
   - **LOC Estimate**: ~200-300 lines

3. **UI Components** (Reagent/re-frame)
   - "Connect Google Drive" button
   - Authentication status display
   - File picker/selector
   - Upload/download progress indicators
   - **Complexity**: Low
   - **LOC Estimate**: ~200-250 lines

4. **State Management** (re-frame)
   - Auth state (connected/disconnected)
   - Available cloud files
   - Sync status
   - Error handling
   - **Complexity**: Low
   - **LOC Estimate**: ~150-200 lines

5. **Configuration**
   - Google Cloud Console project setup
   - Environment variables for API keys
   - Domain whitelist configuration
   - **Complexity**: Low (documentation/setup)

**Total Effort Estimate**: 850-1150 lines of code

### Phase 2: Dropbox Integration (Optional)

**Additional Components**:
- Similar structure to Google Drive
- Slightly simpler due to Chooser component
- **Effort Estimate**: 600-800 lines of code

---

## Architecture Proposal

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      OrcPub Browser App                      │
│                     (ClojureScript/React)                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌────────────────────────────────────────────────────┐    │
│  │         Existing Import/Export System              │    │
│  │                                                      │    │
│  │  • File picker (local files)                       │    │
│  │  • Parse .orcbrew (EDN)                            │    │
│  │  • Validate with specs                             │    │
│  │  • Merge into localStorage                         │    │
│  └────────────────────────────────────────────────────┘    │
│                           ▲                                  │
│                           │ (reuse)                          │
│                           │                                  │
│  ┌────────────────────────────────────────────────────┐    │
│  │         NEW: Cloud Storage Integration             │    │
│  │                                                      │    │
│  │  ┌──────────────────────────────────────────────┐ │    │
│  │  │  Cloud Auth Module                           │ │    │
│  │  │  • OAuth 2.0 + PKCE flow                    │ │    │
│  │  │  • Token management                         │ │    │
│  │  │  • Session state                            │ │    │
│  │  └──────────────────────────────────────────────┘ │    │
│  │                                                      │    │
│  │  ┌──────────────────────────────────────────────┐ │    │
│  │  │  File Sync Module                            │ │    │
│  │  │  • List cloud files                         │ │    │
│  │  │  • Upload .orcbrew                          │ │    │
│  │  │  • Download .orcbrew                        │ │    │
│  │  │  • Progress tracking                        │ │    │
│  │  └──────────────────────────────────────────────┘ │    │
│  │                                                      │    │
│  │  ┌──────────────────────────────────────────────┐ │    │
│  │  │  UI Components                               │ │    │
│  │  │  • Connect button                           │ │    │
│  │  │  • File browser                             │ │    │
│  │  │  • Sync controls                            │ │    │
│  │  └──────────────────────────────────────────────┘ │    │
│  └────────────────────────────────────────────────────┘    │
│                           │                                  │
└───────────────────────────┼──────────────────────────────────┘
                            │ OAuth + API calls
                            ▼
         ┌──────────────────────────────────────┐
         │   Cloud Storage Providers            │
         │                                      │
         │  • Google Drive API                  │
         │  • Dropbox API                       │
         └──────────────────────────────────────┘
```

### Data Flow

**Upload Flow**:
1. User clicks "Export to Google Drive"
2. Check auth state → if not connected, trigger OAuth flow
3. User authorizes app (OAuth consent screen)
4. App receives access token
5. Serialize current homebrew data to EDN
6. Create blob from EDN string
7. Upload blob to Google Drive via API
8. Show success notification

**Download Flow**:
1. User clicks "Import from Google Drive"
2. Check auth state → if not connected, trigger OAuth flow
3. Show file picker (Google Drive UI or custom list)
4. User selects .orcbrew file
5. Download file content via API
6. Parse EDN (reuse existing parser)
7. Validate (reuse existing validation)
8. Merge into state (reuse existing merge logic)
9. Save to localStorage

### State Management (re-frame)

**New DB Schema**:
```clojure
{:cloud-storage
 {:google-drive
  {:connected? false
   :access-token nil
   :token-expiry nil
   :user-email nil
   :files []
   :sync-status :idle  ; :idle | :syncing | :error
   :last-sync nil
   :error nil}

  :dropbox
  {:connected? false
   ; similar structure
   }}}
```

**Events**:
- `::cloud/init-google-auth` - Initialize OAuth
- `::cloud/handle-auth-callback` - Process OAuth redirect
- `::cloud/disconnect` - Clear tokens and state
- `::cloud/list-files` - Fetch available files
- `::cloud/upload-homebrew` - Export to cloud
- `::cloud/download-homebrew` - Import from cloud
- `::cloud/refresh-token` - Renew access token

**Subscriptions**:
- `::cloud/connected?` - Auth status
- `::cloud/available-files` - Cloud file list
- `::cloud/sync-status` - Current operation status

---

## Integration Points with Existing Code

### Reusable Components

**From** `/home/user/orcpub/src/cljs/orcpub/dnd/e5/events.cljs`:

1. **Export Logic** (lines 3162-3194):
   - `export-plugin` - Serialization logic
   - `export-plugin-pretty-print` - Formatted EDN
   - **Reuse**: Use same serialization, upload blob to cloud instead of download

2. **Import Logic** (lines 3220-3247):
   - `import-plugin` - EDN parsing and validation
   - `clean-plugin-errors` - Error correction
   - **Reuse**: Download from cloud, then use same parse/validate/merge pipeline

3. **Validation**:
   - Spec validation (`:orcpub.dnd.e5/plugin`, `:orcpub.dnd.e5/plugins`)
   - Progressive import strategy
   - **Reuse**: Identical validation for cloud imports

### New Files Needed

```
src/cljs/orcpub/cloud/
├── core.cljs           # Main cloud integration namespace
├── google_drive.cljs   # Google Drive specific implementation
├── dropbox.cljs        # Dropbox specific implementation
├── oauth.cljs          # OAuth utilities
└── events.cljs         # re-frame events for cloud operations

src/cljs/orcpub/views/
└── cloud_storage.cljs  # UI components for cloud features
```

---

## Deployment Considerations

### Environment Configuration

**Google Drive**:
```bash
# .env or environment variables
GOOGLE_CLIENT_ID=<your-client-id>.apps.googleusercontent.com
GOOGLE_API_KEY=<your-api-key>
```

**Allowed Origins**:
- Development: `http://localhost:8890`
- Production: `https://orcpub.com` (or your domain)

### Build Process

**No changes needed** to existing ClojureScript build - this is pure frontend JavaScript interop.

**External Libraries**:
- Google Identity Services: Loaded via CDN (script tag)
- Google API Client: Loaded via CDN
- Dropbox SDK: Could use CDN or npm package

**Add to** `/home/user/orcpub/src/clj/orcpub/routes/index.clj`:
```html
<script src="https://accounts.google.com/gsi/client" async defer></script>
<script src="https://apis.google.com/js/api.js" async defer></script>
```

---

## Risks & Mitigation

### Technical Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| OAuth implementation complexity | Medium | Medium | Use official libraries, follow examples |
| Browser compatibility issues | Medium | Low | Test across browsers, graceful degradation |
| Token management bugs | High | Medium | Thorough testing, clear error messages |
| API rate limits | Low | Low | Small file sizes, infrequent operations |
| CORS issues | Medium | Low | Proper API configuration |

### User Experience Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Users confused by OAuth flow | Medium | Medium | Clear instructions, tooltips |
| Lost connection state | Low | Medium | Token refresh, reconnect prompts |
| File conflicts (multiple devices) | Medium | Medium | Last-write-wins, show timestamps |
| Privacy concerns | High | Low | Clear privacy messaging, minimal scopes |

### Operational Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Google/Dropbox API changes | Medium | Low | Monitor deprecation notices |
| OAuth credentials exposed | High | Low | Environment variables, no commits |
| Quota exceeded | Low | Low | Monitor usage, user-specific quotas |

---

## Cost Analysis

### Google Drive API

**Pricing**: FREE for most use cases
- 1 billion queries per day (free tier)
- Individual user quotas: 1000 requests per 100 seconds
- OrcPub usage: ~1-10 requests per user session
- **Cost**: $0 (well within free tier)

### Dropbox API

**Pricing**: FREE for basic usage
- Standard API tier: Free
- No query limits for basic file operations
- **Cost**: $0

### Development Cost

**Time Estimate**:
- Google Drive integration: 40-60 hours
- Dropbox integration: 20-30 hours
- Testing & documentation: 20-30 hours
- **Total**: 80-120 hours (~2-3 weeks full-time)

---

## Recommendations

### Phase 1: MVP (Minimum Viable Product)

**Implement Google Drive Only**

Features:
1. ✅ OAuth authentication with Google
2. ✅ Export current homebrew to Google Drive
3. ✅ Import .orcbrew files from Google Drive
4. ✅ Basic file browser (list of .orcbrew files)
5. ✅ Connection status indicator

**Rationale**:
- Most users have Google accounts
- Best API documentation
- Proven browser compatibility
- Can validate approach before expanding

**Timeline**: 2-3 weeks

### Phase 2: Enhancement

**Add Dropbox Support**

Features:
1. ✅ Dropbox OAuth authentication
2. ✅ Same import/export features
3. ✅ Multi-provider UI (choose Google or Dropbox)

**Timeline**: 1-2 weeks additional

### Phase 3: Advanced Features (Future)

**Consider if Phase 1 succeeds**:

1. **Auto-sync**: Automatically save on changes
2. **Conflict Resolution**: Handle simultaneous edits
3. **Version History**: Browse previous versions
4. **Character Sync**: Extend to character data
5. **Shared Libraries**: Collaborative homebrew editing

### Deferred: iCloud Drive

**Reason**: No browser API available
**Alternative**: Could build native iOS app in future

---

## Success Criteria

### Technical Success

- ✅ Users can connect their Google Drive account
- ✅ Users can export .orcbrew files to cloud
- ✅ Users can import .orcbrew files from cloud
- ✅ No data loss during upload/download
- ✅ Works across major browsers (Chrome, Firefox, Safari, Edge)
- ✅ Graceful error handling with clear messages

### User Experience Success

- ✅ OAuth flow completes in < 60 seconds
- ✅ File upload/download completes in < 5 seconds
- ✅ UI is intuitive (no documentation needed)
- ✅ Users feel their data is secure
- ✅ Feature adoption: >20% of active users within 3 months

### Business Success

- ✅ Reduces support requests about data backup
- ✅ Enables multi-device workflows
- ✅ Differentiates OrcPub from competitors
- ✅ No ongoing costs (free tier sufficient)

---

## Next Steps

### Immediate Actions

1. **Prototype OAuth Flow** (1-2 days)
   - Set up Google Cloud Console project
   - Implement basic OAuth in ClojureScript
   - Validate token management

2. **Prototype File Upload** (1-2 days)
   - Upload test .orcbrew file
   - Verify file appears in Google Drive
   - Test file size limits

3. **Prototype File Download** (1-2 days)
   - List files from Google Drive
   - Download and parse .orcbrew
   - Integrate with existing import logic

4. **Review & Decision** (1 day)
   - Demo to stakeholders
   - Gather feedback
   - Decide to proceed or pivot

### If Approved

5. **Full Implementation** (2-3 weeks)
   - Complete all MVP features
   - Comprehensive testing
   - Documentation

6. **Beta Testing** (1-2 weeks)
   - Limited user rollout
   - Gather feedback
   - Fix bugs

7. **Production Release**
   - Feature flag for gradual rollout
   - Monitor usage and errors
   - Iterate based on feedback

---

## Conclusion

### Feasibility: ✅ HIGHLY FEASIBLE

**Google Drive and Dropbox integrations are technically feasible and straightforward** to implement in the OrcPub codebase. The existing import/export infrastructure can be reused, requiring only the addition of OAuth and cloud API layers.

**iCloud Drive is NOT feasible** due to lack of browser API.

### Recommendation: ✅ PROCEED

Start with **Google Drive integration as MVP**, then expand to Dropbox if successful. This approach:
- ✅ Solves real user problems (backup, multi-device access)
- ✅ Leverages existing code effectively
- ✅ Has low technical and financial risk
- ✅ Can be implemented in 2-3 weeks
- ✅ Provides competitive differentiation

### Risk Level: 🟢 LOW

The primary risks are around OAuth complexity and user experience, both of which can be mitigated through careful implementation and testing.

---

## Appendix: Reference Links

### Google Drive API
- [JavaScript Quickstart](https://developers.google.com/drive/api/quickstart/js)
- [Upload Files Guide](https://developers.google.com/drive/api/guides/manage-uploads)
- [OAuth 2.0 for Client-side Web Apps](https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow)

### Dropbox API
- [JavaScript SDK](https://github.com/dropbox/dropbox-sdk-js)
- [Dropbox Chooser](https://www.dropbox.com/developers/chooser)
- [JavaScript Documentation](https://www.dropbox.com/developers/documentation/javascript)

### OAuth 2.0 Security
- [OAuth 2.0 Security Best Practices - OWASP](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html)
- [Authorization Code Flow with PKCE](https://auth0.com/docs/get-started/authentication-and-authorization-flow/authorization-code-flow-with-pkce)
- [OAuth 2.0 for Browser-Based Applications](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps)
