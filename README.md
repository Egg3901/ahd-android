# A House Divided — Android App

Thin Android wrapper for **A House Divided**, a turn-based political simulation game at [ahousedividedgame.com](https://www.ahousedividedgame.com). Built with [Capacitor](https://capacitorjs.com) — loads the live production site in a WebView. No game logic, auth, or UI is reimplemented natively.

**Docs:** [CHANGELOG](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Push preferences](docs/push-notification-preferences.md)

## Why a Remote-URL Wrapper?

The game's auth uses a custom JWT stored in an HTTP-only cookie scoped to `.ahousedividedgame.com`. The WebView must load the live origin so cookies are sent automatically. Bundling the web app locally would break auth — the cookie domain wouldn't match. This is a deliberate architectural decision, not a shortcut.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Node.js | ≥ 22 | Required by the Capacitor 8 CLI |
| Android Studio | Koala 2024.1.1+ | Includes Android SDK |
| JDK | 21 | Required by Capacitor 8 / Gradle 8 |
| Gradle | (managed by wrapper) | `gradlew` uses the pinned version |

Install Android Studio → open SDK Manager → install:
- Android SDK Platform 36 (Android 16)
- Android SDK Build-Tools 36.0.0
- Command-line tools

Set environment variables:
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
```

## Project Structure

```
ahd-android/
├── capacitor.config.ts        # Capacitor config (remote URL, plugins)
├── package.json                # npm scripts, Capacitor deps
├── www/
│   └── index.html              # Minimal placeholder (overridden by remote URL)
├── src/
│   └── push-notifications.ts   # Phase 2: FCM push opt-in/out API
└── android/                    # Native Android project (Capacitor-generated)
    ├── app/
    │   ├── build.gradle
    │   ├── src/main/
    │   │   ├── AndroidManifest.xml
    │   │   ├── java/com/ahousedivided/app/
    │   │   │   └── MainActivity.java    # Custom: cookies, offline overlay, external links
    │   │   └── res/
    │   │       ├── drawable/ic_launcher_background.xml     # Dark slate background
    │   │       ├── drawable-*/splash.png                   # Splash placeholders
    │   │       ├── mipmap-anydpi-v26/ic_launcher.xml       # Adaptive icon (bg + fg)
    │   │       ├── mipmap-*/ic_launcher*.png               # App icons + foreground (replace with real)
    │   │       └── values/
    │   │           ├── strings.xml
    │   │           ├── styles.xml
    │   │           └── colors.xml
    └── build.gradle
```

## Build & Run

### First-time setup
```bash
# Install dependencies
npm install

# Sync web assets + plugins to native project
npx cap sync android
```

### Build debug APK
```bash
# From project root
npm run build:apk
# or:
cd android && ./gradlew assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

### Install on device/emulator
```bash
# Install debug APK on connected device (requires adb)
npm run build:run
# or:
cd android && ./gradlew installDebug
```

### Open in Android Studio
```bash
npx cap open android
```

### Run on a physical device
1. Enable USB debugging on your Android device (Settings → Developer Options)
2. Connect via USB
3. Run `adb devices` to verify connection
4. `npm run build:run` installs and launches the app

### Run on emulator
1. Create an AVD (Android Virtual Device) in Android Studio for API 24+ (minSdk)
2. Start the emulator
3. `npm run build:run` or run from Android Studio

## Swapping in Real Icons & Splash

### App Icons
Replace the PNG files in `android/app/src/main/res/mipmap-*/`:
```
mipmap-mdpi/    ic_launcher.png       (48×48)
mipmap-hdpi/    ic_launcher.png       (72×72)
mipmap-xhdpi/   ic_launcher.png       (96×96)
mipmap-xxhdpi/  ic_launcher.png      (144×144)
mipmap-xxxhdpi/ ic_launcher.png      (192×192)
```
Same sizes for `ic_launcher_round.png` and `ic_launcher_foreground.png`.

**Recommended:** Use Android Studio's Image Asset Studio (right-click `res/` → New → Image Asset) to auto-generate all densities from a single source image.

The adaptive icon is defined in `mipmap-anydpi-v26/ic_launcher.xml`: the foreground is a PNG (`mipmap-*/ic_launcher_foreground.png`) and the background is the solid color `@color/ic_launcher_background` (`#0F172A`, slate-900).

### Splash Screen
Splash screen is configured in `capacitor.config.ts`:
```ts
SplashScreen: {
  launchShowDuration: 1500,  // ms
  backgroundColor: '#0f172a',
  showSpinner: false,
  androidScaleType: 'CENTER_INSIDE',
}
```

Replace the `drawable-*/splash.png` files with your splash image. Use Android Studio's Image Asset Studio to generate all densities.

## Security

Security controls, known trade-offs, and vulnerability reporting are documented in
[SECURITY.md](SECURITY.md). Summary:

- HTTPS-only loading with cleartext blocked at multiple layers
- `allowBackup="false"` to reduce cookie extraction via backups
- Release builds use R8 minification and resource shrinking
- WebView remote debugging enabled only on debug builds
- Non-allowlisted URLs open in Custom Tabs instead of the WebView

Run dependency checks locally before releases:

```bash
npm run audit
npm run typecheck
```

## Releases and versioning

Versions follow [Semantic Versioning](https://semver.org/). [CHANGELOG.md](CHANGELOG.md) is
generated by [release-please](https://github.com/googleapis/release-please) from
[Conventional Commits](https://www.conventionalcommits.org/) on `master`.

**Release flow:**

1. Merge features/fixes to `master` using conventional commit messages.
2. Release Please opens a **Release PR** updating `CHANGELOG.md` and `package.json`.
3. Merge the Release PR → GitHub Release + git tag (e.g. `v1.2.0`).
4. Android `versionName` and `versionCode` are derived from `package.json` automatically
   at Gradle build time.

See [CONTRIBUTING.md](CONTRIBUTING.md) for commit message format.

## OAuth & WebView Behavior

### How it works
The app loads `https://www.ahousedividedgame.com` in a Capacitor WebView. Capacitor's `BridgeWebViewClient` intercepts URL navigation:

- **Same-origin URLs** (anything on `ahousedividedgame.com`): load inside the WebView — session cookie is sent automatically.
- **OAuth provider URLs** (`discord.com`, `accounts.google.com`, `www.google.com`): configured in `server.allowNavigation` in `capacitor.config.ts` — these load **inside the WebView** so the OAuth callback redirect sets the session cookie in the WebView's cookie jar, not the system browser's. This is critical: if OAuth opened in the system browser, the callback redirect would set the cookie there, and the user would never be logged in inside the app.
- **All other external URLs**: opened in the system browser via `Intent.ACTION_VIEW`.

### OAuth flow walkthrough (Discord)
1. User taps "Sign in with Discord" → WebView navigates to `https://discord.com/oauth2/authorize?...`
2. `allowNavigation` matches `discord.com` → page loads in WebView
3. User authorizes on Discord → Discord redirects to `https://www.ahousedividedgame.com/api/auth/callback/discord?...`
4. This URL matches the app origin → loads in WebView → server sets JWT cookie on `.ahousedividedgame.com`
5. `CookieManager` persists the cookie → user is logged in across restarts

### Custom Tabs
`MainActivity.java` includes an `openInCustomTabs()` utility using AndroidX Custom Tabs. The default Capacitor behavior already handles external links via `Intent.ACTION_VIEW` (system browser). Custom Tabs can be wired in by overriding the WebViewClient if a smoother in-app browser experience is desired for non-OAuth external links.

## Cookie / Session Persistence

The game uses a custom JWT stored in an HTTP-only cookie scoped to `.ahousedividedgame.com` with a 7-day expiry.

`MainActivity.java` ensures cookies persist across app restarts:
- `CookieManager.getInstance().setAcceptCookie(true)` — enable cookie storage
- `CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)` — allow third-party cookies (needed for some OAuth flows)
- `CookieManager.getInstance().flush()` on `onPause()` — force-persist cookies to disk when app goes to background
- `setDomStorageEnabled(true)` — enable localStorage/sessionStorage

Android's `CookieManager` persists cookies to disk automatically when the WebView is destroyed/paused, but the explicit `flush()` call ensures persistence even on crash.

## Offline State

When the WebView fails to load (network unreachable, server down), a full-screen overlay appears:
- Dark slate background matching the app theme
- "Can't Reach the Server" title
- "A House Divided requires an internet connection." subtitle
- **Retry** button — reloads the WebView

This is implemented in `MainActivity.java` via a `WebViewListener` that listens for `onReceivedError`. The overlay is a programmatic `LinearLayout` added to the root `FrameLayout`.

## Pull to Refresh

Swipe down from the top of the page to reload, matching browser refresh on the web version. The refresh indicator uses the app's blue accent (`#2563EB`) on a slate background. Pull-to-refresh only activates when the page is scrolled to the top, so it won't interfere with normal scrolling.

## Phase 2: Push Notifications

### Opt-in / opt-out (required)

Notifications are **off by default**. The native shell must never auto-register
on launch. The game web app owns the Settings toggle and calls this API:

```ts
const push = window.AHD_PushNotifications;

// Drive the Settings toggle from product preference + OS permission
const status = await push.getStatus();
// status.optedIn === false by default

// User turns the toggle ON
const enabled = await push.enable();
if (!enabled.ok && enabled.reason === 'permission_denied') {
  // Keep toggle off; optionally deep-link to system notification settings
}

// User turns the toggle OFF
await push.disable();

// Cold start only — refreshes the FCM token if already opted in; never prompts
await push.syncIfOptedIn();
```

| Call | Behavior |
|------|----------|
| `getStatus()` | Returns `{ optedIn, permission, hasToken, isNative }` |
| `enable()` | Requests OS permission → FCM register → `POST /api/push/register` with `enabled: true` |
| `disable()` | `POST /api/push/unregister` with `enabled: false` → FCM unregister → clears local preference |
| `syncIfOptedIn()` | No-op unless previously opted in **and** OS permission still granted |

If the player denies the system prompt, `enable()` fails with
`permission_denied` and leaves `optedIn` false. If they later revoke OS
permission, `syncIfOptedIn()` clears the local preference so the toggle shows
off (no re-prompt).

See [docs/push-notification-preferences.md](docs/push-notification-preferences.md)
for the full client/server contract.

### What's done
- `@capacitor/push-notifications` plugin installed and synced
- `src/push-notifications.ts` — **opt-in/out preference API** (`enable` /
  `disable` / `getStatus` / `syncIfOptedIn`). Note: this file is **not yet
  wired into the running app**. It compiles to `dist/`, but nothing in
  `www/index.html` loads it and there is no injection step. To activate it,
  either bundle/import it from the web app or inject it via the Capacitor
  bridge. Until then it serves as the reference implementation.
- `google-services.json` integration is scaffolded in `android/app/build.gradle` (auto-applies the Google Services plugin when the file is present)

### What's needed to complete
1. **Create a Firebase project** → [console.firebase.google.com](https://console.firebase.google.com)
2. **Add an Android app** to the Firebase project with package name `com.ahousedivided.app`
3. **Download `google-services.json`** → place in `android/app/`
4. **Create a service account** → Firebase Console → Project Settings → Service Accounts → "Generate new private key". The legacy "Server Key" was decommissioned in June 2024 and no longer works.
5. **Set the service-account credentials** on the game server (e.g. `GOOGLE_APPLICATION_CREDENTIALS` pointing at the JSON, or the Firebase Admin SDK). The server mints short-lived OAuth2 access tokens from these to call the FCM HTTP v1 API.
6. **Implement server-side endpoints** (see below)
7. **Add a Settings → Notifications toggle** in the web app that calls `enable()` / `disable()` (default off)

### Server-side endpoints
The game server needs two new API endpoints:

```http
POST /api/push/register
Authorization: <JWT cookie>
Content-Type: application/json

{ "token": "<FCM_DEVICE_TOKEN>", "platform": "android", "enabled": true }
```
Stores the token in the user document: `user.pushTokens: [{ token, platform, createdAt }]`
and sets `user.settings.pushNotifications = true`.

```http
POST /api/push/unregister
Authorization: <JWT cookie>
Content-Type: application/json

{ "token": "<FCM_DEVICE_TOKEN>", "enabled": false }
```
Removes the token from the user document and sets
`user.settings.pushNotifications = false`.

### Server-side cron hook
In the hourly turn processor, after turn advancement. The simplest correct
approach is the Firebase Admin SDK, which handles OAuth2 token minting and
multicast batching (up to 500 tokens per `sendEachForMulticast` call) for you:

```javascript
import { initializeApp, cert } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';

initializeApp({ credential: cert(process.env.GOOGLE_APPLICATION_CREDENTIALS) });

// 1. Get users with push tokens who opted in
const users = await db.collection('users').find({
  'pushTokens.0': { $exists: true },
  'settings.pushNotifications': true
}).toArray();

const tokens = users.flatMap(u => u.pushTokens.map(t => t.token));

// 2. Send via FCM HTTP v1 API (Admin SDK handles auth + batching).
//    Note: all `data` values must be strings under the v1 API.
const res = await getMessaging().sendEachForMulticast({
  tokens,
  notification: {
    title: 'Your Turn',
    body: 'A new turn has started in A House Divided.',
  },
  data: {
    type: 'turn_start',
    turn: String(currentTurn),
    deep_link: 'https://www.ahousedividedgame.com/dashboard',
  },
});

// 3. Clean up invalid tokens from the per-message responses
//    (errors with code messaging/registration-token-not-registered are stale).
res.responses.forEach((r, i) => {
  if (!r.success && r.error?.code === 'messaging/registration-token-not-registered') {
    // remove tokens[i] from the owning user document
  }
});
```

If you call the raw HTTP endpoint instead of the Admin SDK, POST to
`https://fcm.googleapis.com/v1/projects/<PROJECT_ID>/messages:send` with an
`Authorization: Bearer <oauth2-access-token>` header (one message per request).

## Decisions & Limitations

### This is a remote-URL wrapper, not a bundled build
**Decision:** The app loads `https://www.ahousedividedgame.com` in a WebView instead of bundling the Next.js build output.

**Why:** Auth is a domain-scoped JWT cookie on `.ahousedividedgame.com`. A bundled build would require either reimplementing auth (rejected — complex, fragile, duplicated logic) or running a local server with cookie domain manipulation (rejected — unstable, breaks OAuth redirects). Loading the live origin is the cleanest path that preserves the existing auth flow 100%.

**Implications:**
- Requires internet connectivity (no offline mode — the game is server-driven anyway)
- First load shows a blank screen until the page renders (mitigated by splash screen)
- App size is tiny (~5MB) — no bundled JS/CSS
- Updates ship automatically when the web app deploys (no app store update needed for content changes)

### No native UI
No Kotlin/Compose UI, no React Native. The wrapper is as thin as possible — just a WebView with cookie persistence, back button handling, offline detection, and external link interception.

### Limitations
- **Push notifications** are stubbed (Phase 2). Requires Firebase project setup + server-side implementation.
- **Biometric auth** is not implemented. Could be added via `@capacitor-community/biometric-auth` if desired.
- **Deep linking** (e.g., `ahd://politicians/123`) is not configured. Could be added via Android intent filters + Capacitor App plugin's `appUrlOpen` event.
- **File downloads** are not handled. If the game adds downloadable content, a download listener needs to be added to the WebView.

## Google Play Store Checklist

### Before publishing
- [ ] **Signing key**: Generate a release keystore and configure signing via
  `android/keystore.properties` (see [keystore.properties.example](android/keystore.properties.example)):
  ```bash
  keytool -genkey -v -keystore android/keystore/ahd-release.keystore -alias ahd \
    -keyalg RSA -keysize 2048 -validity 10000
  cp android/keystore.properties.example android/keystore.properties
  # Edit keystore.properties with real passwords and paths
  ```
  `android/app/build.gradle` reads `android/keystore.properties` when present and signs
  release builds automatically. CI builds unsigned release-capable APKs when the file
  is absent.

- [ ] **Release hardening**: Release builds enable R8 minification and resource shrinking
  with Capacitor keep rules in `proguard-rules.pro`.

- [ ] **Privacy policy URL**: Required by Play Store. Host at `https://www.ahousedividedgame.com/privacy` and add to Play Console.

- [ ] **App content rating**: Fill out the content rating questionnaire in Play Console. Political simulation — likely "Teen" (simulate voting, political content).

- [ ] **Store listing assets**:
  - App icon: 512×512 PNG
  - Feature graphic: 1024×500 PNG
  - Phone screenshots: minimum 2, recommended 3–8 (min 320px, max 3840px)
  - App description (short + full)

- [ ] **Data safety form**: Declare data collection. The app itself collects no data — all data is handled by the web app's existing privacy policy. The wrapper only stores session cookies.

- [ ] **Target API level**: Play Store requires `targetSdkVersion` to meet the current requirement (API 36 as of 2025). Already configured in `variables.gradle`.

- [ ] **App signing**: Enroll in Play App Signing (Google holds the signing key, you upload a release APK signed with your upload key).

- [ ] **Internal testing track**: Upload the debug APK (or a signed release build) to the internal testing track first. Add testers, verify the app loads the game and login works.

- [ ] **Review the WebView policy**: Google Play requires apps using WebView to use an up-to-date WebView. Since we use the system WebView (not a bundled one), this is automatically satisfied — the user's Chrome WebView updates via Play Store.

### Build a release APK/AAB
```bash
# Generate signed APK
cd android && ./gradlew assembleRelease

# Generate App Bundle (required for new Play Store uploads)
cd android && ./gradlew bundleRelease
```

Output:
- APK: `android/app/build/outputs/apk/release/app-release.apk`
- AAB: `android/app/build/outputs/bundle/release/app-release.aab`