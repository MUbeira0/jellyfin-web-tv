package com.mubeira0.jellyfinwebtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * Shows the Jellyfin web client full screen with its TV layout, so server-side web plugins
 * (Home Screen Sections, Media Bar, ...) look the same as in a browser and work with a remote.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "settings";
    private static final String KEY_URL = "server_url";

    // Jellyfin web reads this on start; "tv" switches it to the D-pad friendly layout.
    private static final String FORCE_TV_LAYOUT =
            "(function(){try{if(localStorage.getItem('layout')!=='tv'){localStorage.setItem('layout','tv');location.reload();}}catch(e){}})();";

    private FrameLayout root;
    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        setContentView(root);

        String url = getPreferences().getString(KEY_URL, null);
        if (url == null) {
            askForServer(false);
        } else {
            openServer(url);
        }
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void askForServer(final boolean cancelable) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        String current = getPreferences().getString(KEY_URL, "https://");
        input.setText(current);
        input.setSelection(current.length());

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.server_title)
                .setMessage(R.string.server_message)
                .setView(input)
                .setCancelable(cancelable)
                .setPositiveButton(R.string.open, (dialog, which) -> {
                    String url = normalize(input.getText().toString());
                    if (url == null) {
                        askForServer(cancelable);
                        return;
                    }
                    getPreferences().edit().putString(KEY_URL, url).apply();
                    openServer(url);
                });

        if (cancelable) {
            builder.setNegativeButton(R.string.cancel, null);
        } else {
            builder.setNegativeButton(R.string.exit, (dialog, which) -> finish());
        }

        builder.show();
    }

    private static String normalize(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty() || url.equals("https://") || url.equals("http://")) {
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private void openServer(String url) {
        if (webView == null) {
            webView = createWebView();
            root.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        webView.loadUrl(url + "/web/index.html");
        webView.requestFocus();
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setBackgroundColor(0xFF101010);

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString() + " JellyfinWebTV/1.0");

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri target = request.getUrl();
                Uri current = Uri.parse(v.getUrl() == null ? "" : v.getUrl());
                if (target.getHost() != null && target.getHost().equals(current.getHost())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, target));
                } catch (Exception ignored) {
                    // No app can open it; stay on the page.
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(FORCE_TV_LAYOUT, null);
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View video, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = video;
                customViewCallback = callback;
                root.addView(video, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) {
                    return;
                }
                root.removeView(customView);
                customView = null;
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                webView.setVisibility(View.VISIBLE);
                webView.requestFocus();
            }
        });

        return view;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showMenu() {
        final CharSequence[] items = {
                getString(R.string.menu_reload),
                getString(R.string.menu_change_server),
                getString(R.string.exit)
        };
        new AlertDialog.Builder(this)
                .setItems(items, (dialog, which) -> {
                    if (which == 0 && webView != null) {
                        webView.reload();
                    } else if (which == 1) {
                        askForServer(true);
                    } else if (which == 2) {
                        finish();
                    }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            // Lets the web client leave its own fullscreen player view.
            webView.getWebChromeClient().onHideCustomView();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
