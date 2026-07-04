# Fable Handoff: AHD Android App — MVP Completion

## Audit Summary (pre-loaded knowledge — do not re-discover)

The Android wrapper **already builds and generates a debug APK successfully** (`./gradlew assembleDebug` → BUILD SUCCESSFUL, 6.9MB APK). The `cap sync android` step works cleanly with 4 plugins registered. All Gradle dependencies resolve. Tests pass.

What's missing is **runtime behavior polish** — 3 concrete gaps make the app feel broken at runtime:

1. **Back button exits app immediately** — `MainActivity` doesn't override `onBackPressed()`. Pressing back should step through WebView history, only exiting when exhausted.
2. **Offline overlay never fires on real network loss** — The overlay is hooked to `WebViewListener.onReceivedError` only, which fires on *page load failures*. If the user is already on the dashboard and the network drops, nothing happens. The `@capacitor/network` plugin is installed but not wired to a native `ConnectivityManager.NetworkCallback`.
3. **App icon + splash are Capacitor defaults** — Not AHD-branded. Placeholder PNGs exist at all densities (they display), but they're the Capacitor triangle, not the AHD capitol dome.

Everything else (OAuth `allowNavigation`, cookie persistence, `CookieManager.flush()`, `DomStorage`, splash screen config, Gradle SDK targeting, `google-services.json` optional gating) is already done and correct.

## Deliverables

Fix the 3 gaps above. Do **not** refactor, restructure, or touch working code unless one of the gaps requires it. After each fix, verify with `cd android && ./gradlew assembleDebug`.

Final deliverable list:
1. `MainActivity.java` — add `onBackPressed()` override for WebView history navigation
2. `MainActivity.java` — add `ConnectivityManager.NetworkCallback` to drive the offline overlay
3. Replace `mipmap-*/ic_launcher*.png` (15 files across 5 densities) and `drawable-*/splash.png` (10 files) with AHD-branded assets (generate programmatically — see Icon section)
4. `./gradlew assembleDebug` → BUILD SUCCESSFUL
5. Report the APK path and size

---

## Gap 1: Back Button WebView History Navigation

**File:** `android/app/src/main/java/com/ahousedivided/app/MainActivity.java`

**Problem:** No `onBackPressed()` override exists. Android's default back behavior finishes the Activity immediately, so users can't navigate back through the game's page history inside the WebView.

**Fix:** Add this method after `onPause()` (~line 102):

```java
@Override
public void onBackPressed() {
    Bridge bridge = getBridge();
    if (bridge != null) {
        WebView webView = bridge.getWebView();
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
    }
    super.onBackPressed();
}
```

**Verification:** After the app loads and the user navigates to sub-pages (e.g., Dashboard → Politicians), pressing back should go to the previous page, not exit. Only when at the WebView's first page should back exit the app.

---

## Gap 2: Real Network Loss Detection

**File:** `android/app/src/main/java/com/ahousedivided/app/MainActivity.java`

**Problem:** The offline overlay is triggered only by `WebViewListener.onReceivedError()`, which fires when a *page load* fails (HTTP error or DNS failure). Once the site is loaded and the user is browsing, a network drop (WiFi off, airplane mode) produces no visual feedback — the WebView just silently stops loading new pages and eventually times out.

**Fix:** Register a `ConnectivityManager.NetworkCallback` in `setupWebView()` that calls `showOfflineOverlay()` / `hideOfflineOverlay()`. Unregister it in `onDestroy()`.

**Exact additions needed:**

### Import block (add these):
```java
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
```

### New fields (add after `private View offlineOverlay;`):
```java
private ConnectivityManager.NetworkCallback networkCallback;
private ConnectivityManager connectivityManager;
```

### In `setupWebView()`, add at the END of the method (after the `bridge.addWebViewListener(...)` block):
```java
// ── Real network loss detection ──────────────────────────
connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
NetworkRequest request = new NetworkRequest.Builder()
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .build();

networkCallback = new ConnectivityManager.NetworkCallback() {
    @Override
    public void onAvailable(Network network) {
        runOnUiThread(() -> hideOfflineOverlay());
    }

    @Override
    public void onLost(Network network) {
        runOnUiThread(() -> showOfflineOverlay());
    }

    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
        boolean hasInternet = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        runOnUiThread(() -> {
            if (hasInternet) hideOfflineOverlay();
            else showOfflineOverlay();
        });
    }
};

connectivityManager.registerNetworkCallback(request, networkCallback);
```

### New `onDestroy()` override (add before the final closing brace of the class):
```java
@Override
protected void onDestroy() {
    super.onDestroy();
    if (connectivityManager != null && networkCallback != null) {
        connectivityManager.unregisterNetworkCallback(networkCallback);
    }
}
```

**Verification:** With the app running, toggle airplane mode → offline overlay should appear. Toggle back → overlay should disappear and the WebView should be usable. The WebViewListener error-based overlay should still work for initial load failures.

**Pitfall — `hasCapability` on API < 23:** Our `minSdkVersion` is 24, so `hasCapability()` is safe. No API-level guard needed.

---

## Gap 3: AHD-Branded App Icon & Splash

**Files affected:** 25 PNG files across `mipmap-*/` (icon) and `drawable-*/` (splash).

**Problem:** All icon and splash PNGs are the default Capacitor blue triangle on white. The app works but looks unprofessional.

**Fix:** Generate AHD-branded replacements programmatically using Python/Pillow. The AHD brand is dark slate (`#0F172A`) with blue accents (`#2563EB`).

### Icon spec
All 5 densities of 3 icon types (launcher, launcher_round, launcher_foreground):

```
mipmap-mdpi/    48×48
mipmap-hdpi/    72×72
mipmap-xhdpi/   96×96
mipmap-xxhdpi/  144×144
mipmap-xxxhdpi/ 192×192
```

**Design:** Solid `#0F172A` background with a simplified white capitol dome silhouette centered (use basic Pillow shapes — rectangle base + triangle roof + small circle on top). The foreground variant should be the dome on a transparent background (for adaptive icons).

### Splash spec
10 files (portrait + landscape at 5 densities each):

```
drawable-port-mdpi/     320×480
drawable-port-hdpi/     480×800
drawable-port-xhdpi/    720×1280
drawable-port-xxhdpi/   960×1600
drawable-port-xxxhdpi/  1280×1920

drawable-land-mdpi/     480×320
drawable-land-hdpi/     800×480
drawable-land-xhdpi/    1280×720
drawable-land-xxhdpi/   1600×960
drawable-land-xxxhdpi/  1920×1280
```

**Design:** Solid `#0F172A` background with centered "A HOUSE DIVIDED" text in white (small, centered vertically and horizontally).

### Implementation
Use Pillow to generate all 25 files in one script. The script should:
1. Create icon PNGs (launcher = filled bg + dome, round = same but circular mask, foreground = dome only on transparent)
2. Create splash PNGs (dark bg + centered text)
3. Overwrite the existing placeholder files

After generating, verify: `cd android && ./gradlew assembleDebug` must still succeed.

---

## What NOT to Touch

- ✅ `capacitor.config.ts` — OAuth `allowNavigation` is correct
- ✅ `capacitor.build.gradle` / `capacitor.settings.gradle` — auto-generated by `cap sync`
- ✅ `variables.gradle` — SDK versions are correct (compileSdk 36, targetSdk 36, minSdk 24)
- ✅ `app/build.gradle` — dependency versions and `google-services.json` optional gating are correct
- ✅ `AndroidManifest.xml` — permissions and activity config are complete
- ✅ `src/push-notifications.ts` — Phase 2 stub, not for MVP
- ✅ `www/index.html` — minimal placeholder is correct for remote-URL mode
- ✅ WebView cookie persistence (`CookieManager.flush()`, `setAcceptCookie`, `DomStorage`) — done
- ✅ Splash screen config in `capacitor.config.ts` — done
- ✅ `colors.xml`, `styles.xml`, `strings.xml` — done
- ✅ Offline overlay layout code (`showOfflineOverlay`, `hideOfflineOverlay`) — done, just needs the network trigger
- ✅ Custom Tabs utility (`openInCustomTabs`) — done, works as is

## MVPs NOT in Scope (Phase 2 / future)

- Push notifications — requires Firebase project + game server endpoints
- Deep linking (`ahd://` scheme)
- Biometric auth
- File download handling
- Release signing / Play Store publishing

## Verification (run after all changes)

```bash
cd /root/ahd-android
npx cap sync android
cd android
export ANDROID_HOME=/root/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew assembleDebug
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

Expected: BUILD SUCCESSFUL, APK present. Report the APK size.
