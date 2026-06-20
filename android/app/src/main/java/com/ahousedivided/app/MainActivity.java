package com.ahousedivided.app;

import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.Logger;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "AHD-MainActivity";

    private View offlineOverlay;
    private ViewGroup contentRoot;

    @Override
    protected void load() {
        super.load();
        setupCookies();
        setupWebViewListeners();
    }

    private void setupCookies() {
        WebView webView = getBridge().getWebView();
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        cm.flush();
        contentRoot = (ViewGroup) webView.getParent();
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
            }

            @Override
            public void onReceivedError(WebView view) {
                Logger.debug(TAG, "WebView load error");
                showOfflineOverlay();
            }
        });
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
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
