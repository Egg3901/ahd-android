package com.ahousedivided.app;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
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
    // Reload when returning from background after this long — the game is
    // turn-based, so a stale page can show a turn-old state.
    private static final long STALE_SESSION_MS = 30 * 60 * 1000;
    private static final long BACK_EXIT_WINDOW_MS = 2000;

    private View offlineOverlay;
    private ViewGroup contentRoot;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;
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

        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefreshLayout.setColorSchemeColors(0xFF2563EB);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(0xFF0F172A);

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
                hideOfflineOverlay();
                CookieManager.getInstance().flush();
                stopRefreshIndicator();
            }

            @Override
            public void onReceivedError(WebView view) {
                Logger.debug(TAG, "WebView load error");
                stopRefreshIndicator();
                showOfflineOverlay();
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
                // Fired only when the default network drops with no replacement.
                showOfflineOverlay();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                boolean online = caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                if (online) {
                    runOnUiThread(() -> {
                        boolean wasOffline = offlineOverlay != null && offlineOverlay.getVisibility() == View.VISIBLE;
                        hideOfflineOverlay();
                        // The page under the overlay is dead/stale — refresh it
                        if (wasOffline && getBridge() != null && getBridge().getWebView() != null) {
                            getBridge().getWebView().reload();
                        }
                    });
                } else {
                    showOfflineOverlay();
                }
            }
        };

        connectivityManager.registerDefaultNetworkCallback(networkCallback);
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
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
