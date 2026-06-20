package com.ahousedivided.app;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Bridge;
import com.getcapacitor.WebViewListener;
import com.getcapacitor.Logger;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "AHD-MainActivity";
    private static final String APP_ORIGIN = "ahousedividedgame.com";

    private View offlineOverlay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void load() {
        super.load();
        setupWebView();
    }

    private void setupWebView() {
        Bridge bridge = this.getBridge();
        if (bridge == null) return;

        WebView webView = bridge.getWebView();
        if (webView == null) return;

        // ── Cookie persistence ──────────────────────────────────────
        // The game uses a domain-scoped HTTP-only JWT cookie on
        // .ahousedividedgame.com. Enable persistent cookie storage so
        // the 7-day session survives app restarts.
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        // Force immediate persistence of any cookies in memory
        cookieManager.flush();

        // ── WebView settings ────────────────────────────────────────
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        // Allow viewport scaling control by the page (responsive game)
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        // Allow file access for downloads if needed
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        // Media playback
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        // ── Offline detection via WebViewListener ───────────────────
        bridge.addWebViewListener(new WebViewListener() {
            @Override
            public void onPageStarted(WebView view) {
                hideOfflineOverlay();
            }

            @Override
            public void onPageLoaded(WebView view) {
                hideOfflineOverlay();
            }

            @Override
            public void onReceivedError(WebView view) {
                Logger.debug(TAG, "WebView error received — showing offline overlay");
                showOfflineOverlay();
            }

            @Override
            public void onReceivedHttpError(WebView view) {
                // Don't show offline for non-main-frame HTTP errors (e.g., 404 on sub-resource).
                // Only main-frame failures indicate server unreachable.
                Logger.debug(TAG, "WebView HTTP error received");
            }
        });

        // ── Flush cookies on pause so they persist ──────────────────
        // This is handled by the bridge lifecycle, but we reinforce it.
    }

    @Override
    public void onPause() {
        super.onPause();
        // Persist cookies when app goes to background
        CookieManager.getInstance().flush();
    }

    // ── External link interception ───────────────────────────────────
    // The Capacitor Bridge already handles external links: its
    // BridgeWebViewClient calls bridge.launchIntent(url) from
    // shouldOverrideUrlLoading, and any host outside the app origin (and
    // outside server.allowNavigation) opens in the system browser via
    // Intent.ACTION_VIEW. This is sufficient for the OAuth flow — the
    // Discord/Google auth pages are whitelisted in allowNavigation and
    // load inside the WebView so the callback cookie lands in the
    // WebView's cookie jar. No custom WebViewClient is required.

    // ── Offline overlay ───────────────────────────────────────────────

    private void showOfflineOverlay() {
        runOnUiThread(() -> {
            if (offlineOverlay != null) {
                offlineOverlay.setVisibility(View.VISIBLE);
                return;
            }

            FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
            offlineOverlay = new LinearLayout(this);
            LinearLayout layout = (LinearLayout) offlineOverlay;
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(android.view.Gravity.CENTER);
            layout.setPadding(64, 64, 64, 64);
            layout.setBackgroundColor(0xFF0F172A); // slate-900

            TextView title = new TextView(this);
            title.setText("Can't Reach the Server");
            title.setTextColor(0xFF94A3B8); // slate-400
            title.setTextSize(20);
            title.setPadding(0, 0, 0, 16);
            title.setGravity(android.view.Gravity.CENTER);

            TextView subtitle = new TextView(this);
            subtitle.setText("A House Divided requires an internet connection.\nCheck your connection and try again.");
            subtitle.setTextColor(0xFF64748B); // slate-500
            subtitle.setTextSize(14);
            subtitle.setGravity(android.view.Gravity.CENTER);
            subtitle.setPadding(0, 0, 0, 32);

            Button retry = new Button(this);
            retry.setText("Retry");
            retry.setTextColor(0xFFFFFFFF);
            retry.setBackgroundColor(0xFF2563EB); // blue-600
            retry.setPadding(48, 24, 48, 24);
            retry.setOnClickListener(v -> {
                hideOfflineOverlay();
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().reload();
                }
            });

            layout.addView(title);
            layout.addView(subtitle);
            layout.addView(retry);

            root.addView(offlineOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ));
        });
    }

    private void hideOfflineOverlay() {
        runOnUiThread(() -> {
            if (offlineOverlay != null) {
                offlineOverlay.setVisibility(View.GONE);
            }
        });
    }
}