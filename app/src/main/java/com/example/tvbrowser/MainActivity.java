package com.example.tvbrowser;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import java.io.File;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.LayoutInflater;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.CustomViewCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    /** Holds one browser tab's WebView instance plus display metadata. */
    private static class TabData {
        CustomWebView view;
        String title = "New Tab";
    }

    private final List<TabData> tabs = new ArrayList<>();
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
    private static final float DEFAULT_CURSOR_SPEED = 40f;
    private static final float DEFAULT_SCROLL_SPEED = 120f;
    private float scrollStep = DEFAULT_SCROLL_SPEED;
    private boolean privateBrowsingEnabled = false;

    private String pendingJsUrl = null;
    private String pendingJsContent = null;
    private android.content.BroadcastReceiver downloadCompleteReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        backButton.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });
        forwardButton.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });
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

        addNewTab(getHomepage());

        webView.post(() -> {
            cursorView.setCursorPosition(webView.getWidth() / 2f, webView.getHeight() / 2f);
        });
    }

    /**
     * Creates and fully configures a brand new tab (its own WebView
     * instance, settings, WebViewClient/WebChromeClient callbacks, and JS
     * interfaces), adds it to the tab container (initially hidden), and
     * switches to it immediately.
     */
    private void addNewTab(String startUrl) {
        CustomWebView wv = new CustomWebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        wv.setVisibility(View.GONE);
        wv.setFocusable(true);
        wv.setFocusableInTouchMode(true);

        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setDomStorageEnabled(true);
        wv.getSettings().setUseWideViewPort(true);
        wv.getSettings().setLoadWithOverviewMode(true);
        wv.getSettings().setMediaPlaybackRequiresUserGesture(false);
        wv.getSettings().setDatabaseEnabled(true);
        wv.getSettings().setAllowFileAccess(true);
        wv.getSettings().setAllowContentAccess(true);
        wv.getSettings().setMixedContentMode(
            android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        wv.getSettings().setUserAgentString(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        wv.setBackgroundColor(Color.BLACK);
        wv.getSettings().setSupportMultipleWindows(true);
        wv.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        wv.getSettings().setBuiltInZoomControls(true);
        wv.getSettings().setDisplayZoomControls(false);
        wv.getSettings().setSupportZoom(true);

        TabData tab = new TabData();
        tab.view = wv;

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

        wv.setWebViewClient(new WebViewClient() {
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

                injectAllUserscripts();
                injectTabNavigationScript();
                injectScrollWatcher();
                stripTargetBlankLinks();
                if (!cursorModeEnabled) {
                    webView.evaluateJavascript("window.__tvTabNav && window.__tvTabNav.enable();", null);
                }

                if (url != null && url.toLowerCase().endsWith(".js")) {
                    pendingJsUrl = url;
                    view.evaluateJavascript(
                            "document.body ? document.body.innerText : ''",
                            value -> {
                                pendingJsContent = unescapeJson(value);
                                promptInstallScript(url);
                            });
                }
            }
        });

        wv.addJavascriptInterface(new KeyboardBridge(), "AndroidKeyboard");
        wv.addJavascriptInterface(new ChromeBridge(), "AndroidChrome");
        wv.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            promptDownloadConfirmation(url, userAgent, contentDisposition, mimetype);
        });

        tabContainer.addView(wv);
        tabs.add(tab);
        switchToTab(tabs.size() - 1);
        wv.loadUrl(startUrl);
    }

    /** Shows the tab at the given index and hides all others. */
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
    }

    /**
     * Closes the given tab. If it was the active tab, switches to a
     * neighboring tab; if it was the only tab left, opens a fresh one at
     * the homepage so the browser is never left with zero tabs.
     */
    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        TabData closed = tabs.remove(index);
        tabContainer.removeView(closed.view);
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

    /**
     * Shows a simple list of open tabs with each tab's title/url, a
     * close (X) button per row to end that tab, tap-to-switch behavior,
     * and a "+ New Tab" button.
     */
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
                // The row itself is NOT focusable. Only its two children
                // (the select-tab label and the close button) are
                // focusable, so D-pad LEFT/RIGHT can move between them
                // instead of the row swallowing all focus and hiding the
                // close button from the remote entirely.
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

        // Immediately focus the active tab's row (or the first row) once
        // the dialog is shown, so the remote user has a visible starting
        // point instead of no focus indicator at all.
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

    private void tabNavNext() {
        webView.evaluateJavascript(
            "window.__tvTabNav ? window.__tvTabNav.next() : 'inactive';",
            result -> {
                if (result != null && result.contains("bottom")) {
                    webView.evaluateJavascript("window.scrollBy(0, " + scrollStep + ");", null);
                }
            });
    }

    private void tabNavPrev() {
        webView.evaluateJavascript(
            "window.__tvTabNav ? window.__tvTabNav.prev() : 'inactive';",
            result -> {
                if (result != null && result.contains("top")) {
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

    private void injectAllUserscripts() {
        JSONArray arr = loadScripts();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.optBoolean("enabled", true)) {
                    String code = obj.getString("code");
                    webView.evaluateJavascript(code, null);
                }
            } catch (JSONException e) {
                Log.e("TVBrowser", "inject failed", e);
            }
        }
    }

    private void promptInstallScript(String url) {
        if (pendingJsContent == null || pendingJsContent.isEmpty()) return;
        String defaultName = url.substring(url.lastIndexOf('/') + 1);

        new AlertDialog.Builder(this)
                .setTitle("Install userscript?")
                .setMessage(url)
                .setPositiveButton("Install", (dialog, which) -> {
                    addScript(defaultName, url, pendingJsContent);
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

    private String getHomepage() {
        return prefs().getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE);
    }

    private void setHomepage(String url) {
        prefs().edit().putString(KEY_HOMEPAGE, url).apply();
    }

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

        /**
     * Uses Android's system DownloadManager to fetch the file into a
     * dedicated PanGalacticMonkey folder under the public Downloads
     * directory, and records it in SharedPreferences so it can be
     * managed later (install / open, delete, or dismiss) from the
     * Downloads screen.
     */
    /**
     * Asks the user for confirmation before downloading anything,
     * showing the file name so they know what they are about to save.
     */
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
     * Determines the real file name for a download, preferring the
     * actual extension present in the URL itself (e.g. ".apk", ".pdf")
     * over Android's URLUtil.guessFileName, which frequently mislabels
     * files as generic ".bin" when the server's Content-Type header is
     * missing or set to application/octet-stream.
     */
    private String extractFileName(String url, String contentDisposition, String mimetype) {
        try {
            if (contentDisposition != null) {
                String cd = contentDisposition;
                int fnIdx = cd.toLowerCase().indexOf("filename=");
                if (fnIdx >= 0) {
                    String name = cd.substring(fnIdx + 9).replace("\"", "").trim();
                    int semi = name.indexOf(';');
                    if (semi >= 0) name = name.substring(0, semi).trim();
                    if (!name.isEmpty()) return name;
                }
            }

            Uri uri = Uri.parse(url);
            String lastSegment = uri.getLastPathSegment();
            if (lastSegment != null && lastSegment.contains(".") && lastSegment.length() > 1) {
                return lastSegment;
            }

            return android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype);
        } catch (Exception e) {
            return "downloaded_file";
        }
    }

    /**
     * Returns the list of available download base locations: Internal
     * Storage (the standard public Downloads folder) plus any detected
     * SD card / secondary storage volume, using Android's own
     * getExternalFilesDirs() to discover real removable storage paths
     * rather than guessing folder names.
     */
    /**
     * IMPORTANT: getExternalFilesDirs() (previously used here to detect
     * SD card paths) returns app-PRIVATE directories under
     * Android/data/<package>/files on each storage volume. Those
     * directories are sandboxed per-app on Android 10+ and cannot be
     * read by other apps (MX Player, Xplore, etc.) under any
     * circumstances -- writing downloads there is exactly what caused
     * the "Open failed: permission denied" / EACCES errors, because the
     * file itself was never actually shared storage in the first place.
     * Only the public Downloads directory (and folders inside it) are
     * guaranteed to be readable by other installed apps, so that's the
     * only root offered now.
     */
    private java.util.List<File> getAvailableStorageRoots() {
        java.util.List<File> roots = new java.util.ArrayList<>();
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        return roots;
    }

    private File getDownloadBaseDir() {
        String saved = prefs().getString(KEY_DOWNLOAD_DIR, null);
        if (saved != null) {
            // Safety net: if an earlier version of the app saved a
            // sandboxed Android/data/... app-private path (from the old
            // getExternalFilesDirs()-based SD card detection), silently
            // fall back to public storage and clear the bad setting so
            // future downloads are always readable by other apps.
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

    /**
     * Lets the user choose where new downloads are saved: the default
     * internal Downloads folder, a detected SD card / secondary storage
     * volume (if present on the device), or a custom folder name typed
     * in relative to internal storage.
     */
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

    /**
     * Polls the system DownloadManager in the background until the given
     * download finishes (success or failure), then shows a follow-up
     * dialog with the full file path and Install, Delete, and Done
     * actions -- exactly like the main Downloads manager, but surfaced
     * immediately for the file the user just downloaded.
     */
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

    /**
     * When DownloadManager writes to a custom destination via
     * setDestinationUri() (used for our SD card / custom folder
     * support), the resulting file is sometimes created as
     * owner-only-readable (mode 600), which is why external apps like
     * MX Player or Xplore report EACCES / "cannot play this link" even
     * though the file downloaded successfully. This explicitly marks
     * the file world-readable and triggers a media scan so external
     * players and file managers can actually open it.
     */
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

    /**
     * The post-download confirmation dialog: shows the full file path
     * and offers Install, Delete, and Done buttons.
     */
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

    /**
     * Checks the real Android DownloadManager status for every tracked
     * download and refreshes each record's "status" field
     * (downloading / complete / failed) before the Downloads dialog is
     * displayed, so users always see up-to-date progress state.
     */
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

    /**
     * Shows every tracked download with its full file path, current
     * status, and Install/Open, Delete, and Done (dismiss from list)
     * actions.
     */
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

    /**
     * Opens a downloaded file with the appropriate system handler: APKs
     * launch the package installer (prompting the user to allow
     * install-from-unknown-sources the first time if needed), while
     * everything else (video, images, PDFs, etc.) launches whatever app
     * the user has installed capable of viewing that file type.
     */
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

            // Some third-party players (MX Player, Xplore, etc.) resolve
            // the receiving Activity via queryIntentActivities() and
            // don't automatically inherit the URI grant unless it is
            // explicitly extended to every matching app, which is why
            // EACCES / "cannot play this link" errors happen even
            // though the app declares FLAG_GRANT_READ_URI_PERMISSION.
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

    // ---------------- History ----------------

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

    /**
     * Adds a visited page to the front of the history list, skipping
     * duplicate consecutive entries for the same URL, and trims the
     * list down to the most recent MAX_HISTORY_ITEMS entries.
     */
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

    /**
     * Shows the last visited pages (most recent first) with tap-to-open
     * behavior, a per-item Delete button, and a Clear All button.
     */
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

    // ---------------- Private Browsing ----------------

    /**
     * Toggles private browsing for the rest of the app session. While
     * enabled, no new history entries are recorded and cookies plus all
     * WebView storage are cleared both when entering private mode and
     * again automatically when the app is closed, so nothing persists
     * between private sessions.
     */
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

private void showBookmarksMenu() {
        String[] options = {
            "View Bookmarks",
            "Add Current Page",
            "Change Homepage",
            "Downloads",
            "Change Download Location",
            "History",
            privateBrowsingEnabled ? "Private Browsing: ON" : "Private Browsing: OFF",
            "Zoom In",
            "Zoom Out",
            "Cursor & Scroll Speed"
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
                case 0:
                    showBookmarksList();
                    break;
                case 1:
                    promptAddBookmark();
                    break;
                case 2:
                    promptChangeHomepage();
                    break;
                case 3:
                    showDownloadsManager();
                    break;
                case 4:
                    showChangeDownloadLocation();
                    break;
                case 5:
                    showHistoryManager();
                    break;
                case 6:
                    togglePrivateBrowsing();
                    break;
                case 7:
                    zoomIn();
                    break;
                case 8:
                    zoomOut();
                    break;
                case 9:
                    showSpeedSettings();
                    break;
            }
        });

        dialog.show();
    }

    private void showSpeedSettings() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        android.widget.TextView cursorLabel = new android.widget.TextView(this);
        cursorLabel.setText("Cursor Speed: " + (int) cursorStep);
        layout.addView(cursorLabel);

        android.widget.SeekBar cursorSeek = new android.widget.SeekBar(this);
        cursorSeek.setMax(90);
        cursorSeek.setProgress((int) cursorStep - 10);
        layout.addView(cursorSeek);

        android.widget.TextView scrollLabel = new android.widget.TextView(this);
        scrollLabel.setText("Scroll Speed: " + (int) scrollStep);
        scrollLabel.setPadding(0, 24, 0, 0);
        layout.addView(scrollLabel);

        android.widget.SeekBar scrollSeek = new android.widget.SeekBar(this);
        scrollSeek.setMax(380);
        scrollSeek.setProgress((int) scrollStep - 20);
        layout.addView(scrollSeek);

        cursorSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                cursorLabel.setText("Cursor Speed: " + (progress + 10));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        scrollSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                scrollLabel.setText("Scroll Speed: " + (progress + 20));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Cursor & Scroll Speed")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    cursorStep = cursorSeek.getProgress() + 10;
                    scrollStep = scrollSeek.getProgress() + 20;
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

    private void showScriptsManager() {
        JSONArray arr = loadScripts();
        int count = arr.length();
        if (count == 0) {
            Toast.makeText(this, "No userscripts installed", Toast.LENGTH_SHORT).show();
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

        new AlertDialog.Builder(this)
                .setTitle("Installed userscripts (select to remove)")
                .setItems(names, (dialog, which) -> confirmRemove(which, names[which]))
                .setNegativeButton("Close", null)
                .show();
    }

    private void confirmRemove(int index, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remove script?")
                .setMessage(name)
                .setPositiveButton("Remove", (d, w) -> {
                    removeScript(index);
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

        // Long-press DPAD_CENTER/ENTER acts as a "right click": if the
        // element under the cursor (or currently focused/highlighted
        // element in tab-nav mode) is an image, holding the button down
        // for LONG_PRESS_DELAY_MS prompts the user to save it. A quick
        // tap still performs the normal click/activate behavior.
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

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            if (!cursorModeEnabled) {
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

            float x = cursorView.getCursorX();
            float y = cursorView.getCursorY();

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (y - cursorStep < 0) {
                        webView.evaluateJavascript(
                            "(function(){ return window.scrollY <= 0; })();",
                            result -> {
                                if ("true".equals(result)) {
                                    runOnUiThread(this::focusUrlBar);
                                } else {
                                    webView.evaluateJavascript("window.scrollBy(0, -" + scrollStep + ");", null);
                                }
                            });
                    } else {
                        cursorView.setCursorPosition(x, y - cursorStep);
                    }
                    return true;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                    int usableHeight = cursorView.getHeight();
                    if (y + cursorStep > usableHeight) {
                        webView.evaluateJavascript("window.scrollBy(0, " + scrollStep + ");", null);
                    } else {
                        cursorView.setCursorPosition(x, y + cursorStep);
                    }
                    return true;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    cursorView.setCursorPosition(Math.max(0, x - cursorStep), y);
                    return true;

                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    cursorView.setCursorPosition(Math.min(cursorView.getWidth(), x + cursorStep), y);
                    return true;

                case KeyEvent.KEYCODE_MENU:
                    showTopBar();
                    showBookmarksMenu();
                    return true;

                default:
                    break;
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
        // If private browsing was active, make sure cookies, cache, and
        // form data are wiped when the app closes so nothing carries
        // over into the next session. Every new launch already starts
        // fresh at the homepage via onCreate(), so this guarantees a
        // private session leaves no trace once the app is exited.
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

    /**
     * Checks whether the element currently under the on-screen cursor
     * (or, in tab-nav/no-cursor mode, the currently highlighted/focused
     * element) is an image, and if so, resolves its full image URL and
     * routes it through the same download-confirmation flow used for
     * regular file downloads -- effectively a "right click > save
     * image" for the remote control.
     */
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

    /**
     * Checks whether the element under the cursor (or, in tab-nav/no
     * cursor mode, the currently focused/highlighted element) is a
     * saveable media file -- image, video, audio, or a link pointing
     * directly to one (pdf, zip, apk, doc, etc.) -- and if so routes it
     * through the download-confirmation flow. Also handles the case
     * where the current page itself IS the media file, which happens
     * when the browser navigates directly to a raw file URL like a
     * .mp4 or .png link with nothing else on the page.
     */
    private void checkLongPressForImage() {
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
                "  return el ? __tvResolveMediaUrl(el) : '';" +
                "})()";
        } else {
            js = "(function(){" +
                "  var el = document.activeElement;" +
                "  return el ? __tvResolveMediaUrl(el) : '';" +
                "})()";
        }

        String helper =
            "window.__tvResolveMediaUrl = window.__tvResolveMediaUrl || function(el){" +
            "  var cur = el;" +
            "  for (var depth = 0; cur && depth < 4; depth++, cur = cur.parentElement) {" +
            "    if (cur.tagName === 'IMG' && cur.src) return cur.src;" +
            "    if ((cur.tagName === 'VIDEO' || cur.tagName === 'AUDIO')) {" +
            "      if (cur.currentSrc) return cur.currentSrc;" +
            "      if (cur.src) return cur.src;" +
            "      var source = cur.querySelector('source[src]');" +
            "      if (source) return source.src;" +
            "    }" +
            "    if (cur.tagName === 'A' && cur.href) return cur.href;" +
            "    var bg = window.getComputedStyle(cur).backgroundImage;" +
            "    var m = bg && bg.match(/url\\(['\"]?(.*?)['\"]?\\)/);" +
            "    if (m && m[1]) return m[1];" +
            "  }" +
            "  return '';" +
            "};";

        webView.evaluateJavascript(helper + js, result -> {
            String mediaUrl = unescapeJson(result);
            if (mediaUrl == null || mediaUrl.isEmpty()) {
                return;
            }
            runOnUiThread(() -> offerSaveForUrl(mediaUrl));
        });
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
}
