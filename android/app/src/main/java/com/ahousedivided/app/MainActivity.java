package com.ahousedivided.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.view.GestureDetector;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;
import com.getcapacitor.Logger;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "AHD-MainActivity";
    private static final String APP_ORIGIN = "ahousedividedgame.com";
    // Server switcher (testers/devs): long-press the top-left corner to pick
    // Main vs Sandbox. Persisted across launches. Both are *.ahousedividedgame.com
    // so isAllowedHost() keeps them in the WebView and the auth cookie is shared.
    private static final String PREFS = "ahd_prefs";
    private static final String PREF_SERVER = "server_env"; // "main" | "sandbox"
    private static final String MAIN_URL = "https://ahousedividedgame.com";
    private static final String SANDBOX_URL = "https://sandbox.ahousedividedgame.com";
    private GestureDetector serverSwitchDetector;
    // Reload when returning from background after this long — the game is
    // turn-based, so a stale page can show a turn-old state.
    private static final long STALE_SESSION_MS = 30 * 60 * 1000;
    private static final long BACK_EXIT_WINDOW_MS = 2000;
    // How long the default network must stay bad before we cover the game
    // with the offline overlay. OEM power management (and WiFi<->cellular
    // handoffs) flap the VALIDATED capability for a second or two while the
    // page underneath is perfectly fine.
    private static final long OFFLINE_OVERLAY_DEBOUNCE_MS = 3000;

    private View offlineOverlay;
    private ViewGroup contentRoot;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingOfflineShow;
    // Set only when the page document itself failed to load — the one case
    // where the WebView content is dead and a reload is required.
    private boolean mainFrameFailed = false;
    private long lastBackPressMs = 0;
    private long pausedAtMs = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Match system bars to the game's slate background
        getWindow().setStatusBarColor(0xFF0F172A);
        getWindow().setNavigationBarColor(0xFF0F172A);
    }

    @Override
    protected void load() {
        super.load();
        setupCookies();
        setupPullToRefresh();
        setupWebViewListeners();
        setupExternalLinkHandling();
        setupNetworkMonitoring();
        setupServerSwitchGesture();
        applyServerPreference();

        // Remote inspection from chrome://inspect (debuggable builds only)
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private void setupCookies() {
        WebView webView = getBridge().getWebView();
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        cm.flush();
        contentRoot = (ViewGroup) webView.getParent();
    }

    private void setupPullToRefresh() {
        WebView webView = getBridge().getWebView();
        if (webView == null || contentRoot == null) return;

        swipeRefreshLayout = new ZoomFriendlySwipeRefreshLayout(this);
        swipeRefreshLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefreshLayout.setColorSchemeColors(0xFF2563EB);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(0xFF0F172A);
        // Let the page's own touch handlers (map pan/zoom surfaces call
        // preventDefault) veto the pull-to-refresh gesture.
        swipeRefreshLayout.setLegacyRequestDisallowInterceptTouchEventEnabled(true);

        ViewGroup.LayoutParams webViewParams = webView.getLayoutParams();
        contentRoot.removeView(webView);
        swipeRefreshLayout.addView(webView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        contentRoot.addView(swipeRefreshLayout, webViewParams);

        // Only pull-to-refresh when scrolled to the top of the page.
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) ->
                webView.canScrollVertically(-1));

        swipeRefreshLayout.setOnRefreshListener(() -> {
            WebView wv = getBridge().getWebView();
            if (wv != null) {
                wv.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void stopRefreshIndicator() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void setupWebViewListeners() {
        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageStarted(WebView view) {
                hideOfflineOverlay();
            }

            @Override
            public void onPageLoaded(WebView view) {
                mainFrameFailed = false;
                hideOfflineOverlay();
                CookieManager.getInstance().flush();
                stopRefreshIndicator();
            }

            @Override
            public void onReceivedError(WebView view) {
                // Capacitor fires this for EVERY failed resource — a blocked
                // analytics beacon or an aborted prefetch included — with no
                // frame or error-code detail. Never treat it as "offline";
                // the real signal is the main-frame check in the
                // BridgeWebViewClient override below.
                stopRefreshIndicator();
            }
        });
    }

    // Hosts in allowNavigation (app origin + OAuth providers) must stay in
    // the WebView so the session cookie lands in its jar; everything else
    // gets a Custom Tab instead of a cold browser hand-off.
    private void setupExternalLinkHandling() {
        Bridge bridge = getBridge();
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                String scheme = url.getScheme();
                if (("http".equals(scheme) || "https".equals(scheme)) && !isAllowedHost(url.getHost())) {
                    openInCustomTabs(url);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, request);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // Only a failed page document means the game is unreachable.
                // Subresource failures (ads/analytics blocked by DNS filters,
                // a flaky image, a cancelled fetch) must not cover a working
                // page with the offline screen.
                if (request == null || !request.isForMainFrame()) return;
                CharSequence rawDesc = error != null ? error.getDescription() : null;
                String desc = rawDesc != null ? rawDesc.toString() : "";
                // A superseded navigation (tapping a link mid-load, the
                // client-side router taking over) surfaces as ERR_ABORTED —
                // not a connectivity failure.
                if (desc.contains("ERR_ABORTED")) return;
                Logger.debug(TAG, "Main-frame load failed: " + desc);
                mainFrameFailed = true;
                stopRefreshIndicator();
                showOfflineOverlay();
            }
        });
    }

    // The WebViewListener only catches page-load failures; this catches
    // connectivity drops while the game is idle. We track the *default*
    // active network rather than every network, so a WiFi->cellular handoff
    // (WiFi's onLost fires while cellular is already carrying traffic) no
    // longer flashes a spurious offline overlay. We also require the
    // VALIDATED capability so a captive-portal / no-real-internet link is
    // treated as offline instead of falsely "connected".
    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                // Don't hide the overlay yet — wait for onCapabilitiesChanged
                // to confirm the default network actually reaches the internet.
            }

            @Override
            public void onLost(Network network) {
                // Fired when the default network drops — including the moment
                // of a WiFi->cellular switch, so debounce before covering the
                // game.
                scheduleOfflineOverlay();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                boolean online = caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                if (online) {
                    cancelPendingOfflineOverlay();
                    runOnUiThread(() -> {
                        hideOfflineOverlay();
                        // Reload only when the page document itself died —
                        // an intact page survives a connectivity blip and a
                        // forced reload would dump the player's scroll/form
                        // state at random.
                        if (mainFrameFailed && getBridge() != null && getBridge().getWebView() != null) {
                            mainFrameFailed = false;
                            getBridge().getWebView().reload();
                        }
                    });
                } else {
                    scheduleOfflineOverlay();
                }
            }
        };

        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private void scheduleOfflineOverlay() {
        mainHandler.post(() -> {
            if (pendingOfflineShow != null) return; // already counting down
            pendingOfflineShow = () -> {
                pendingOfflineShow = null;
                showOfflineOverlay();
            };
            mainHandler.postDelayed(pendingOfflineShow, OFFLINE_OVERLAY_DEBOUNCE_MS);
        });
    }

    private void cancelPendingOfflineOverlay() {
        mainHandler.post(() -> {
            if (pendingOfflineShow != null) {
                mainHandler.removeCallbacks(pendingOfflineShow);
                pendingOfflineShow = null;
            }
        });
    }

    private boolean isAllowedHost(String host) {
        if (host == null) return false;
        if (host.equals(APP_ORIGIN) || host.endsWith("." + APP_ORIGIN)) return true;
        Bridge bridge = getBridge();
        String[] allowed = bridge != null ? bridge.getConfig().getAllowNavigation() : null;
        if (allowed != null) {
            for (String entry : allowed) {
                String e = entry.startsWith("*.") ? entry.substring(2) : entry;
                if (host.equals(e) || host.endsWith("." + e)) return true;
            }
        }
        return false;
    }

    private void openInCustomTabs(Uri url) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setShowTitle(true);
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        customTabsIntent.launchUrl(this, url);
    }

    private void showOfflineOverlay() {
        runOnUiThread(() -> {
            if (offlineOverlay != null) {
                offlineOverlay.setVisibility(View.VISIBLE);
                return;
            }

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            layout.setPadding(dp(32), dp(32), dp(32), dp(32));
            layout.setBackgroundColor(0xFF0F172A);

            TextView title = new TextView(this);
            title.setText("Can't Reach the Server");
            title.setTextColor(0xFF94A3B8);
            title.setTextSize(20);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, 0, 0, dp(8));

            TextView subtitle = new TextView(this);
            subtitle.setText("A House Divided requires an internet connection.\nCheck your connection and try again.");
            subtitle.setTextColor(0xFF64748B);
            subtitle.setTextSize(14);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setPadding(0, 0, 0, dp(24));

            Button retry = new Button(this);
            retry.setText("Retry");
            retry.setTextColor(0xFFFFFFFF);
            retry.setBackgroundColor(0xFF2563EB);
            retry.setPadding(dp(24), dp(12), dp(24), dp(12));
            retry.setOnClickListener(v -> {
                hideOfflineOverlay();
                mainFrameFailed = false;
                WebView wv = getBridge().getWebView();
                if (wv != null) wv.reload();
            });

            layout.addView(title);
            layout.addView(subtitle);
            layout.addView(retry);

            offlineOverlay = layout;
            if (contentRoot != null) {
                contentRoot.addView(offlineOverlay, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
        });
    }

    private void hideOfflineOverlay() {
        runOnUiThread(() -> {
            if (offlineOverlay != null) offlineOverlay.setVisibility(View.GONE);
        });
    }

    // ── Server switcher (Main ↔ Sandbox) ──────────────────────────────────

    private boolean isSandbox() {
        return "sandbox".equals(
                getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_SERVER, "main"));
    }

    // If the saved preference is Sandbox but the app booted on the default (Main)
    // origin from capacitor.config, switch the WebView over. No-op on Main.
    private void applyServerPreference() {
        if (!isSandbox()) return;
        WebView wv = getBridge().getWebView();
        if (wv != null) wv.loadUrl(SANDBOX_URL);
    }

    // Detected via dispatchTouchEvent so it never intercepts the WebView's own
    // touch handling — a long-press localized to the top-left corner opens the
    // switcher. Corner-scoped so normal long-presses in the game are unaffected.
    private void setupServerSwitchGesture() {
        serverSwitchDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(MotionEvent e) {
                        View decor = getWindow().getDecorView();
                        if (e.getX() < decor.getWidth() * 0.18f
                                && e.getY() < decor.getHeight() * 0.12f) {
                            showServerSwitcher();
                        }
                    }
                });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (serverSwitchDetector != null) serverSwitchDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void showServerSwitcher() {
        boolean sandbox = isSandbox();
        new AlertDialog.Builder(this)
                .setTitle("Server")
                .setMessage("Currently on: " + (sandbox ? "Sandbox" : "Main")
                        + "\n\nPick which server the app connects to.")
                .setPositiveButton("Main", (d, w) -> switchServer("main", MAIN_URL))
                .setNegativeButton("Sandbox", (d, w) -> switchServer("sandbox", SANDBOX_URL))
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void switchServer(String env, String url) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_SERVER, env).apply();
        WebView wv = getBridge().getWebView();
        if (wv != null) wv.loadUrl(url);
        Toast.makeText(this,
                "Switched to " + ("sandbox".equals(env) ? "Sandbox" : "Main"),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        pausedAtMs = SystemClock.elapsedRealtime();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pausedAtMs > 0 && SystemClock.elapsedRealtime() - pausedAtMs > STALE_SESSION_MS) {
            // Turn-based game: don't let the player act on a turn-old page
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().reload();
            }
        }
        pausedAtMs = 0;
    }

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
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackPressMs < BACK_EXIT_WINDOW_MS) {
            super.onBackPressed();
        } else {
            lastBackPressMs = now;
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // SwipeRefreshLayout treats any downward drag as pull-to-refresh, which
    // hijacks pinch-zoom/pan on the in-game maps when the page is scrolled to
    // the top ("zooming in on a map causes the page to refresh"). A second
    // pointer always means zoom/pan, never refresh.
    private static class ZoomFriendlySwipeRefreshLayout extends SwipeRefreshLayout {
        ZoomFriendlySwipeRefreshLayout(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getPointerCount() > 1) return false;
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getPointerCount() > 1) return false;
            return super.onTouchEvent(ev);
        }
    }
}
