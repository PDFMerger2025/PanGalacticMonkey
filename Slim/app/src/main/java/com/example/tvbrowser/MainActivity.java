package com.example.tvbrowser;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    // ---------- GM_SHIM_JS ----------
    private static final String GM_SHIM_JS =
            "(function(){" +
            "if(window.GM_getValue)return;" +
            "var P='__gm_';" +
            "window.GM_getValue=function(k,d){try{var v=localStorage.getItem(P+k);return v===null?d:JSON.parse(v);}catch(e){return d;}};" +
            "window.GM_setValue=function(k,v){try{localStorage.setItem(P+k,JSON.stringify(v));}catch(e){}};" +
            "window.GM_deleteValue=function(k){try{localStorage.removeItem(P+k);}catch(e){}};" +
            "window.GM_listValues=function(){var o=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(k.indexOf(P)===0)o.push(k.slice(P.length));}}catch(e){}return o;};" +
            "window.GM_addStyle=function(css){var s=document.createElement('style');s.textContent=css;(document.head||document.documentElement).appendChild(s);return s;};" +
            "window.__gmXhrCallbacks={};" +
            "window.__gmXhrCallback=function(id,json){" +
            "var cb=window.__gmXhrCallbacks[id];if(!cb)return;delete window.__gmXhrCallbacks[id];" +
            "try{var r=JSON.parse(json);" +
            "if(r.ok){if(cb.onload)cb.onload({status:r.status,statusText:r.statusText||'',responseText:r.responseText,response:r.response});}" +
            "else{if(cb.onerror)cb.onerror(r.error||'GM_xmlhttpRequest failed');}" +
            "}catch(e){if(cb.onerror)cb.onerror(e);}};" +
            "window.GM_xmlhttpRequest=function(d){" +
            "try{" +
            "if(window.AndroidGMXhr&&window.AndroidGMXhr.request){" +
            "var id='gmxhr_'+Date.now()+'_'+Math.random().toString(36).slice(2);" +
            "window.__gmXhrCallbacks[id]={onload:d.onload,onerror:d.onerror};" +
            "var details={method:d.method||'GET',url:d.url,headers:d.headers||{},data:d.data||'',timeout:d.timeout||15000};" +
            "window.AndroidGMXhr.request(id,JSON.stringify(details));" +
            "}else{" +
            "var x=new XMLHttpRequest();x.open(d.method||'GET',d.url,true);" +
            "if(d.headers){for(var h in d.headers){try{x.setRequestHeader(h,d.headers[h]);}catch(e){}}}" +
            "if(d.timeout)x.timeout=d.timeout;" +
            "x.onload=function(){if(d.onload)d.onload({status:x.status,statusText:x.statusText,responseText:x.responseText,response:x.response});};" +
            "x.onerror=function(e){if(d.onerror)d.onerror(e);};" +
            "x.ontimeout=function(e){if(d.ontimeout)d.ontimeout(e);};" +
            "x.send(d.data||null);" +
            "}" +
            "}catch(e){if(d.onerror)d.onerror(e);}};" +
            "window.GM_openInTab=function(url){window.open(url,'_blank');};" +
            "window.GM_notification=function(o){try{console.log('[GM_notification]',o);}catch(e){}};" +
            "window.GM_setClipboard=function(t){try{var ta=document.createElement('textarea');ta.value=t;document.body.appendChild(ta);ta.select();document.execCommand('copy');document.body.removeChild(ta);}catch(e){}};" +
            "window.GM_info={script:{name:'userscript',version:'1.0'},version:'1.0'};" +
            "window.unsafeWindow=window;" +
            "})();";

    // ---------- TabData ----------
    private static class TabData {
        CustomWebView view;
        String title = "New Tab";
    }

    private final List<TabData> tabs = new ArrayList<>();
    private final java.util.Map<CustomWebView, java.util.List<androidx.webkit.ScriptHandler>> documentStartHandlers = new java.util.HashMap<>();
    private int activeTabIndex = -1;
    private FrameLayout tabContainer;

    private CustomWebView webView;
    private EditText urlBar;
    private CursorView cursorView;
    private EditText keyboardProxy;
    private boolean keyboardProxyActive = false;
    private boolean keyboardProxySyncing = false;
    private String keyboardProxyLastValue = "";
    private float cursorStep = DEFAULT_CURSOR_SPEED;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View topBar;
    private ProgressBar loadProgressBar;
    private Button cursorModeButton;
    private Button bookmarkStarButton;
    private Button tabsButton;
    private boolean cursorModeEnabled = true;
    private final android.os.Handler backLongPressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable backLongPressRunnable;
    private boolean backLongPressTriggered = false;
    private final android.os.Handler centerLongPressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable centerLongPressRunnable;
    private boolean centerLongPressTriggered = false;
    private static final long LONG_PRESS_DELAY_MS = 500L;
    private long lastExitPromptTime = 0L;
    private static final long EXIT_PROMPT_WINDOW_MS = 2000L;
    private static final String PREFS = "userscript_prefs";
    private static final String KEY_SCRIPTS = "scripts_json";
    private static final String KEY_BOOKMARKS = "bookmarks_json";
    private static final String KEY_HOMEPAGE = "homepage_url";
    private static final String DEFAULT_HOMEPAGE = "https://burnsymbol.com";
    private static final String KEY_CURSOR_SPEED = "cursor_speed";
    private static final String KEY_SCROLL_SPEED = "scroll_speed";
    private static final String KEY_DOWNLOADS = "downloads_json";
    private static final String KEY_HISTORY = "history_json";
    private static final int MAX_HISTORY_ITEMS = 20;
    private static final String KEY_DOWNLOAD_DIR = "download_dir_uri";
    private static final String KEY_DESKTOP_MODE = "desktop_mode_enabled";
    private static final float DEFAULT_CURSOR_SPEED = 8f;
    private static final float DEFAULT_SCROLL_SPEED = 120f;
    private float scrollStep = DEFAULT_SCROLL_SPEED;
    private boolean privateBrowsingEnabled = false;

    private static final String MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final String DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private boolean desktopModeEnabled = false;

    private String pendingJsUrl = null;
    private String pendingJsContent = null;
    private android.content.BroadcastReceiver downloadCompleteReceiver;

    // ---------- JavaScript Toggle ----------
    private static final String KEY_JS_ENABLED = "js_enabled";
    private boolean javascriptEnabled = true;

    // ---------- Smooth GPU cursor ----------
    private final Set<Integer> heldDirectionKeys = new HashSet<>();
    private long cursorMoveStartTime = 0L;
    private long lastCursorFrameTimeNanos = 0L;
    private static final float CURSOR_MAX_MULTIPLIER = 3.0f;
    private static final long CURSOR_RAMP_DURATION_MS = 600L;
    private boolean cursorFrameLoopRunning = false;

    // ---------- Cursor auto-hide ----------
    private static final String KEY_CURSOR_AUTO_HIDE = "cursor_auto_hide";
    private static final long CURSOR_HIDE_DELAY_MS = 10000; // 10 seconds
    private boolean cursorAutoHideEnabled = true;
    private final Handler cursorHideHandler = new Handler();
    private Runnable cursorHideRunnable;

    private final Choreographer.FrameCallback cursorFrameCallback =
            new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    if (heldDirectionKeys.isEmpty() || !cursorModeEnabled) {
                        cursorFrameLoopRunning = false;
                        lastCursorFrameTimeNanos = 0L;
                        return;
                    }

                    if (urlBar.hasFocus()) {
                        runOnUiThread(() -> {
                            urlBar.clearFocus();
                            hideKeyboard();
                            webView.requestFocus();
                        });
                    }

                    float deltaMultiplier = 1f;
                    if (lastCursorFrameTimeNanos != 0L) {
                        long deltaNanos = frameTimeNanos - lastCursorFrameTimeNanos;
                        float deltaMs = deltaNanos / 1_000_000f;
                        deltaMultiplier = deltaMs / 16.6667f;
                    }
                    lastCursorFrameTimeNanos = frameTimeNanos;

                    long elapsed = System.currentTimeMillis() - cursorMoveStartTime;
                    float ramp = Math.min(1f, elapsed / (float) CURSOR_RAMP_DURATION_MS);
                    float speed = cursorStep * (1f + ramp * (CURSOR_MAX_MULTIPLIER - 1f)) * deltaMultiplier;

                    float x = cursorView.getCursorX();
                    float y = cursorView.getCursorY();

                    int viewHeight = cursorView.getHeight();
                    if (viewHeight == 0) viewHeight = webView.getHeight();

                    boolean moved = false;

                    if (heldDirectionKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) {
                        if (y - speed < 0) {
                            webView.evaluateJavascript(
                                    "(function(){return window.scrollY<=0;})();",
                                    result -> {
                                        if ("true".equals(result)) {
                                            if (!urlBar.hasFocus()) {
                                                runOnUiThread(MainActivity.this::focusUrlBar);
                                            }
                                        } else {
                                            webView.evaluateJavascript(
                                                    "window.scrollBy(0,-" + scrollStep + ");",
                                                    null
                                            );
                                        }
                                    }
                            );
                        } else {
                            y -= speed;
                            moved = true;
                        }
                    }
                    if (heldDirectionKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) {
                        if (y + speed > viewHeight) {
                            webView.evaluateJavascript("window.scrollBy(0," + scrollStep + ");", null);
                        } else {
                            y += speed;
                            moved = true;
                        }
                    }
                    if (heldDirectionKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) {
                        float newX = Math.max(0, x - speed);
                        if (newX != x) moved = true;
                        x = newX;
                    }
                    if (heldDirectionKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        float newX = Math.min(cursorView.getWidth(), x + speed);
                        if (newX != x) moved = true;
                        x = newX;
                    }

                    if (moved) {
                        cursorView.setCursorPositionFast(x, y);
                        resetCursorHideTimer();
                    }

                    Choreographer.getInstance().postFrameCallback(this);
                }
            };

    private void startCursorFrameLoopIfNeeded() {
        if (!cursorFrameLoopRunning) {
            cursorFrameLoopRunning = true;
            cursorMoveStartTime = System.currentTimeMillis();
            lastCursorFrameTimeNanos = 0L;
            Choreographer.getInstance().postFrameCallback(cursorFrameCallback);
        }
    }

    private void stopCursorFrameLoop() {
        heldDirectionKeys.clear();
        cursorFrameLoopRunning = false;
        lastCursorFrameTimeNanos = 0L;
        Choreographer.getInstance().removeFrameCallback(cursorFrameCallback);
    }

    private void resetCursorHideTimer() {
        if (!cursorAutoHideEnabled || !cursorModeEnabled) return;
        cancelCursorHideTimer();
        showCursor();
        cursorHideRunnable = () -> {
            if (cursorAutoHideEnabled && cursorModeEnabled) {
                cursorView.setVisibility(View.INVISIBLE);
            }
        };
        cursorHideHandler.postDelayed(cursorHideRunnable, CURSOR_HIDE_DELAY_MS);
    }

    private void cancelCursorHideTimer() {
        if (cursorHideRunnable != null) {
            cursorHideHandler.removeCallbacks(cursorHideRunnable);
            cursorHideRunnable = null;
        }
    }

    private void showCursor() {
        if (cursorView.getVisibility() != View.VISIBLE) {
            cursorView.setVisibility(View.VISIBLE);
        }
    }

    private void toggleCursorAutoHide() {
        cursorAutoHideEnabled = !cursorAutoHideEnabled;
        prefs().edit().putBoolean(KEY_CURSOR_AUTO_HIDE, cursorAutoHideEnabled).apply();
        if (cursorAutoHideEnabled && cursorModeEnabled) {
            resetCursorHideTimer();
        } else {
            cancelCursorHideTimer();
            showCursor();
        }
        Toast.makeText(this,
                cursorAutoHideEnabled ? "Auto‑hide cursor: ON" : "Auto‑hide cursor: OFF",
                Toast.LENGTH_SHORT).show();
    }

    // ---------- JS toggle ----------
    private boolean loadJsSetting() {
        return prefs().getBoolean(KEY_JS_ENABLED, true);
    }

    private void toggleJavaScript() {
        javascriptEnabled = !javascriptEnabled;
        prefs().edit().putBoolean(KEY_JS_ENABLED, javascriptEnabled).apply();
        for (TabData tab : tabs) {
            if (tab.view != null) {
                tab.view.getSettings().setJavaScriptEnabled(javascriptEnabled);
            }
        }
        webView.reload();
        Toast.makeText(this, javascriptEnabled ? "JavaScript: ON" : "JavaScript: OFF", Toast.LENGTH_SHORT).show();
    }

    // ---------- Built‑in AdBlock Manager ----------
    private SimpleAdBlockManager adBlockManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adBlockManager = new SimpleAdBlockManager(this);
        new Thread(() -> adBlockManager.loadFilters()).start();

        tabContainer = findViewById(R.id.tabContainer);
        urlBar = findViewById(R.id.urlBar);
        cursorView = findViewById(R.id.cursorView);
        keyboardProxy = findViewById(R.id.keyboardProxy);
        loadProgressBar = findViewById(R.id.loadProgressBar);
        setupKeyboardProxy();
        Button goButton = findViewById(R.id.goButton);
        Button scriptsButton = findViewById(R.id.scriptsButton);
        Button backButton = findViewById(R.id.backButton);
        Button forwardButton = findViewById(R.id.forwardButton);
        Button menuButton = findViewById(R.id.bookmarksButton);
        Button homeButton = findViewById(R.id.homeButton);
        bookmarkStarButton = findViewById(R.id.bookmarkStarButton);
        tabsButton = findViewById(R.id.tabsButton);
        topBar = findViewById(R.id.topBar);
        cursorModeButton = findViewById(R.id.cursorModeButton);

        urlBar.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                View next = urlBar.focusSearch(View.FOCUS_RIGHT);
                if (next != null) {
                    next.requestFocus();
                    return true;
                }
            }
            return false;
        });

        goButton.setOnClickListener(v -> webView.reload());
        scriptsButton.setOnClickListener(v -> showScriptsManager());
        backButton.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        forwardButton.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        cursorModeButton.setOnClickListener(v -> toggleCursorMode());
        menuButton.setOnClickListener(v -> showBookmarksMenu());
        homeButton.setOnClickListener(v -> webView.loadUrl(getHomepage()));
        bookmarkStarButton.setOnClickListener(v -> toggleCurrentPageBookmark());
        tabsButton.setOnClickListener(v -> showTabsSwitcher());

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            loadFromUrlBar();
            return true;
        });

        loadSpeedSettings();
        desktopModeEnabled = prefs().getBoolean(KEY_DESKTOP_MODE, false);
        javascriptEnabled = loadJsSetting();
        cursorAutoHideEnabled = prefs().getBoolean(KEY_CURSOR_AUTO_HIDE, true);

        addNewTab(getHomepage());

        webView.post(() -> {
            cursorView.setCursorPosition(webView.getWidth() / 2f, webView.getHeight() / 2f);
            if (cursorAutoHideEnabled && cursorModeEnabled) {
                resetCursorHideTimer();
            }
        });
    }

    // ---------- addNewTab ----------
    private void addNewTab(String startUrl) {
        CustomWebView wv = new CustomWebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        wv.setVisibility(View.GONE);
        wv.setFocusable(true);
        wv.setFocusableInTouchMode(true);

        wv.getSettings().setJavaScriptEnabled(javascriptEnabled);
        wv.getSettings().setDomStorageEnabled(true);
        wv.getSettings().setUseWideViewPort(true);
        wv.getSettings().setLoadWithOverviewMode(true);
        wv.getSettings().setMediaPlaybackRequiresUserGesture(false);
        wv.getSettings().setDatabaseEnabled(true);
        wv.getSettings().setAllowFileAccess(true);
        wv.getSettings().setAllowContentAccess(true);
        wv.getSettings().setMixedContentMode(
            android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        wv.getSettings().setUserAgentString(desktopModeEnabled ? DESKTOP_USER_AGENT : MOBILE_USER_AGENT);
        wv.setBackgroundColor(Color.BLACK);
        wv.getSettings().setSupportMultipleWindows(true);
        wv.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        wv.getSettings().setBuiltInZoomControls(true);
        wv.getSettings().setDisplayZoomControls(false);
        wv.getSettings().setSupportZoom(true);

        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        TabData tab = new TabData();
        tab.view = wv;

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (wv != webView) return;
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view.evaluateJavascript(GM_SHIM_JS, null);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (wv != webView) return;
                urlBar.setText(url);
                webView.evaluateJavascript("window.__tvKeyboardTarget = null;", null);
                updateBookmarkStarIcon();
                updateProgressBar(100);
                if (!privateBrowsingEnabled) {
                    recordHistory(view.getTitle(), url);
                }

                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    injectAllUserscripts();
                }
                injectTabNavigationScript();
                reInjectTabNav();
                injectScrollWatcher();
                stripTargetBlankLinks();
                if (!cursorModeEnabled) {
                    webView.evaluateJavascript("window.__tvTabNav && window.__tvTabNav.enable();", null);
                }

                if (url != null && url.toLowerCase().endsWith(".txt")) {
                    showTextUrlPopup(url);
                }

                if (url != null && url.toLowerCase().endsWith(".js")) {
                    pendingJsUrl = url;
                    view.evaluateJavascript(
                            "document.body ? document.body.innerText : ''",
                            value -> {
                                pendingJsContent = unescapeJson(value);
                                if (pendingJsContent != null && !pendingJsContent.isEmpty()) {
                                    promptInstallScriptWithContent(url);
                                }
                            });
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (adBlockManager != null && adBlockManager.isEnabled()) {
                    String url = request.getUrl().toString();
                    if (adBlockManager.shouldBlock(url)) {
                        Log.d("AdBlock", "Blocked: " + url);
                        return null;
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        wv.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                    boolean isUserGesture, android.os.Message resultMsg) {
                WebView.HitTestResult result = view.getHitTestResult();
                String url = result != null ? result.getExtra() : null;
                if (url != null) {
                    wv.loadUrl(url);
                }
                return false;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (wv == webView) {
                    updateProgressBar(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title != null && !title.isEmpty()) {
                    tab.title = title;
                }
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                if (customView != null) {
                    onHideCustomView();
                }
                customView = view;
                customViewCallback = callback;
                FrameLayout root = findViewById(android.R.id.content);
                root.addView(customView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                if (customView != null) {
                    FrameLayout root = findViewById(android.R.id.content);
                    root.removeView(customView);
                    customView = null;
                }
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
            }
        });

        registerDocumentStartScripts(wv);

        wv.addJavascriptInterface(new KeyboardBridge(), "AndroidKeyboard");
        wv.addJavascriptInterface(new ChromeBridge(), "AndroidChrome");
        wv.addJavascriptInterface(new GMXhrBridge(), "AndroidGMXhr");
        wv.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            promptDownloadConfirmation(url, userAgent, contentDisposition, mimetype);
        });

        tabContainer.addView(wv);
        tabs.add(tab);
        switchToTab(tabs.size() - 1);
        wv.loadUrl(startUrl);
    }

    // ---------- switchToTab ----------
    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).view.setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        activeTabIndex = index;
        webView = tabs.get(index).view;
        urlBar.setText(webView.getUrl());
        updateBookmarkStarIcon();
        updateTabsButtonLabel();
        webView.requestFocus();
        if (cursorAutoHideEnabled && cursorModeEnabled) {
            resetCursorHideTimer();
        }
    }

    // ---------- closeTab ----------
    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        TabData closed = tabs.remove(index);
        tabContainer.removeView(closed.view);
        documentStartHandlers.remove(closed.view);
        closed.view.destroy();

        if (tabs.isEmpty()) {
            addNewTab(getHomepage());
            return;
        }

        int newIndex = Math.min(index, tabs.size() - 1);
        if (index == activeTabIndex || activeTabIndex >= tabs.size()) {
            switchToTab(newIndex);
        } else if (index < activeTabIndex) {
            activeTabIndex--;
        }
        updateTabsButtonLabel();
    }

    private void updateTabsButtonLabel() {
        if (tabsButton != null) {
            tabsButton.setText("Tabs (" + tabs.size() + ")");
        }
    }

    // ---------- showTabsSwitcher ----------
    private void showTabsSwitcher() {
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(16, 16, 16, 16);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        View[] firstRowHolder = new View[1];

        android.widget.LinearLayout listHolder = new android.widget.LinearLayout(this);
        listHolder.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.addView(listHolder);

        Runnable[] rebuildHolder = new Runnable[1];
        Runnable rebuild = () -> {
            listHolder.removeAllViews();
            firstRowHolder[0] = null;
            for (int i = 0; i < tabs.size(); i++) {
                final int idx = i;
                TabData tab = tabs.get(i);

                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setPadding(12, 12, 12, 12);
                final int normalBg = i == activeTabIndex ? Color.parseColor("#333333") : Color.TRANSPARENT;
                row.setBackgroundColor(normalBg);

                TextView label = new TextView(this);
                String title = tab.title != null && !tab.title.isEmpty() ? tab.title : "New Tab";
                label.setText(title);
                label.setTextColor(Color.WHITE);
                label.setTextSize(16f);
                label.setPadding(16, 12, 16, 12);
                label.setFocusable(true);
                label.setFocusableInTouchMode(true);
                label.setOnFocusChangeListener((v, hasFocus) -> {
                    row.setBackgroundColor(hasFocus ? Color.parseColor("#3DFF71") : normalBg);
                    label.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
                });
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                row.addView(label, lp);
                label.setOnClickListener(v -> {
                    switchToTab(idx);
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                });

                Button closeBtn = new Button(this);
                closeBtn.setText("\u2715");
                closeBtn.setTextColor(Color.WHITE);
                closeBtn.setBackgroundColor(Color.parseColor("#555555"));
                closeBtn.setMinWidth(0);
                closeBtn.setMinHeight(0);
                closeBtn.setPadding(24, 8, 24, 8);
                closeBtn.setFocusable(true);
                closeBtn.setOnFocusChangeListener((v, hasFocus) -> {
                    closeBtn.setBackgroundColor(Color.parseColor(hasFocus ? "#3DFF71" : "#555555"));
                    closeBtn.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
                });
                closeBtn.setOnClickListener(v -> {
                    closeTab(idx);
                    if (rebuildHolder[0] != null) rebuildHolder[0].run();
                });
                row.addView(closeBtn);

                listHolder.addView(row);
                if (i == activeTabIndex || firstRowHolder[0] == null) {
                    firstRowHolder[0] = label;
                }
            }
        };
        rebuildHolder[0] = rebuild;
        rebuild.run();

        Button newTabBtn = new Button(this);
        newTabBtn.setText("+ New Tab");
        newTabBtn.setTextColor(Color.WHITE);
        newTabBtn.setBackgroundColor(Color.parseColor("#555555"));
        newTabBtn.setFocusable(true);
        newTabBtn.setOnFocusChangeListener((v, hasFocus) -> {
            newTabBtn.setBackgroundColor(Color.parseColor(hasFocus ? "#3DFF71" : "#555555"));
            newTabBtn.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
        });
        newTabBtn.setOnClickListener(v -> {
            addNewTab(getHomepage());
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
        });
        container.addView(newTabBtn, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        dialogHolder[0] = new AlertDialog.Builder(this)
                .setTitle("Tabs")
                .setView(container)
                .setNegativeButton("Close", null)
                .create();
        dialogHolder[0].show();

        if (firstRowHolder[0] != null) {
            firstRowHolder[0].requestFocus();
        }
    }

    private void updateProgressBar(int progress) {
        if (loadProgressBar == null) return;
        if (progress >= 100) {
            loadProgressBar.setProgress(100);
            loadProgressBar.postDelayed(() -> loadProgressBar.setVisibility(View.GONE), 200);
        } else {
            loadProgressBar.setVisibility(View.VISIBLE);
            loadProgressBar.setProgress(progress);
        }
    }

    private void loadSpeedSettings() {
        cursorStep = prefs().getFloat(KEY_CURSOR_SPEED, DEFAULT_CURSOR_SPEED);
        scrollStep = prefs().getFloat(KEY_SCROLL_SPEED, DEFAULT_SCROLL_SPEED);
    }

    private void loadFromUrlBar() {
        String input = urlBar.getText().toString().trim();
        if (input.isEmpty()) return;
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            if (input.contains(".") && !input.contains(" ")) {
                input = "https://" + input;
            } else {
                input = "https://www.google.com/search?q=" + input;
            }
        }
        webView.loadUrl(input);
        hideKeyboard();
    }

    // ---------- Bridges ----------
    private class KeyboardBridge {
        @android.webkit.JavascriptInterface
        public void showKeyboard() {
            runOnUiThread(MainActivity.this::showSystemKeyboard);
        }
    }

    private class ChromeBridge {
        @android.webkit.JavascriptInterface
        public void onScrollDown() {
            runOnUiThread(MainActivity.this::hideTopBar);
        }

        @android.webkit.JavascriptInterface
        public void onScrollUp() {
            runOnUiThread(MainActivity.this::showTopBar);
        }
    }

    private class GMXhrBridge {
        @android.webkit.JavascriptInterface
        public void request(final String requestId, final String detailsJson) {
            new Thread(() -> {
                String responseJson;
                try {
                    JSONObject details = new JSONObject(detailsJson);
                    String method = details.optString("method", "GET");
                    String urlStr = details.getString("url");
                    int timeout = details.optInt("timeout", 15000);

                    java.net.URL url = new java.net.URL(urlStr);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod(method);
                    conn.setConnectTimeout(timeout > 0 ? timeout : 15000);
                    conn.setReadTimeout(timeout > 0 ? timeout : 15000);
                    conn.setInstanceFollowRedirects(true);

                    if (details.has("headers")) {
                        JSONObject headers = details.getJSONObject("headers");
                        java.util.Iterator<String> keys = headers.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            try { conn.setRequestProperty(key, headers.getString(key)); } catch (Exception ignore) {}
                        }
                    }

                    String data = details.optString("data", null);
                    if (data != null && !data.isEmpty() && !"GET".equalsIgnoreCase(method)) {
                        conn.setDoOutput(true);
                        try (java.io.OutputStream os = conn.getOutputStream()) {
                            os.write(data.getBytes("UTF-8"));
                        }
                    }

                    int status = conn.getResponseCode();
                    java.io.InputStream is;
                    try {
                        is = conn.getInputStream();
                    } catch (Exception e) {
                        is = conn.getErrorStream();
                    }
                    String responseText = "";
                    if (is != null) {
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = is.read(buf)) != -1) buffer.write(buf, 0, n);
                        is.close();
                        responseText = buffer.toString("UTF-8");
                    }

                    JSONObject result = new JSONObject();
                    result.put("status", status);
                    result.put("statusText", "");
                    result.put("responseText", responseText);
                    result.put("response", responseText);
                    result.put("ok", true);
                    responseJson = result.toString();
                } catch (Exception e) {
                    JSONObject err = new JSONObject();
                    try {
                        err.put("ok", false);
                        err.put("error", e.getMessage() != null ? e.getMessage() : e.toString());
                    } catch (JSONException ignore) {}
                    responseJson = err.toString();
                }

                final String finalResponseJson = responseJson;
                runOnUiThread(() -> {
                    if (webView == null) return;
                    String escaped = finalResponseJson
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r");
                    webView.evaluateJavascript(
                            "window.__gmXhrCallback && window.__gmXhrCallback('" + requestId + "', '" + escaped + "');",
                            null);
                });
            }).start();
        }
    }

    private void setupKeyboardProxy() {
        keyboardProxy.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                if (keyboardProxySyncing || !keyboardProxyActive) return;
                String newValue = editable.toString();
                String lastValue = keyboardProxyLastValue;
                if (newValue.equals(lastValue)) return;

                if (newValue.length() > lastValue.length() && newValue.startsWith(lastValue)) {
                    String added = newValue.substring(lastValue.length());
                    typeIntoPage(added);
                } else if (newValue.length() < lastValue.length() && lastValue.startsWith(newValue)) {
                    int deleteCount = lastValue.length() - newValue.length();
                    for (int i = 0; i < deleteCount; i++) {
                        deleteFromPage();
                    }
                } else {
                    for (int i = 0; i < lastValue.length(); i++) {
                        deleteFromPage();
                    }
                    if (!newValue.isEmpty()) {
                        typeIntoPage(newValue);
                    }
                }
                keyboardProxyLastValue = newValue;
            }
        });

        keyboardProxy.setOnEditorActionListener((v, actionId, event) -> {
            submitProxyInputAndCloseKeyboard();
            return true;
        });

        keyboardProxy.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                submitProxyInputAndCloseKeyboard();
                return true;
            }
            return false;
        });
    }

    private void submitProxyInputAndCloseKeyboard() {
        String finalText = keyboardProxy.getText() != null ? keyboardProxy.getText().toString() : "";
        String escaped = finalText.replace("\\\\", "\\\\\\\\").replace("'", "\\\\'");
        String js =
            "(function(){" +
            "  " + RESOLVE_TARGET_JS +
            "  if (!el) return 'noel';" +
            "  el.focus({preventScroll:true});" +
            "  var s = '" + escaped + "';" +
            "  if (el.isContentEditable) {" +
            "    if (el.innerText !== s) {" +
            "      el.innerText = s;" +
            "      var r = document.createRange(); r.selectNodeContents(el); r.collapse(false);" +
            "      var sel = window.getSelection(); sel.removeAllRanges(); sel.addRange(r);" +
            "    }" +
            "  } else if ('value' in el) {" +
            "    if (el.value !== s) {" +
            "      el.value = s;" +
            "    }" +
            "    el.selectionStart = el.selectionEnd = el.value.length;" +
            "    el.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  }" +
            "  var opts = {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true};" +
            "  el.dispatchEvent(new KeyboardEvent('keydown', opts));" +
            "  el.dispatchEvent(new KeyboardEvent('keypress', opts));" +
            "  el.dispatchEvent(new KeyboardEvent('keyup', opts));" +
            "  if (el.form) {" +
            "    var btn = el.form.querySelector('button[type=submit], input[type=submit]');" +
            "    if (btn) { btn.click(); } else { el.form.submit(); }" +
            "  }" +
            "  window.__tvKeyboardTarget = null;" +
            "  return 'done';" +
            "})()";

        webView.evaluateJavascript(js, result -> hideSystemKeyboard());
    }

    private void resetKeyboardProxyText() {
        keyboardProxySyncing = true;
        keyboardProxy.setText("");
        keyboardProxySyncing = false;
        keyboardProxyLastValue = "";
    }

    private void showSystemKeyboard() {
        keyboardProxyActive = true;
        keyboardProxy.setVisibility(View.VISIBLE);
        resetKeyboardProxyText();
        keyboardProxy.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(keyboardProxy, InputMethodManager.SHOW_FORCED);
        }
    }

    private void hideSystemKeyboard() {
        keyboardProxyActive = false;
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(keyboardProxy.getWindowToken(), 0);
        }
        keyboardProxy.setVisibility(View.GONE);
        resetKeyboardProxyText();
        webView.requestFocus();
    }

    private static final String RESOLVE_TARGET_JS =
        "var el = window.__tvKeyboardTarget || document.activeElement;" +
        "if (el === document.body || el === document.documentElement) { el = null; }";

    private void typeIntoPage(String chars) {
        String escaped = chars.replace("\\\\", "\\\\\\\\").replace("'", "\\\\'");
        webView.evaluateJavascript(
            "(function(){" +
            "  " + RESOLVE_TARGET_JS +
            "  if (!el) return;" +
            "  var s = '" + escaped + "';" +
            "  if (el.isContentEditable) {" +
            "    el.focus({preventScroll:true});" +
            "    document.execCommand('insertText', false, s);" +
            "  } else if ('value' in el) {" +
            "    var start = el.selectionStart != null ? el.selectionStart : el.value.length;" +
            "    var end = el.selectionEnd != null ? el.selectionEnd : el.value.length;" +
            "    el.value = el.value.slice(0, start) + s + el.value.slice(end);" +
            "    el.selectionStart = el.selectionEnd = start + s.length;" +
            "    el.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  }" +
            "})()", null);
    }

    private void deleteFromPage() {
        webView.evaluateJavascript(
            "(function(){" +
            "  " + RESOLVE_TARGET_JS +
            "  if (!el) return;" +
            "  if (el.isContentEditable) {" +
            "    el.focus({preventScroll:true});" +
            "    document.execCommand('delete', false, null);" +
            "  } else if ('value' in el) {" +
            "    var start = el.selectionStart != null ? el.selectionStart : el.value.length;" +
            "    var end = el.selectionEnd != null ? el.selectionEnd : el.value.length;" +
            "    if (start === end && start > 0) { start = start - 1; }" +
            "    el.value = el.value.slice(0, start) + el.value.slice(end);" +
            "    el.selectionStart = el.selectionEnd = start;" +
            "    el.dispatchEvent(new Event('input', {bubbles:true}));" +
            "  }" +
            "})()", null);
    }

    private void pressEnterOnPage() {
        webView.evaluateJavascript(
            "(function(){" +
            "  " + RESOLVE_TARGET_JS +
            "  if (!el) return;" +
            "  var opts = {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true};" +
            "  el.dispatchEvent(new KeyboardEvent('keydown', opts));" +
            "  el.dispatchEvent(new KeyboardEvent('keyup', opts));" +
            "  if (el.form) { " +
            "    var btn = el.form.querySelector('button[type=submit], input[type=submit]');" +
            "    if (btn) { btn.click(); } else { el.form.submit(); }" +
            "  }" +
            "})()", null);
    }

    private void zoomIn() {
        boolean zoomed = webView.zoomIn();
        if (!zoomed) {
            Toast.makeText(this, "Maximum zoom reached", Toast.LENGTH_SHORT).show();
        }
    }

    private void zoomOut() {
        boolean zoomed = webView.zoomOut();
        if (!zoomed) {
            Toast.makeText(this, "Minimum zoom reached", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleCursorMode() {
        cursorModeEnabled = !cursorModeEnabled;
        if (!cursorModeEnabled) {
            stopCursorFrameLoop();
            cancelCursorHideTimer();
        } else {
            if (cursorAutoHideEnabled) {
                resetCursorHideTimer();
            } else {
                showCursor();
            }
        }
        cursorModeButton.setText(cursorModeEnabled ? "Cursor: ON" : "Cursor: OFF");
        cursorView.setVisibility(cursorModeEnabled ? View.VISIBLE : View.GONE);
        if (!cursorModeEnabled) {
            showTopBar();
            webView.evaluateJavascript("window.__tvTabNav && window.__tvTabNav.enable();", null);
            webView.requestFocus();
        } else {
            webView.evaluateJavascript("window.__tvTabNav && window.__tvTabNav.disable();", null);
        }
    }

    // ---------- Tab navigation script ----------
    private void injectTabNavigationScript() {
        String js = "(function(){" +
            "  if (window.__tvTabNav) { return; }" +
            "  var items = [];" +
            "  var idx = -1;" +
            "  var active = false;" +
            "  var HIGHLIGHT = '3px solid #3DFF71';" +
            "  function collect(){" +
            "    items = Array.prototype.slice.call(document.querySelectorAll(" +
            "      'a, button, input, textarea, select, [role=button], [onclick], [tabindex]'" +
            "    )).filter(function(el){" +
            "      var r = el.getBoundingClientRect();" +
            "      return r.width > 0 && r.height > 0;" +
            "    });" +
            "  }" +
            "  function clearHighlight(){" +
            "    if (idx >= 0 && items[idx]) { items[idx].style.outline = ''; }" +
            "  }" +
            "  function highlight(i){" +
            "    clearHighlight();" +
            "    idx = i;" +
            "    if (items[idx]) {" +
            "      items[idx].style.outline = HIGHLIGHT;" +
            "      items[idx].scrollIntoView({block:'center', behavior:'smooth'});" +
            "      try { items[idx].focus({preventScroll:true}); } catch(e){}" +
            "    }" +
            "  }" +
            "  window.__tvTabNav = {" +
            "    enable: function(){ active = true; collect(); if (idx < 0 && items.length) highlight(0); }," +
            "    disable: function(){ active = false; clearHighlight(); }," +
            "    next: function(){" +
            "      if (!active) return 'inactive';" +
            "      collect();" +
            "      if (!items.length) return 'empty';" +
            "      if (idx >= items.length - 1) { return 'bottom'; }" +
            "      highlight(idx+1);" +
            "      return 'ok';" +
            "    }," +
            "    prev: function(){" +
            "      if (!active) return 'inactive';" +
            "      collect();" +
            "      if (!items.length) return 'empty';" +
            "      if (idx <= 0) { return 'top'; }" +
            "      highlight(idx-1);" +
            "      return 'ok';" +
            "    }," +
            "    activate: function(){ if (!active || idx < 0 || !items[idx]) return; " +
            "      var el = items[idx]; " +
            "      if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable) {" +
            "        window.__tvKeyboardTarget = el;" +
            "        el.focus({preventScroll:true});" +
            "        AndroidKeyboard.showKeyboard();" +
            "      } else {" +
            "        el.click();" +
            "      }" +
            "    }" +
            "  };" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void reInjectTabNav() {
        injectTabNavigationScript();
    }

    private void tabNavNext() {
        if (webView == null) return;
        webView.evaluateJavascript(
            "window.__tvTabNav ? window.__tvTabNav.next() : 'inactive';",
            result -> {
                if (result == null) return;
                if (result.contains("bottom") || result.contains("empty")) {
                    webView.evaluateJavascript("window.scrollBy(0," + scrollStep + ");", null);
                }
            });
    }

    private void tabNavPrev() {
        if (webView == null) return;
        webView.evaluateJavascript(
            "window.__tvTabNav ? window.__tvTabNav.prev() : 'inactive';",
            result -> {
                if (result == null) return;
                if (result.contains("top") || result.contains("empty")) {
                    webView.evaluateJavascript("window.scrollBy(0,-" + scrollStep + ");", null);
                    runOnUiThread(this::focusUrlBar);
                }
            });
    }

    private void tabNavActivate() {
        String js = "(function(){" +
            "  var el = document.activeElement;" +
            "  if (el && el !== document.body && el !== document.documentElement) {" +
            "    if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable) {" +
            "      window.__tvKeyboardTarget = el;" +
            "      el.focus({preventScroll:true});" +
            "      AndroidKeyboard.showKeyboard();" +
            "    } else {" +
            "      el.click();" +
            "    }" +
            "    return;" +
            "  }" +
            "  window.__tvTabNav && window.__tvTabNav.activate();" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void injectScrollWatcher() {
        String js = "(function(){" +
            "  if (window.__tvScrollWatcher) { return; }" +
            "  window.__tvScrollWatcher = true;" +
            "  var lastY = window.scrollY;" +
            "  window.addEventListener('scroll', function(){" +
            "    var y = window.scrollY;" +
            "    if (y > lastY && y > 40) {" +
            "      AndroidChrome.onScrollDown();" +
            "    } else if (y < lastY) {" +
            "      AndroidChrome.onScrollUp();" +
            "    }" +
            "    lastY = y;" +
            "  }, {passive:true});" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void hideTopBar() {
        if (topBar != null && topBar.getVisibility() == View.VISIBLE) {
            topBar.setVisibility(View.GONE);
        }
    }

    private void showTopBar() {
        if (topBar != null && topBar.getVisibility() != View.VISIBLE) {
            topBar.setVisibility(View.VISIBLE);
        }
    }

    private void stripTargetBlankLinks() {
        String js = "(function(){" +
            "  function strip(){" +
            "    var links = document.querySelectorAll('a[target=\"_blank\"]');" +
            "    for (var i = 0; i < links.length; i++) {" +
            "      links[i].removeAttribute('target');" +
            "    }" +
            "  }" +
            "  strip();" +
            "  new MutationObserver(strip).observe(document.documentElement, " +
            "    {childList:true, subtree:true});" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
        }
        if (keyboardProxyActive) {
            hideSystemKeyboard();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    // ---------- Scripts management ----------
    private JSONArray loadScripts() {
        String raw = prefs().getString(KEY_SCRIPTS, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveScripts(JSONArray arr) {
        prefs().edit().putString(KEY_SCRIPTS, arr.toString()).apply();
    }

    private void addScript(String name, String url, String code) {
        JSONArray arr = loadScripts();
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("url", url);
            obj.put("code", code);
            obj.put("enabled", true);
            arr.put(obj);
            saveScripts(arr);
            Toast.makeText(this, "Installed: " + name, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Log.e("TVBrowser", "addScript failed", e);
        }
    }

    private void removeScript(int index) {
        JSONArray arr = loadScripts();
        JSONArray newArr = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i != index) {
                newArr.put(arr.opt(i));
            }
        }
        saveScripts(newArr);
    }

    private void showScriptsManager() {
        JSONArray arr = loadScripts();
        int count = arr.length();
        if (count == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("No userscripts installed");
            builder.setMessage("You can add a userscript by URL or from a local .js file.");
            builder.setPositiveButton("Install from URL", (d, w) -> promptInstallScriptFromUrl(null));
            builder.setNeutralButton("Install from Device", (d, w) -> pickLocalScriptFile());
            builder.setNegativeButton("Close", null);
            builder.show();
            return;
        }
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            try {
                names[i] = arr.getJSONObject(i).optString("name", "script " + i);
            } catch (JSONException e) {
                names[i] = "script " + i;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Installed userscripts (select to remove)");
        builder.setItems(names, (dialog, which) -> confirmRemove(which, names[which]));
        builder.setNeutralButton("Install from URL", (d, w) -> promptInstallScriptFromUrl(null));
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void promptInstallScriptFromUrl(String url) {
        if (url != null && !url.isEmpty()) {
            if (pendingJsContent != null && !pendingJsContent.isEmpty()) {
                promptInstallScriptWithContent(url);
                return;
            }
        }
        final EditText input = new EditText(this);
        input.setHint("https://example.com/script.js");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Install userscript from URL")
                .setView(input)
                .setPositiveButton("Install", (d, w) -> {
                    String urlStr = input.getText().toString().trim();
                    if (!urlStr.isEmpty()) {
                        webView.loadUrl(urlStr);
                    } else {
                        Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptInstallScriptWithContent(String url) {
        if (pendingJsContent == null || pendingJsContent.isEmpty()) return;
        String defaultName = url.substring(url.lastIndexOf('/') + 1);

        new AlertDialog.Builder(this)
                .setTitle("Install userscript?")
                .setMessage(url)
                .setPositiveButton("Install", (dialog, which) -> {
                    addScript(defaultName, url, pendingJsContent);
                    refreshDocumentStartScriptsOnAllTabs();
                    pendingJsUrl = null;
                    pendingJsContent = null;
                    showTopBar();
                    if (webView.canGoBack()) {
                        webView.goBack();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    pendingJsUrl = null;
                    pendingJsContent = null;
                    showTopBar();
                    if (webView.canGoBack()) {
                        webView.goBack();
                    }
                })
                .show();
    }

    // ---------- Local file picker ----------
    private static final int REQUEST_CODE_PICK_JS_FILE = 1001;

    private void pickLocalScriptFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/javascript");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/javascript", "text/plain"});
        startActivityForResult(intent, REQUEST_CODE_PICK_JS_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_JS_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();
                    String code = sb.toString();

                    String fileName = getFileName(uri);
                    if (fileName == null || fileName.isEmpty()) {
                        fileName = "userscript.js";
                    }

                    addScript(fileName, uri.toString(), code);
                    refreshDocumentStartScriptsOnAllTabs();
                    Toast.makeText(this, "Installed: " + fileName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("TVBrowser", "Failed to read script file", e);
                    Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    // ---------- registerDocumentStartScripts ----------
    private void registerDocumentStartScripts(CustomWebView wv) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.e("TVBrowser", "DOCUMENT_START_SCRIPT UNSUPPORTED on this WebView");
            return;
        }
        Log.d("TVBrowser", "Registering GM_ shim + userscripts via WebViewCompat");
        java.util.List<androidx.webkit.ScriptHandler> existing = documentStartHandlers.get(wv);
        if (existing != null) {
            for (androidx.webkit.ScriptHandler handler : existing) {
                try {
                    handler.remove();
                } catch (Exception e) {
                    Log.e("TVBrowser", "ScriptHandler remove failed", e);
                }
            }
        }
        java.util.List<androidx.webkit.ScriptHandler> handlers = new ArrayList<>();
        handlers.add(WebViewCompat.addDocumentStartJavaScript(wv, GM_SHIM_JS, java.util.Collections.singleton("*")));

        JSONArray startupScripts = loadScripts();
        for (int i = 0; i < startupScripts.length(); i++) {
            try {
                JSONObject obj = startupScripts.getJSONObject(i);
                if (obj.optBoolean("enabled", true)) {
                    String code = obj.getString("code");
                    java.util.Set<String> originRules = parseMatchOriginRules(code);
                    String scriptName = obj.optString("name", "unnamed");
                    Log.d("TVBrowser", "Registering userscript '" + scriptName + "' with origin rules: " + originRules);
                    String wrapped = "try{\n" + code + "\n}catch(e){console.error('Userscript error:',e && e.message ? e.message : e);}";
                    handlers.add(WebViewCompat.addDocumentStartJavaScript(wv, wrapped, originRules));
                }
            } catch (JSONException e) {
                Log.e("TVBrowser", "document-start inject failed", e);
            }
        }
        documentStartHandlers.put(wv, handlers);
    }

    private java.util.Set<String> parseMatchOriginRules(String scriptCode) {
        java.util.Set<String> origins = new java.util.HashSet<>();
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("//\\s*@match\\s+(\\S+)")
                    .matcher(scriptCode);
            while (m.find()) {
                String pattern = m.group(1);
                String origin = matchPatternToOrigin(pattern);
                if (origin != null) origins.add(origin);
            }
        } catch (Exception e) {
            Log.e("TVBrowser", "parseMatchOriginRules failed", e);
        }
        if (origins.isEmpty()) {
            origins.add("*");
        }
        return origins;
    }

    private String matchPatternToOrigin(String pattern) {
        try {
            int schemeEnd = pattern.indexOf("://");
            if (schemeEnd < 0) return null;
            String scheme = pattern.substring(0, schemeEnd);
            String rest = pattern.substring(schemeEnd + 3);
            int pathStart = rest.indexOf('/');
            String hostPart = pathStart >= 0 ? rest.substring(0, pathStart) : rest;
            if (hostPart.isEmpty() || hostPart.equals("*")) return "*";
            return scheme + "://" + hostPart;
        } catch (Exception e) {
            return null;
        }
    }

    private void refreshDocumentStartScriptsOnAllTabs() {
        for (TabData tab : tabs) {
            if (tab.view != null) {
                registerDocumentStartScripts(tab.view);
            }
        }
    }

    private void injectAllUserscripts() {
        webView.evaluateJavascript(GM_SHIM_JS, null);

        JSONArray arr = loadScripts();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.optBoolean("enabled", true)) {
                    String code = obj.getString("code");
                    String wrapped = "try{\n" + code + "\n}catch(e){console.error('Userscript error:',e && e.message ? e.message : e);}";
                    webView.evaluateJavascript(wrapped, null);
                }
            } catch (JSONException e) {
                Log.e("TVBrowser", "inject failed", e);
            }
        }
    }

    // ---------- Homepage ----------
    private String getHomepage() {
        return prefs().getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE);
    }

    private void setHomepage(String url) {
        prefs().edit().putString(KEY_HOMEPAGE, url).apply();
    }

    // ---------- Bookmarks ----------
    private JSONArray loadBookmarks() {
        String raw = prefs().getString(KEY_BOOKMARKS, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveBookmarks(JSONArray arr) {
        prefs().edit().putString(KEY_BOOKMARKS, arr.toString()).apply();
    }

    private void addBookmark(String name, String url) {
        JSONArray arr = loadBookmarks();
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("url", url);
            arr.put(obj);
            saveBookmarks(arr);
            Toast.makeText(this, "Bookmarked: " + name, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Log.e("TVBrowser", "addBookmark failed", e);
        }
        updateBookmarkStarIcon();
    }

    private void removeBookmark(int index) {
        JSONArray arr = loadBookmarks();
        JSONArray newArr = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i != index) {
                newArr.put(arr.opt(i));
            }
        }
        saveBookmarks(newArr);
        updateBookmarkStarIcon();
    }

    private int findBookmarkIndexForUrl(String url) {
        if (url == null) return -1;
        JSONArray arr = loadBookmarks();
        for (int i = 0; i < arr.length(); i++) {
            try {
                if (url.equals(arr.getJSONObject(i).optString("url"))) {
                    return i;
                }
            } catch (JSONException e) {
                Log.e("TVBrowser", "findBookmarkIndexForUrl failed", e);
            }
        }
        return -1;
    }

    private void toggleCurrentPageBookmark() {
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "No page loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        int existingIndex = findBookmarkIndexForUrl(currentUrl);
        if (existingIndex >= 0) {
            removeBookmark(existingIndex);
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
        } else {
            String name = webView.getTitle() != null ? webView.getTitle() : currentUrl;
            addBookmark(name, currentUrl);
        }
    }

    private void updateBookmarkStarIcon() {
        if (bookmarkStarButton == null) return;
        String currentUrl = webView.getUrl();
        boolean isBookmarked = findBookmarkIndexForUrl(currentUrl) >= 0;
        bookmarkStarButton.setText(isBookmarked ? "\u2605" : "\u2606");
    }

    // ---------- Downloads ----------
    private void promptDownloadConfirmation(String url, String userAgent, String contentDisposition, String mimetype) {
        final String finalFileName = extractFileName(url, contentDisposition, mimetype);

        new AlertDialog.Builder(this)
                .setTitle("Download this file?")
                .setMessage(finalFileName + "\n\n" + url)
                .setPositiveButton("Download", (d, w) ->
                        startFileDownload(url, userAgent, contentDisposition, mimetype, finalFileName))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Improved file name extraction that handles Google Images, favicon services, etc.
     */
    private String extractFileName(String url, String contentDisposition, String mimetype) {
        // 1. Try content-disposition
        if (contentDisposition != null) {
            try {
                String cd = contentDisposition;
                int fnIdx = cd.toLowerCase().indexOf("filename=");
                if (fnIdx >= 0) {
                    String name = cd.substring(fnIdx + 9).replace("\"", "").trim();
                    int semi = name.indexOf(';');
                    if (semi >= 0) name = name.substring(0, semi).trim();
                    if (!name.isEmpty()) return name;
                }
            } catch (Exception e) { /* ignore */ }
        }

        // 2. Handle Google favicon URLs (gstatic.com/faviconV2) – but now we rarely get them
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null && host.contains("gstatic.com") && uri.getPath() != null
                    && uri.getPath().contains("faviconV2")) {
                String siteUrl = uri.getQueryParameter("url");
                if (siteUrl != null && !siteUrl.isEmpty()) {
                    String domain = extractDomainFromUrl(siteUrl);
                    if (domain != null && !domain.isEmpty()) {
                        return "favicon_" + domain + ".png";
                    }
                }
                return "favicon.png";
            }
        } catch (Exception e) { /* ignore */ }

        // 3. If it's a Google Images result URL, extract the imgurl parameter
        String realUrl = url;
        try {
            Uri uri = Uri.parse(url);
            if (uri.getHost() != null && (uri.getHost().contains("google.com") || uri.getHost().contains("google."))) {
                String imgurl = uri.getQueryParameter("imgurl");
                if (imgurl != null && !imgurl.isEmpty()) {
                    realUrl = imgurl;
                }
            }
        } catch (Exception e) { /* ignore */ }

        // 4. Try to get a file name from the real URL's path
        try {
            Uri realUri = Uri.parse(realUrl);
            String lastSegment = realUri.getLastPathSegment();
            if (lastSegment != null && lastSegment.contains(".") && lastSegment.length() > 1) {
                int q = lastSegment.indexOf('?');
                if (q >= 0) lastSegment = lastSegment.substring(0, q);
                return lastSegment;
            }
        } catch (Exception e) { /* ignore */ }

        // 5. Fallback to URLUtil.guessFileName
        return android.webkit.URLUtil.guessFileName(realUrl, contentDisposition, mimetype);
    }

    /**
     * Extract the domain (e.g., "example.com") from a URL.
     */
    private String extractDomainFromUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return null;
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return null;
        }
    }

    private java.util.List<File> getAvailableStorageRoots() {
        java.util.List<File> roots = new java.util.ArrayList<>();
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        return roots;
    }

    private File getDownloadBaseDir() {
        String saved = prefs().getString(KEY_DOWNLOAD_DIR, null);
        if (saved != null) {
            if (saved.contains("/Android/data/")) {
                prefs().edit().remove(KEY_DOWNLOAD_DIR).apply();
            } else {
                File dir = new File(saved);
                if (dir.exists() || dir.mkdirs()) {
                    return dir;
                }
            }
        }
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    private void setDownloadBaseDir(String path) {
        prefs().edit().putString(KEY_DOWNLOAD_DIR, path).apply();
    }

    private void showChangeDownloadLocation() {
        java.util.List<File> roots = getAvailableStorageRoots();
        java.util.List<String> options = new java.util.ArrayList<>();
        for (File root : roots) {
            options.add(root.getAbsolutePath());
        }
        options.add("Custom folder name...");

        String[] optionsArr = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Choose download location")
                .setItems(optionsArr, (dialog, which) -> {
                    if (which == optionsArr.length - 1) {
                        promptCustomDownloadFolder();
                    } else {
                        setDownloadBaseDir(optionsArr[which]);
                        Toast.makeText(this, "Downloads will save to:\n" + optionsArr[which], Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptCustomDownloadFolder() {
        final EditText input = new EditText(this);
        input.setHint("e.g. MyFiles");
        new AlertDialog.Builder(this)
                .setTitle("Custom folder (inside internal storage)")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File custom = new File(base, name);
                    custom.mkdirs();
                    setDownloadBaseDir(custom.getAbsolutePath());
                    Toast.makeText(this, "Downloads will save to:\n" + custom.getAbsolutePath(), Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startFileDownload(String url, String userAgent, String contentDisposition, String mimetype, String fileName) {
        try {
            String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
            String resolvedMime = mimetype;
            if (extension != null && !extension.isEmpty()) {
                String extMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                if (extMime != null) {
                    resolvedMime = extMime;
                }
            }
            if (resolvedMime == null || resolvedMime.isEmpty() || resolvedMime.equals("application/octet-stream")) {
                if (fileName.toLowerCase().endsWith(".apk")) {
                    resolvedMime = "application/vnd.android.package-archive";
                }
            }

            File baseDir = getDownloadBaseDir();
            File destDir = new File(baseDir, "PanGalacticMonkey");
            destDir.mkdirs();
            File destFile = new File(destDir, fileName);
            String fullPath = destFile.getAbsolutePath();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(resolvedMime);
            request.addRequestHeader("User-Agent", userAgent != null ? userAgent
                    : "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            request.setDescription("Downloading " + fileName);
            request.setTitle(fileName);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationUri(Uri.fromFile(destFile));

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            long downloadId = dm.enqueue(request);

            recordDownload(downloadId, fileName, fullPath);
            Toast.makeText(this, "Downloading: " + fileName, Toast.LENGTH_SHORT).show();
            watchDownloadCompletion(downloadId, fileName, fullPath);
        } catch (Exception e) {
            Log.e("TVBrowser", "startFileDownload failed", e);
            Toast.makeText(this, "Download failed to start", Toast.LENGTH_SHORT).show();
        }
    }

    private void watchDownloadCompletion(long downloadId, String fileName, String fullPath) {
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Runnable[] pollRunnable = new Runnable[1];
        pollRunnable[0] = () -> {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            android.database.Cursor cursor = dm.query(query);
            int status = -1;
            if (cursor != null && cursor.moveToFirst()) {
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (statusIdx >= 0) status = cursor.getInt(statusIdx);
                cursor.close();
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                makeFileReadableAndScan(fullPath);
                showDownloadCompleteDialog(fileName, fullPath, true);
            } else if (status == DownloadManager.STATUS_FAILED) {
                showDownloadCompleteDialog(fileName, fullPath, false);
            } else {
                handler.postDelayed(pollRunnable[0], 800);
            }
        };
        handler.postDelayed(pollRunnable[0], 800);
    }

    private void makeFileReadableAndScan(String fullPath) {
        try {
            File file = new File(fullPath);
            if (file.exists()) {
                file.setReadable(true, false);
                file.setExecutable(true, false);
            }
            File parent = file.getParentFile();
            while (parent != null && parent.exists()
                    && parent.getAbsolutePath().contains(
                            Environment.getExternalStorageDirectory().getAbsolutePath())) {
                parent.setReadable(true, false);
                parent.setExecutable(true, false);
                parent = parent.getParentFile();
            }
            android.media.MediaScannerConnection.scanFile(
                    this, new String[]{fullPath}, null, null);
        } catch (Exception e) {
            Log.e("TVBrowser", "makeFileReadableAndScan failed", e);
        }
    }

    private void showDownloadCompleteDialog(String fileName, String fullPath, boolean success) {
        String message = fullPath;
        if (!success) {
            message = "Download failed.\n\n" + fullPath;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(success ? "Download complete: " + fileName : "Download failed: " + fileName)
                .setMessage(message);

        if (success) {
            String actionLabel = fullPath.toLowerCase().endsWith(".apk") ? "Install" : "Open";
            builder.setPositiveButton(actionLabel, (d, w) -> openDownloadedFile(fullPath));
        }
        builder.setNeutralButton("Delete", (d, w) -> {
            deleteDownloadedFile(fullPath);
            int idx = findDownloadIndexByPath(fullPath);
            if (idx >= 0) removeDownloadRecord(idx);
            Toast.makeText(this, "Deleted: " + fileName, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Done", null);
        builder.show();
    }

    private int findDownloadIndexByPath(String path) {
        JSONArray arr = loadDownloads();
        for (int i = 0; i < arr.length(); i++) {
            try {
                if (path.equals(arr.getJSONObject(i).optString("path"))) {
                    return i;
                }
            } catch (JSONException e) {
                Log.e("TVBrowser", "findDownloadIndexByPath failed", e);
            }
        }
        return -1;
    }

    private JSONArray loadDownloads() {
        String raw = prefs().getString(KEY_DOWNLOADS, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveDownloads(JSONArray arr) {
        prefs().edit().putString(KEY_DOWNLOADS, arr.toString()).apply();
    }

    private void recordDownload(long downloadId, String fileName, String filePath) {
        JSONArray arr = loadDownloads();
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", downloadId);
            obj.put("name", fileName);
            obj.put("path", filePath);
            obj.put("status", "downloading");
            arr.put(obj);
            saveDownloads(arr);
        } catch (JSONException e) {
            Log.e("TVBrowser", "recordDownload failed", e);
        }
    }

    private void removeDownloadRecord(int index) {
        JSONArray arr = loadDownloads();
        JSONArray newArr = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i != index) {
                newArr.put(arr.opt(i));
            }
        }
        saveDownloads(newArr);
    }

    private void refreshDownloadStatuses() {
        JSONArray arr = loadDownloads();
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                long id = obj.optLong("id", -1);
                if (id < 0) continue;
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(id);
                android.database.Cursor cursor = dm.query(query);
                if (cursor != null && cursor.moveToFirst()) {
                    int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = statusIdx >= 0 ? cursor.getInt(statusIdx) : -1;
                    String statusStr = "downloading";
                    if (status == DownloadManager.STATUS_SUCCESSFUL) statusStr = "complete";
                    else if (status == DownloadManager.STATUS_FAILED) statusStr = "failed";
                    obj.put("status", statusStr);
                    cursor.close();
                }
            } catch (Exception e) {
                Log.e("TVBrowser", "refreshDownloadStatuses failed", e);
            }
        }
        saveDownloads(arr);
    }

    private void showDownloadsManager() {
        refreshDownloadStatuses();
        JSONArray arr = loadDownloads();
        int count = arr.length();
        if (count == 0) {
            Toast.makeText(this, "No downloads yet", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout listHolder = new android.widget.LinearLayout(this);
        listHolder.setOrientation(android.widget.LinearLayout.VERTICAL);
        scrollView.addView(listHolder);
        container.addView(scrollView);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        Runnable[] rebuildHolder = new Runnable[1];

        Runnable rebuild = () -> {
            listHolder.removeAllViews();
            JSONArray current = loadDownloads();
            if (current.length() == 0) {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                return;
            }
            for (int i = 0; i < current.length(); i++) {
                final int idx = i;
                JSONObject item;
                try {
                    item = current.getJSONObject(i);
                } catch (JSONException e) {
                    continue;
                }
                String name = item.optString("name", "file");
                String path = item.optString("path", "");
                String status = item.optString("status", "downloading");

                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.VERTICAL);
                row.setPadding(12, 12, 12, 12);
                row.setBackgroundColor(Color.parseColor("#222222"));
                android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, 8);
                row.setLayoutParams(rowLp);

                TextView nameView = new TextView(this);
                nameView.setText(name + "  [" + status + "]");
                nameView.setTextColor(Color.WHITE);
                nameView.setTextSize(16f);
                row.addView(nameView);

                TextView pathView = new TextView(this);
                pathView.setText(path);
                pathView.setTextColor(Color.parseColor("#AAAAAA"));
                pathView.setTextSize(12f);
                pathView.setPadding(0, 4, 0, 8);
                row.addView(pathView);

                android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
                btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);

                String actionLabel = path.toLowerCase().endsWith(".apk") ? "Install" : "Open";
                Button installBtn = makeDownloadActionButton("complete".equals(status) ? actionLabel : "Not Ready");
                installBtn.setEnabled("complete".equals(status));
                installBtn.setOnClickListener(v -> openDownloadedFile(path));
                btnRow.addView(installBtn);

                Button deleteBtn = makeDownloadActionButton("Delete");
                deleteBtn.setOnClickListener(v -> {
                    deleteDownloadedFile(path);
                    removeDownloadRecord(idx);
                    Toast.makeText(this, "Deleted: " + name, Toast.LENGTH_SHORT).show();
                    if (rebuildHolder[0] != null) rebuildHolder[0].run();
                });
                btnRow.addView(deleteBtn);

                Button doneBtn = makeDownloadActionButton("Done");
                doneBtn.setOnClickListener(v -> {
                    removeDownloadRecord(idx);
                    if (rebuildHolder[0] != null) rebuildHolder[0].run();
                });
                btnRow.addView(doneBtn);

                row.addView(btnRow);
                listHolder.addView(row);
            }
        };
        rebuildHolder[0] = rebuild;
        rebuild.run();

        dialogHolder[0] = new AlertDialog.Builder(this)
                .setTitle("Downloads")
                .setView(container)
                .setNegativeButton("Close", null)
                .create();
        dialogHolder[0].show();
    }

    private Button makeDownloadActionButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#555555"));
        b.setTextSize(12f);
        b.setPadding(16, 4, 16, 4);
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            b.setBackgroundColor(Color.parseColor(hasFocus ? "#3DFF71" : "#555555"));
            b.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
        });
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, 8, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void openDownloadedFile(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider", file);

            boolean isApk = path.toLowerCase().endsWith(".apk");
            String mime;
            if (isApk) {
                mime = "application/vnd.android.package-archive";
            } else {
                String extension = MimeTypeMap.getFileExtensionFromUrl(path);
                mime = extension != null
                        ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase())
                        : null;
                if (mime == null) mime = "*/*";
            }

            if (isApk && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                    && !getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this,
                        "Enable \"Install unknown apps\" for this browser, then try again",
                        Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settingsIntent);
                return;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            java.util.List<android.content.pm.ResolveInfo> resolvedApps =
                    getPackageManager().queryIntentActivities(intent, 0);
            for (android.content.pm.ResolveInfo resolveInfo : resolvedApps) {
                String packageName = resolveInfo.activityInfo.packageName;
                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("TVBrowser", "openDownloadedFile failed", e);
            Toast.makeText(this, "Unable to open file", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteDownloadedFile(String path) {
        try {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            Log.e("TVBrowser", "deleteDownloadedFile failed", e);
        }
    }

    // ---------- History ----------
    private JSONArray loadHistory() {
        String raw = prefs().getString(KEY_HISTORY, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveHistory(JSONArray arr) {
        prefs().edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void recordHistory(String title, String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) {
            return;
        }
        JSONArray arr = loadHistory();
        try {
            if (arr.length() > 0) {
                JSONObject mostRecent = arr.getJSONObject(0);
                if (url.equals(mostRecent.optString("url"))) {
                    return;
                }
            }

            JSONArray newArr = new JSONArray();
            JSONObject entry = new JSONObject();
            entry.put("title", title != null && !title.isEmpty() ? title : url);
            entry.put("url", url);
            entry.put("time", System.currentTimeMillis());
            newArr.put(entry);

            for (int i = 0; i < arr.length() && newArr.length() < MAX_HISTORY_ITEMS; i++) {
                newArr.put(arr.getJSONObject(i));
            }
            saveHistory(newArr);
        } catch (JSONException e) {
            Log.e("TVBrowser", "recordHistory failed", e);
        }
    }

    private void clearHistory() {
        saveHistory(new JSONArray());
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
    }

    private void removeHistoryEntry(int index) {
        JSONArray arr = loadHistory();
        JSONArray newArr = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i != index) {
                newArr.put(arr.opt(i));
            }
        }
        saveHistory(newArr);
    }

    private void showHistoryManager() {
        JSONArray arr = loadHistory();
        int count = arr.length();
        if (count == 0) {
            Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout listHolder = new android.widget.LinearLayout(this);
        listHolder.setOrientation(android.widget.LinearLayout.VERTICAL);
        scrollView.addView(listHolder);
        container.addView(scrollView, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog[] dialogHolder = new AlertDialog[1];
        Runnable[] rebuildHolder = new Runnable[1];

        Runnable rebuild = () -> {
            listHolder.removeAllViews();
            JSONArray current = loadHistory();
            if (current.length() == 0) {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                return;
            }
            for (int i = 0; i < current.length(); i++) {
                final int idx = i;
                JSONObject item;
                try {
                    item = current.getJSONObject(i);
                } catch (JSONException e) {
                    continue;
                }
                String title = item.optString("title", "page");
                String url = item.optString("url", "");

                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setPadding(12, 12, 12, 12);
                android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, 4);
                row.setLayoutParams(rowLp);
                row.setBackgroundColor(Color.parseColor("#222222"));

                android.widget.LinearLayout textCol = new android.widget.LinearLayout(this);
                textCol.setOrientation(android.widget.LinearLayout.VERTICAL);
                textCol.setFocusable(true);
                textCol.setFocusableInTouchMode(true);
                textCol.setPadding(12, 8, 12, 8);
                textCol.setOnFocusChangeListener((v, hasFocus) ->
                        row.setBackgroundColor(Color.parseColor(hasFocus ? "#3DFF71" : "#222222")));

                TextView titleView = new TextView(this);
                titleView.setText(title);
                titleView.setTextColor(Color.WHITE);
                titleView.setTextSize(15f);
                textCol.addView(titleView);

                TextView urlView = new TextView(this);
                urlView.setText(url);
                urlView.setTextColor(Color.parseColor("#AAAAAA"));
                urlView.setTextSize(11f);
                textCol.addView(urlView);

                textCol.setOnClickListener(v -> {
                    webView.loadUrl(url);
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                });

                android.widget.LinearLayout.LayoutParams textLp = new android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                row.addView(textCol, textLp);

                Button deleteBtn = new Button(this);
                deleteBtn.setText("\u2715");
                deleteBtn.setTextColor(Color.WHITE);
                deleteBtn.setBackgroundColor(Color.parseColor("#555555"));
                deleteBtn.setMinWidth(0);
                deleteBtn.setMinHeight(0);
                deleteBtn.setPadding(24, 8, 24, 8);
                deleteBtn.setFocusable(true);
                deleteBtn.setOnFocusChangeListener((v, hasFocus) -> {
                    deleteBtn.setBackgroundColor(Color.parseColor(hasFocus ? "#3DFF71" : "#555555"));
                    deleteBtn.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
                });
                deleteBtn.setOnClickListener(v -> {
                    removeHistoryEntry(idx);
                    if (rebuildHolder[0] != null) rebuildHolder[0].run();
                });
                row.addView(deleteBtn);

                listHolder.addView(row);
            }
        };
        rebuildHolder[0] = rebuild;
        rebuild.run();

        Button clearAllBtn = new Button(this);
        clearAllBtn.setText("Clear All History");
        clearAllBtn.setTextColor(Color.WHITE);
        clearAllBtn.setBackgroundColor(Color.parseColor("#555555"));
        clearAllBtn.setFocusable(true);
        clearAllBtn.setOnFocusChangeListener((v, hasFocus) -> {
            clearAllBtn.setBackgroundColor(Color.parseColor(hasFocus ? "#3DFF71" : "#555555"));
            clearAllBtn.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
        });
        clearAllBtn.setOnClickListener(v -> {
            clearHistory();
            if (rebuildHolder[0] != null) rebuildHolder[0].run();
        });
        container.addView(clearAllBtn, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        dialogHolder[0] = new AlertDialog.Builder(this)
                .setTitle("History")
                .setView(container)
                .setNegativeButton("Close", null)
                .create();
        dialogHolder[0].show();
    }

    // ---------- Private Browsing ----------
    private void togglePrivateBrowsing() {
        privateBrowsingEnabled = !privateBrowsingEnabled;
        if (privateBrowsingEnabled) {
            android.webkit.CookieManager.getInstance().setAcceptCookie(false);
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            webView.clearHistory();
            webView.clearCache(true);
            webView.clearFormData();
            Toast.makeText(this, "Private browsing enabled", Toast.LENGTH_SHORT).show();
        } else {
            android.webkit.CookieManager.getInstance().setAcceptCookie(true);
            Toast.makeText(this, "Private browsing disabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleDesktopMode() {
        desktopModeEnabled = !desktopModeEnabled;
        prefs().edit().putBoolean(KEY_DESKTOP_MODE, desktopModeEnabled).apply();

        String ua = desktopModeEnabled ? DESKTOP_USER_AGENT : MOBILE_USER_AGENT;
        for (TabData tab : tabs) {
            if (tab.view != null) {
                tab.view.getSettings().setUserAgentString(ua);
            }
        }

        if (webView != null) {
            webView.reload();
        }

        Toast.makeText(this,
                desktopModeEnabled ? "Requesting desktop site" : "Requesting mobile site",
                Toast.LENGTH_SHORT).show();
    }

    // ---------- Menu ----------
    private void showBookmarksMenu() {
        String[] options = {
            "View Bookmarks",
            "Add Current Page",
            "Change Homepage",
            "Downloads",
            "Change Download Location",
            "History",
            privateBrowsingEnabled ? "Private Browsing: ON" : "Private Browsing: OFF",
            desktopModeEnabled ? "Request Desktop Site: ON" : "Request Desktop Site: OFF",
            "Zoom In",
            "Zoom Out",
            "Cursor & Scroll Speed",
            cursorAutoHideEnabled ? "Auto‑Hide Cursor: ON" : "Auto‑Hide Cursor: OFF",
            javascriptEnabled ? "JavaScript: ON" : "JavaScript: OFF",
            "Ad Block Filters",
            "Whitelist Current Site",
            "Manage Whitelist"
        };

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, options));

        android.widget.TextView footer = new android.widget.TextView(this);
        footer.setText("created by burnSYMBOL.com");
        footer.setTextColor(Color.parseColor("#888888"));
        footer.setTextSize(12f);
        footer.setPadding(24, 16, 24, 16);
        footer.setGravity(android.view.Gravity.CENTER);

        container.addView(listView, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        container.addView(footer, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Menu")
                .setView(container)
                .setNegativeButton("Close", null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            switch (position) {
                case 0: showBookmarksList(); break;
                case 1: promptAddBookmark(); break;
                case 2: promptChangeHomepage(); break;
                case 3: showDownloadsManager(); break;
                case 4: showChangeDownloadLocation(); break;
                case 5: showHistoryManager(); break;
                case 6: togglePrivateBrowsing(); break;
                case 7: toggleDesktopMode(); break;
                case 8: zoomIn(); break;
                case 9: zoomOut(); break;
                case 10: showSpeedSettings(); break;
                case 11: toggleCursorAutoHide(); break;
                case 12: toggleJavaScript(); break;
                case 13: showAdBlockManagerDialog(); break;
                case 14: toggleWhitelistCurrentSite(); break;
                case 15: showWhitelistManager(); break;
            }
        });

        dialog.show();
        requestDialogButtonFocus(dialog);
    }

    // ---------- Whitelist ----------
    private void toggleWhitelistCurrentSite() {
        String url = webView.getUrl();
        if (url == null) {
            Toast.makeText(this, "No page loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        String host = Uri.parse(url).getHost();
        if (host == null) {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean isWhitelisted = adBlockManager.isWhitelisted(host);
        if (isWhitelisted) {
            adBlockManager.removeWhitelist(host);
            Toast.makeText(this, "Removed from whitelist: " + host, Toast.LENGTH_SHORT).show();
        } else {
            adBlockManager.addWhitelist(host);
            Toast.makeText(this, "Added to whitelist: " + host, Toast.LENGTH_SHORT).show();
        }
        webView.clearCache(true);
        webView.reload();
    }

    private void showWhitelistManager() {
        Set<String> whitelist = adBlockManager.getWhitelist();
        if (whitelist.isEmpty()) {
            Toast.makeText(this, "No sites whitelisted", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Whitelisted Sites");
        String[] items = whitelist.toArray(new String[0]);
        builder.setItems(items, (dialog, which) -> {
            String host = items[which];
            adBlockManager.removeWhitelist(host);
            Toast.makeText(this, "Removed: " + host, Toast.LENGTH_SHORT).show();
            showWhitelistManager();
        });
        builder.setNegativeButton("Close", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        requestDialogButtonFocus(dialog);
    }

    // ---------- AdBlock Manager Dialog (scrollable, in-place update, yellow focus) ----------
    private AlertDialog adBlockDialog = null;

    private void showAdBlockManagerDialog() {
        if (adBlockDialog != null && adBlockDialog.isShowing()) {
            updateAdBlockDialogContent();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ad Block Filters");

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setFillViewport(true);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        buildAdBlockContent(layout);

        scrollView.addView(layout);
        builder.setView(scrollView);
        builder.setNegativeButton("Close", (d, w) -> { adBlockDialog = null; });

        adBlockDialog = builder.create();
        adBlockDialog.show();
        requestDialogButtonFocus(adBlockDialog);
    }

    private void updateAdBlockDialogContent() {
        if (adBlockDialog == null || !adBlockDialog.isShowing()) return;
        View contentView = adBlockDialog.findViewById(android.R.id.content);
        if (contentView instanceof android.widget.ScrollView) {
            android.widget.ScrollView scrollView = (android.widget.ScrollView) contentView;
            if (scrollView.getChildCount() > 0 && scrollView.getChildAt(0) instanceof android.widget.LinearLayout) {
                android.widget.LinearLayout layout = (android.widget.LinearLayout) scrollView.getChildAt(0);
                layout.removeAllViews();
                buildAdBlockContent(layout);
                layout.requestLayout();
            }
        }
    }

    private void buildAdBlockContent(android.widget.LinearLayout layout) {
        // Toggle
        android.widget.LinearLayout toggleRow = new android.widget.LinearLayout(this);
        toggleRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        toggleRow.setPadding(0, 0, 0, 16);

        TextView toggleLabel = new TextView(this);
        toggleLabel.setText("AdBlock Enabled: ");
        toggleLabel.setTextColor(Color.WHITE);
        toggleLabel.setTextSize(16f);
        toggleRow.addView(toggleLabel);

        final Button toggleButton = new Button(this);
        boolean enabled = adBlockManager.isEnabled();
        toggleButton.setText(enabled ? "ON" : "OFF");
        toggleButton.setBackgroundColor(enabled ? Color.GREEN : Color.RED);
        toggleButton.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) toggleButton.setBackgroundColor(Color.YELLOW);
            else toggleButton.setBackgroundColor(enabled ? Color.GREEN : Color.RED);
        });
        toggleButton.setOnClickListener(v -> {
            boolean newState = !adBlockManager.isEnabled();
            adBlockManager.setEnabled(newState);
            toggleButton.setText(newState ? "ON" : "OFF");
            toggleButton.setBackgroundColor(newState ? Color.GREEN : Color.RED);
            webView.clearCache(true);
            webView.reload();
            Toast.makeText(MainActivity.this, "AdBlock " + (newState ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
        });
        toggleRow.addView(toggleButton);
        layout.addView(toggleRow);

        // List filters
        Set<String> currentUrls = adBlockManager.getFilterUrls();
        if (currentUrls.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No filter lists added.");
            emptyText.setTextColor(Color.GRAY);
            layout.addView(emptyText);
        } else {
            for (String url : currentUrls) {
                android.widget.LinearLayout row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                row.setPadding(0, 8, 0, 8);

                TextView urlText = new TextView(this);
                urlText.setText(url);
                urlText.setTextColor(Color.WHITE);
                urlText.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                row.addView(urlText);

                Button removeBtn = new Button(this);
                removeBtn.setText("Remove");
                removeBtn.setTextColor(Color.WHITE);
                removeBtn.setBackgroundColor(Color.RED);
                removeBtn.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) removeBtn.setBackgroundColor(Color.YELLOW);
                    else removeBtn.setBackgroundColor(Color.RED);
                });
                removeBtn.setOnClickListener(v -> {
                    adBlockManager.removeFilterUrl(url);
                    webView.clearCache(true);
                    updateAdBlockDialogContent();
                });
                row.addView(removeBtn);
                layout.addView(row);
            }
        }

        View divider = new View(this);
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(Color.GRAY);
        layout.addView(divider);

        // Add new filter
        TextView addTitle = new TextView(this);
        addTitle.setText("Add New Filter List:");
        addTitle.setTextColor(Color.WHITE);
        addTitle.setPadding(0, 16, 0, 8);
        layout.addView(addTitle);

        final EditText input = new EditText(this);
        input.setHint("https://example.com/filter.txt");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        layout.addView(input);

        android.widget.LinearLayout buttonRow = new android.widget.LinearLayout(this);
        buttonRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 8, 0, 8);

        Button addBtn = new Button(this);
        addBtn.setText("Add");
        addBtn.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) addBtn.setBackgroundColor(Color.YELLOW);
            else addBtn.setBackgroundColor(Color.parseColor("#555555"));
        });
        addBtn.setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                adBlockManager.addFilterUrl(url);
                new Thread(() -> adBlockManager.loadFilters()).start();
                webView.clearCache(true);
                updateAdBlockDialogContent();
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            }
        });
        buttonRow.addView(addBtn);

        Button pasteBtn = new Button(this);
        pasteBtn.setText("Paste");
        pasteBtn.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) pasteBtn.setBackgroundColor(Color.YELLOW);
            else pasteBtn.setBackgroundColor(Color.parseColor("#555555"));
        });
        pasteBtn.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                String text = item.getText().toString();
                if (text != null) input.setText(text);
            }
        });
        buttonRow.addView(pasteBtn);

        layout.addView(buttonRow);

        // Presets
        TextView presetTitle = new TextView(this);
        presetTitle.setText("Quick Add Presets:");
        presetTitle.setTextColor(Color.WHITE);
        presetTitle.setPadding(0, 16, 0, 8);
        layout.addView(presetTitle);

        android.widget.LinearLayout presetRow1 = new android.widget.LinearLayout(this);
        presetRow1.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        presetRow1.setPadding(0, 0, 0, 8);

        android.widget.LinearLayout presetRow2 = new android.widget.LinearLayout(this);
        presetRow2.setOrientation(android.widget.LinearLayout.HORIZONTAL);

        String[][] presets = {
            {"Fanboy Annoyances", "https://easylist.to/easylist/fanboy-annoyance.txt"},
            {"EasyList Cookie", "https://easylist.to/easylist/easyprivacy.txt"},
            {"AdGuard Mobile", "https://filters.adtidy.org/extension/chromium/filters/11.txt"},
            {"NoCoin", "https://filters.adtidy.org/extension/chromium/filters/242.txt"}
        };

        for (int i = 0; i < presets.length; i++) {
            final String name = presets[i][0];
            final String url = presets[i][1];
            Button presetBtn = new Button(this);
            presetBtn.setText(name);
            presetBtn.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) presetBtn.setBackgroundColor(Color.YELLOW);
                else presetBtn.setBackgroundColor(Color.parseColor("#555555"));
            });
            presetBtn.setOnClickListener(v -> {
                adBlockManager.addFilterUrl(url);
                new Thread(() -> adBlockManager.loadFilters()).start();
                webView.clearCache(true);
                Toast.makeText(MainActivity.this, "Added " + name, Toast.LENGTH_SHORT).show();
                updateAdBlockDialogContent();
            });
            if (i < 2) presetRow1.addView(presetBtn);
            else presetRow2.addView(presetBtn);
        }
        layout.addView(presetRow1);
        layout.addView(presetRow2);

        View spacer = new View(this);
        spacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 16));
        layout.addView(spacer);
    }

    // ---------- Speed Settings ----------
    private void showSpeedSettings() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        TextView cursorLabel = new TextView(this);
        cursorLabel.setText("Cursor Speed: " + (int) cursorStep);
        layout.addView(cursorLabel);

        android.widget.SeekBar cursorSeek = new android.widget.SeekBar(this);
        cursorSeek.setMax(90);
        cursorSeek.setProgress((int) cursorStep - 10);
        cursorSeek.setKeyProgressIncrement(1);
        layout.addView(cursorSeek);

        android.widget.LinearLayout cursorBtnRow = new android.widget.LinearLayout(this);
        cursorBtnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        Button cursorDec = new Button(this);
        cursorDec.setText("-1");
        cursorDec.setOnClickListener(v -> {
            int val = (int) cursorStep - 1;
            if (val < 10) val = 10;
            cursorStep = val;
            cursorSeek.setProgress(val - 10);
            cursorLabel.setText("Cursor Speed: " + val);
        });
        cursorBtnRow.addView(cursorDec);

        Button cursorInc = new Button(this);
        cursorInc.setText("+1");
        cursorInc.setOnClickListener(v -> {
            int val = (int) cursorStep + 1;
            if (val > 100) val = 100;
            cursorStep = val;
            cursorSeek.setProgress(val - 10);
            cursorLabel.setText("Cursor Speed: " + val);
        });
        cursorBtnRow.addView(cursorInc);
        layout.addView(cursorBtnRow);

        TextView scrollLabel = new TextView(this);
        scrollLabel.setText("Scroll Speed: " + (int) scrollStep);
        scrollLabel.setPadding(0, 24, 0, 0);
        layout.addView(scrollLabel);

        android.widget.SeekBar scrollSeek = new android.widget.SeekBar(this);
        scrollSeek.setMax(380);
        scrollSeek.setProgress((int) scrollStep - 20);
        scrollSeek.setKeyProgressIncrement(1);
        layout.addView(scrollSeek);

        android.widget.LinearLayout scrollBtnRow = new android.widget.LinearLayout(this);
        scrollBtnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        Button scrollDec = new Button(this);
        scrollDec.setText("-1");
        scrollDec.setOnClickListener(v -> {
            int val = (int) scrollStep - 1;
            if (val < 20) val = 20;
            scrollStep = val;
            scrollSeek.setProgress(val - 20);
            scrollLabel.setText("Scroll Speed: " + val);
        });
        scrollBtnRow.addView(scrollDec);

        Button scrollInc = new Button(this);
        scrollInc.setText("+1");
        scrollInc.setOnClickListener(v -> {
            int val = (int) scrollStep + 1;
            if (val > 400) val = 400;
            scrollStep = val;
            scrollSeek.setProgress(val - 20);
            scrollLabel.setText("Scroll Speed: " + val);
        });
        scrollBtnRow.addView(scrollInc);
        layout.addView(scrollBtnRow);

        cursorSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + 10;
                cursorStep = val;
                cursorLabel.setText("Cursor Speed: " + val);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        scrollSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + 20;
                scrollStep = val;
                scrollLabel.setText("Scroll Speed: " + val);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Cursor & Scroll Speed")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    prefs().edit()
                            .putFloat(KEY_CURSOR_SPEED, cursorStep)
                            .putFloat(KEY_SCROLL_SPEED, scrollStep)
                            .apply();
                    Toast.makeText(this, "Speed settings saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void makeCurrentPageHomepage() {
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "No page loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        setHomepage(currentUrl);
        Toast.makeText(this, "Homepage set to current page", Toast.LENGTH_SHORT).show();
    }

    private void promptAddBookmark() {
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "No page loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setText(webView.getTitle() != null ? webView.getTitle() : currentUrl);
        new AlertDialog.Builder(this)
                .setTitle("Bookmark name")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = currentUrl;
                    addBookmark(name, currentUrl);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBookmarksList() {
        JSONArray arr = loadBookmarks();
        int count = arr.length();
        if (count == 0) {
            Toast.makeText(this, "No bookmarks yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            try {
                names[i] = arr.getJSONObject(i).optString("name", "bookmark " + i);
            } catch (JSONException e) {
                names[i] = "bookmark " + i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Bookmarks")
                .setItems(names, (dialog, which) -> {
                    try {
                        String url = arr.getJSONObject(which).getString("url");
                        webView.loadUrl(url);
                    } catch (JSONException e) {
                        Log.e("TVBrowser", "open bookmark failed", e);
                    }
                })
                .setNeutralButton("Remove...", (dialog, which) -> promptRemoveBookmark(names))
                .setNegativeButton("Close", null)
                .show();
    }

    private void promptRemoveBookmark(String[] names) {
        new AlertDialog.Builder(this)
                .setTitle("Remove which bookmark?")
                .setItems(names, (dialog, which) -> {
                    removeBookmark(which);
                    Toast.makeText(this, "Removed: " + names[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptChangeHomepage() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(getHomepage());
        new AlertDialog.Builder(this)
                .setTitle("Set homepage")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://" + url;
                        }
                        setHomepage(url);
                        Toast.makeText(this, "Homepage updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Use Current Page", (d, w) -> makeCurrentPageHomepage())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemove(int index, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remove script?")
                .setMessage(name)
                .setPositiveButton("Remove", (d, w) -> {
                    removeScript(index);
                    refreshDocumentStartScriptsOnAllTabs();
                    Toast.makeText(this, "Removed: " + name, Toast.LENGTH_SHORT).show();
                    webView.reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    // ---------- .txt popup ----------
    private void showTextUrlPopup(String url) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Filter List URL");
            builder.setMessage("Copy this URL to add it as a filter list:\n\n" + url);
            builder.setPositiveButton("Copy URL", (d, w) -> {
                copyToClipboard("Filter URL", url);
            });
            builder.setNegativeButton("Close", null);
            AlertDialog dialog = builder.create();
            dialog.show();
            requestDialogButtonFocus(dialog);
        });
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    // ---------- Long-press link popup ----------
    private void showLinkPopup(String url, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Link");
        String display = text.isEmpty() ? url : text + "\n" + url;
        builder.setMessage(display);
        builder.setPositiveButton("Copy Link", (d, w) -> copyToClipboard("Link", url));
        builder.setNegativeButton("Close", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        requestDialogButtonFocus(dialog);
    }

    // ---------- Unified long-press with improved favicon filtering ----------
    private void checkLongPressForElement() {
        // First, try using WebView's HitTestResult
        WebView.HitTestResult result = webView.getHitTestResult();
        if (result != null) {
            int type = result.getType();
            String extra = result.getExtra();
            if (extra != null && extra.startsWith("http") && !extra.contains("faviconV2") && !extra.contains("favicon")) {
                if (type == WebView.HitTestResult.IMAGE_TYPE ||
                    type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    offerSaveForUrl(extra);
                    return;
                } else if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                    showLinkPopup(extra, "");
                    return;
                }
            }
            // If it's a favicon, fall through to JavaScript
        }

        String currentUrl = webView.getUrl();
        if (hasSaveableExtension(currentUrl)) {
            offerSaveForUrl(currentUrl);
            return;
        }

        String js;
        if (cursorModeEnabled) {
            int[] cursorLoc = new int[2];
            cursorView.getLocationOnScreen(cursorLoc);
            float screenX = cursorLoc[0] + cursorView.getCursorX();
            float screenY = cursorLoc[1] + cursorView.getCursorY();

            int[] webViewLoc = new int[2];
            webView.getLocationOnScreen(webViewLoc);
            float localX = screenX - webViewLoc[0];
            float localY = screenY - webViewLoc[1];

            js = "(function(){" +
                "  var ratio = window.devicePixelRatio || 1;" +
                "  var cssX = " + localX + " / ratio;" +
                "  var cssY = " + localY + " / ratio;" +
                "  var el = document.elementFromPoint(cssX, cssY);" +
                "  return el ? __tvResolveElement(el) : '';" +
                "})()";
        } else {
            js = "(function(){" +
                "  var el = document.activeElement;" +
                "  return el ? __tvResolveElement(el) : '';" +
                "})()";
        }

        // Improved resolver: skips any URL containing "favicon", extracts img from anchors,
        // and prioritises data-src, srcset, and Google imgurl.
        String helper =
            "window.__tvResolveElement = window.__tvResolveElement || function(el){" +
            "  var cur = el;" +
            "  for (var depth = 0; cur && depth < 5; depth++, cur = cur.parentElement) {" +
            "    // IMG tags" +
            "    if (cur.tagName === 'IMG') {" +
            "      var src = cur.src;" +
            "      var dataSrc = cur.dataset.src || cur.getAttribute('data-src') || cur.getAttribute('data-original') || cur.getAttribute('data-lazy-src');" +
            "      if (dataSrc && dataSrc.startsWith('http')) src = dataSrc;" +
            "      if (!src || src.startsWith('data:') || src.startsWith('blob:')) {" +
            "        if (cur.srcset) {" +
            "          var parts = cur.srcset.split(',');" +
            "          var best = '', maxW = 0;" +
            "          for (var i = 0; i < parts.length; i++) {" +
            "            var entry = parts[i].trim().split(/\\s+/);" +
            "            var url = entry[0];" +
            "            var w = parseInt(entry[1]) || 0;" +
            "            if (w > maxW) { maxW = w; best = url; }" +
            "          }" +
            "          if (best) src = best;" +
            "        }" +
            "      }" +
            "      if (src && src.startsWith('http') && !src.includes('faviconV2') && !src.includes('favicon')) {" +
            "        return 'img|' + src;" +
            "      }" +
            "      // if favicon, continue searching upward" +
            "      continue;" +
            "    }" +
            "    // VIDEO / AUDIO" +
            "    if (cur.tagName === 'VIDEO' || cur.tagName === 'AUDIO') {" +
            "      var src = cur.currentSrc || cur.src || (cur.querySelector('source[src]') ? cur.querySelector('source[src]').src : '');" +
            "      if (src && src.startsWith('http') && !src.includes('faviconV2') && !src.includes('favicon')) return 'media|' + src;" +
            "    }" +
            "    // LINK (anchor) – first look for an IMG inside, then fallback to href with imgurl extraction" +
            "    if (cur.tagName === 'A' && cur.href) {" +
            "      var href = cur.href;" +
            "      // Check for Google imgurl" +
            "      var match = href.match(/[?&]imgurl=([^&]+)/);" +
            "      if (match) {" +
            "        var realUrl = decodeURIComponent(match[1]);" +
            "        if (realUrl.startsWith('http') && !realUrl.includes('faviconV2') && !realUrl.includes('favicon')) {" +
            "          var ext = realUrl.split('.').pop().split('?')[0].toLowerCase();" +
            "          if (['jpg','jpeg','png','gif','webp','bmp','svg'].indexOf(ext) !== -1) {" +
            "            return 'img|' + realUrl;" +
            "          }" +
            "          return 'link|' + realUrl + '|' + (cur.innerText || cur.textContent || '');" +
            "        }" +
            "      }" +
            "      // If href itself is a real image and not favicon" +
            "      if (href.startsWith('http') && !href.includes('faviconV2') && !href.includes('favicon')) {" +
            "        var ext2 = href.split('.').pop().split('?')[0].toLowerCase();" +
            "        if (['jpg','jpeg','png','gif','webp','bmp','svg'].indexOf(ext2) !== -1) {" +
            "          return 'img|' + href;" +
            "        }" +
            "        return 'link|' + href + '|' + (cur.innerText || cur.textContent || '');" +
            "      }" +
            "    }" +
            "    // Background image" +
            "    var bg = window.getComputedStyle(cur).backgroundImage;" +
            "    var m = bg && bg.match(/url\\(['\"]?(.*?)['\"]?\\)/);" +
            "    if (m && m[1] && m[1].startsWith('http') && !m[1].includes('faviconV2') && !m[1].includes('favicon')) {" +
            "      return 'img|' + m[1];" +
            "    }" +
            "  }" +
            "  return '';" +
            "};";

        webView.evaluateJavascript(helper + js, resultStr -> {
            String data = unescapeJson(resultStr);
            if (data == null || data.isEmpty()) return;
            String[] parts = data.split("\\|", -1);
            if (parts.length < 2) return;
            String type = parts[0];
            String url = parts[1];
            if (type.equals("link")) {
                String linkText = parts.length > 2 ? parts[2] : "";
                runOnUiThread(() -> showLinkPopup(url, linkText));
            } else if (type.equals("img") || type.equals("media")) {
                runOnUiThread(() -> offerSaveForUrl(url));
            }
        });
    }

    private void checkLongPressForImage() {
        checkLongPressForElement();
    }

    // ---------- Dialog focus helper ----------
    private void requestDialogButtonFocus(AlertDialog dialog) {
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE}) {
            android.widget.Button btn = dialog.getButton(which);
            if (btn != null) {
                btn.setFocusable(true);
                btn.setFocusableInTouchMode(true);
            }
        }
        android.widget.Button preferred = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (preferred == null) preferred = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (preferred == null) preferred = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (preferred != null) {
            final android.widget.Button toFocus = preferred;
            toFocus.post(() -> {
                toFocus.requestFocus();
                toFocus.requestFocusFromTouch();
            });
        }
    }

    // ---------- Key Events ----------
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    backLongPressTriggered = false;
                    backLongPressRunnable = () -> {
                        backLongPressTriggered = true;
                        showTopBar();
                        focusUrlBar();
                    };
                    backLongPressHandler.postDelayed(backLongPressRunnable, 500);
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (backLongPressRunnable != null) {
                    backLongPressHandler.removeCallbacks(backLongPressRunnable);
                }
                if (!backLongPressTriggered) {
                    if (keyboardProxyActive) {
                        hideSystemKeyboard();
                    } else if (customView != null && customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                    } else if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        handleExitRequest();
                    }
                }
                return true;
            }
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            toggleCursorMode();
            return true;
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (webView.canGoBack()) {
                webView.goBack();
            }
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (webView.canGoForward()) {
                webView.goForward();
            }
            return true;
        }

        boolean focusInsideTopBar = topBar != null
                && topBar.getVisibility() == View.VISIBLE
                && topBar.findFocus() != null;

        if (focusInsideTopBar) {
            return super.dispatchKeyEvent(event);
        }

        if (keyboardProxyActive) {
            return super.dispatchKeyEvent(event);
        }

        // Long-press DPAD_CENTER/ENTER
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    centerLongPressTriggered = false;
                    centerLongPressRunnable = () -> {
                        centerLongPressTriggered = true;
                        checkLongPressForImage();
                    };
                    centerLongPressHandler.postDelayed(centerLongPressRunnable, LONG_PRESS_DELAY_MS);
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (centerLongPressRunnable != null) {
                    centerLongPressHandler.removeCallbacks(centerLongPressRunnable);
                }
                if (!centerLongPressTriggered) {
                    if (!cursorModeEnabled) {
                        tabNavActivate();
                    } else {
                        simulateClick(cursorView.getCursorX(), cursorView.getCursorY());
                    }
                }
                return true;
            }
        }

        int keyCode = event.getKeyCode();

        // ---------- Tab Navigation Mode (Cursor OFF) ----------
        if (!cursorModeEnabled) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        tabNavNext();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_UP:
                        tabNavPrev();
                        return true;
                    case KeyEvent.KEYCODE_MENU:
                        showTopBar();
                        showBookmarksMenu();
                        return true;
                    default:
                        return super.dispatchKeyEvent(event);
                }
            }
            return super.dispatchKeyEvent(event);
        }

        // ---------- Cursor Mode (ON) ----------
        boolean isDirectionKey = keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;

        if (isDirectionKey) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (urlBar.hasFocus()) {
                    runOnUiThread(() -> {
                        urlBar.clearFocus();
                        hideKeyboard();
                        webView.requestFocus();
                    });
                }
                if (heldDirectionKeys.isEmpty()) {
                    cursorMoveStartTime = System.currentTimeMillis();
                }
                heldDirectionKeys.add(keyCode);
                startCursorFrameLoopIfNeeded();
                resetCursorHideTimer();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                heldDirectionKeys.remove(keyCode);
                if (heldDirectionKeys.isEmpty()) {
                    lastCursorFrameTimeNanos = 0L;
                }
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MENU:
                    showTopBar();
                    showBookmarksMenu();
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (keyboardProxyActive) {
            hideSystemKeyboard();
            return;
        }

        if (customView != null && customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            return;
        }

        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            handleExitRequest();
        }
    }

    private void handleExitRequest() {
        long now = System.currentTimeMillis();
        if (now - lastExitPromptTime < EXIT_PROMPT_WINDOW_MS) {
            finish();
        } else {
            lastExitPromptTime = now;
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
    }

    private void focusUrlBar() {
        if (keyboardProxyActive) {
            hideSystemKeyboard();
        }
        showTopBar();
        urlBar.requestFocus();
        urlBar.selectAll();
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    // ---------- Click Simulation ----------
    private void simulateClick(float x, float y) {
        int[] cursorLoc = new int[2];
        cursorView.getLocationOnScreen(cursorLoc);
        float screenX = cursorLoc[0] + x;
        float screenY = cursorLoc[1] + y;

        int[] urlBarLoc = new int[2];
        urlBar.getLocationOnScreen(urlBarLoc);
        boolean insideUrlBar = screenX >= urlBarLoc[0]
                && screenX <= urlBarLoc[0] + urlBar.getWidth()
                && screenY >= urlBarLoc[1]
                && screenY <= urlBarLoc[1] + urlBar.getHeight();

        if (insideUrlBar) {
            focusUrlBar();
            return;
        }

        if (topBar instanceof ViewGroup && topBar.getVisibility() == View.VISIBLE) {
            ViewGroup group = (ViewGroup) topBar;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child == null || child == urlBar || child.getVisibility() != View.VISIBLE) {
                    continue;
                }

                int[] childLoc = new int[2];
                child.getLocationOnScreen(childLoc);
                boolean insideChild = screenX >= childLoc[0]
                        && screenX <= childLoc[0] + child.getWidth()
                        && screenY >= childLoc[1]
                        && screenY <= childLoc[1] + child.getHeight();

                if (insideChild) {
                    child.requestFocus();
                    child.performClick();
                    return;
                }
            }
        }

        int[] webViewLoc = new int[2];
        webView.getLocationOnScreen(webViewLoc);
        float localX = screenX - webViewLoc[0];
        float localY = screenY - webViewLoc[1];

        if (localX < 0 || localY < 0 || localX > webView.getWidth() || localY > webView.getHeight()) {
            return;
        }

        webView.evaluateJavascript(
            "(function(){" +
            "  var ratio = window.devicePixelRatio || 1;" +
            "  var cssX = " + localX + " / ratio;" +
            "  var cssY = " + localY + " / ratio;" +
            "  var el = document.elementFromPoint(cssX, cssY);" +
            "  if (el) {" +
            "    var tag = el.tagName;" +
            "    if (tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable) {" +
            "      window.__tvKeyboardTarget = el;" +
            "      return 'editable';" +
            "    }" +
            "  }" +
            "  return 'other';" +
            "})()",
            result -> {
                if (result != null && result.contains("editable")) {
                    webView.evaluateJavascript(
                        "(function(){" +
                        "  if (window.__tvKeyboardTarget) {" +
                        "    window.__tvKeyboardTarget.focus({preventScroll:true});" +
                        "  }" +
                        "})()", null);
                    runOnUiThread(() -> {
                        int scrollBeforeFocus = webView.getScrollY();
                        webView.requestFocus();
                        webView.post(() -> webView.scrollTo(webView.getScrollX(), scrollBeforeFocus));
                        showSystemKeyboard();
                    });
                } else {
                    runOnUiThread(() -> dispatchNativeTap(localX, localY));
                }
            }
        );
    }

    @Override
    protected void onDestroy() {
        if (privateBrowsingEnabled) {
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            android.webkit.CookieManager.getInstance().setAcceptCookie(true);
            for (TabData tab : tabs) {
                if (tab.view != null) {
                    tab.view.clearHistory();
                    tab.view.clearCache(true);
                    tab.view.clearFormData();
                }
            }
        }
        super.onDestroy();
    }

    // ---------- Image/Media Save ----------
    private static final java.util.Set<String> SAVEABLE_EXTENSIONS = new java.util.HashSet<>(java.util.Arrays.asList(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg",
        "mp4", "webm", "mkv", "mov", "avi", "3gp", "m4v",
        "mp3", "wav", "ogg", "m4a", "flac", "aac",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "zip", "rar", "7z", "apk", "txt"
    ));

    private boolean hasSaveableExtension(String url) {
        if (url == null) return false;
        try {
            String path = Uri.parse(url).getLastPathSegment();
            if (path == null || !path.contains(".")) return false;
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
            int q = ext.indexOf('?');
            if (q >= 0) ext = ext.substring(0, q);
            return SAVEABLE_EXTENSIONS.contains(ext);
        } catch (Exception e) {
            return false;
        }
    }

    private void offerSaveForUrl(String url) {
        String guessedMime = "*/*";
        String lower = url.toLowerCase();
        if (lower.matches(".*\\.(png|jpe?g|gif|webp|bmp|svg)(\\?.*)?$")) guessedMime = "image/*";
        else if (lower.matches(".*\\.(mp4|webm|mkv|mov|avi|3gp|m4v)(\\?.*)?$")) guessedMime = "video/*";
        else if (lower.matches(".*\\.(mp3|wav|ogg|m4a|flac|aac)(\\?.*)?$")) guessedMime = "audio/*";
        else if (lower.matches(".*\\.pdf(\\?.*)?$")) guessedMime = "application/pdf";

        Toast.makeText(this, "Save file?", Toast.LENGTH_SHORT).show();
        promptDownloadConfirmation(url, null,
                "attachment; filename=\"" + extractFileName(url, null, null) + "\"",
                guessedMime);
    }

    private void dispatchNativeTap(float localX, float localY) {
        long downTime = android.os.SystemClock.uptimeMillis();
        long eventTime = downTime;

        MotionEvent down = MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_DOWN, localX, localY, 0);
        webView.dispatchTouchEvent(down);
        down.recycle();

        long upTime = android.os.SystemClock.uptimeMillis();
        MotionEvent up = MotionEvent.obtain(
                downTime, upTime, MotionEvent.ACTION_UP, localX, localY, 0);
        webView.dispatchTouchEvent(up);
        up.recycle();
    }

    // ---------- SimpleAdBlockManager (inner class) ----------
    private static class SimpleAdBlockManager {
        private final Context context;
        private final Set<String> blockedDomains = new HashSet<>();
        private final Set<String> exceptionDomains = new HashSet<>();
        private final Set<String> filterUrls = new HashSet<>();
        private final Set<String> whitelist = new HashSet<>();
        private boolean enabled = true;
        private static final String PREFS_NAME = "adblock_prefs";
        private static final String KEY_FILTER_URLS = "filter_urls";
        private static final String KEY_WHITELIST = "whitelist";
        private static final String KEY_ENABLED = "adblock_enabled";
        private static final String KEY_INITIALIZED = "adblock_initialized";

        public SimpleAdBlockManager(Context context) {
            this.context = context.getApplicationContext();
            loadFilterUrlsFromPrefs();
            loadWhitelistFromPrefs();
            loadEnabledFromPrefs();
            if (!prefs().contains(KEY_INITIALIZED)) {
                filterUrls.add("https://easylist.to/easylist/easylist.txt");
                prefs().edit().putBoolean(KEY_INITIALIZED, true).apply();
                saveFilterUrlsToPrefs();
            }
        }

        private SharedPreferences prefs() {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        private void loadFilterUrlsFromPrefs() {
            Set<String> saved = prefs().getStringSet(KEY_FILTER_URLS, new HashSet<>());
            filterUrls.clear();
            filterUrls.addAll(saved);
        }

        private void saveFilterUrlsToPrefs() {
            prefs().edit().putStringSet(KEY_FILTER_URLS, filterUrls).apply();
        }

        private void loadWhitelistFromPrefs() {
            Set<String> saved = prefs().getStringSet(KEY_WHITELIST, new HashSet<>());
            whitelist.clear();
            whitelist.addAll(saved);
        }

        private void saveWhitelistToPrefs() {
            prefs().edit().putStringSet(KEY_WHITELIST, whitelist).apply();
        }

        private void loadEnabledFromPrefs() {
            enabled = prefs().getBoolean(KEY_ENABLED, true);
        }

        private void saveEnabledToPrefs() {
            prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
            saveEnabledToPrefs();
        }

        public Set<String> getFilterUrls() {
            return new HashSet<>(filterUrls);
        }

        public void addFilterUrl(String url) {
            if (url != null && !url.isEmpty()) {
                filterUrls.add(url);
                saveFilterUrlsToPrefs();
            }
        }

        public void removeFilterUrl(String url) {
            filterUrls.remove(url);
            saveFilterUrlsToPrefs();
            loadFilters();
        }

        public boolean isWhitelisted(String host) {
            return whitelist.contains(host);
        }

        public void addWhitelist(String host) {
            whitelist.add(host);
            saveWhitelistToPrefs();
        }

        public void removeWhitelist(String host) {
            whitelist.remove(host);
            saveWhitelistToPrefs();
        }

        public Set<String> getWhitelist() {
            return new HashSet<>(whitelist);
        }

        public void loadFilters() {
            blockedDomains.clear();
            exceptionDomains.clear();
            if (filterUrls.isEmpty()) {
                return;
            }
            for (String url : filterUrls) {
                try {
                    String content = download(url);
                    if (content != null) parseFilterList(content);
                } catch (Exception e) {
                    Log.e("AdBlock", "Failed to load filter: " + url, e);
                }
            }
        }

        private String download(String urlString) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                    }
                    return sb.toString();
                }
            } catch (Exception e) {
                Log.e("AdBlock", "Download failed", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            return null;
        }

        private void parseFilterList(String content) {
            String[] lines = content.split("\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[Adblock")) continue;

                boolean isException = false;
                if (line.startsWith("@@")) {
                    isException = true;
                    line = line.substring(2);
                }

                if (line.startsWith("||")) {
                    int end = line.indexOf("^", 2);
                    if (end > 2) {
                        String domain = line.substring(2, end);
                        if (domain.startsWith(".")) domain = domain.substring(1);
                        if (isException) exceptionDomains.add(domain);
                        else blockedDomains.add(domain);
                    }
                }
            }
        }

        public boolean shouldBlock(String url) {
            if (!enabled) return false;
            if (url == null) return false;
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if (host == null) return false;

                if (whitelist.contains(host)) return false;

                for (String exception : exceptionDomains) {
                    if (host.equals(exception) || host.endsWith("." + exception)) return false;
                }

                for (String blocked : blockedDomains) {
                    if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
                }
            } catch (Exception e) { /* ignore */ }
            return false;
        }
    }
}