package com.example.tvbrowser;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.PanZoomController;
import org.mozilla.geckoview.ScreenLength;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;
import org.mozilla.geckoview.WebResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_HOMEPAGE = "https://burnsymbol.com/";
    private static final long LONG_PRESS_DELAY_MS = 500L;
    private static final long EXIT_PROMPT_WINDOW_MS = 2000L;
    private static final String JS_BRIDGE_ID = "js-bridge@tvbrowser";
    private static final int MAX_HISTORY_ITEMS = 200;

    private static final String PREFS_NAME = "tvbrowser_prefs";
    private static final String KEY_HOMEPAGE = "homepage";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_DOWNLOADS = "downloads";
    private static final String KEY_BOOKMARKS = "bookmarks";
    private static final String KEY_CURSOR_SPEED = "cursor_speed";
    private static final String KEY_SCROLL_SPEED = "scroll_speed";
    private static final String KEY_JS_ENABLED = "js_enabled";
    private static final String KEY_DESKTOP_MODE = "desktop_mode";

    private static final float DEFAULT_CURSOR_SPEED = 10f;
    private static final float MIN_CURSOR_SPEED = 2f;
    private static final float MAX_CURSOR_SPEED = 30f;

    private static final float DEFAULT_SCROLL_SPEED = 605f;
    private static final float MIN_SCROLL_SPEED = 5f;
    private static final float MAX_SCROLL_SPEED = 100f;

    private static final float DEFAULT_ZOOM = 1.0f;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 3.0f;
    private static final float ZOOM_STEP = 0.1f;

    private static final String[] EXTENSION_URLS = {
        "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi",
        "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
        null
    };

    private static final Set<String> SAVEABLE_EXTENSIONS = new HashSet<>(java.util.Arrays.asList(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg",
        "mp4", "webm", "mkv", "mov", "avi", "3gp", "m4v",
        "mp3", "wav", "ogg", "m4a", "flac", "aac",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "zip", "rar", "7z", "apk", "txt"
    ));

    private static GeckoRuntime sRuntime;
    private GeckoView geckoView;

    private EditText urlBar;
    private ProgressBar progressBar;
    private Button backButton;
    private Button forwardButton;
    private Button goButton;
    private Button homeButton;
    private Button bookmarkButton;
    private Button cursorModeButton;
    private Button extensionsButton;
    private View topBar;
    private CursorView cursorView;

    private final List<WebExtension> installedExtensions = new ArrayList<>();
    private final Map<String, WebExtension.Action> extensionActions = new HashMap<>();

    private boolean canGoBack = false;
    private boolean canGoForward = false;
    private long lastExitPromptTime = 0L;

    private boolean cursorModeEnabled = true;
    private boolean topBarHasKeyFocus = false;
    private boolean topBarVisible = true;
    private boolean privateBrowsingEnabled = false;

    private float cursorSpeed = DEFAULT_CURSOR_SPEED;
    private float scrollSpeed = DEFAULT_SCROLL_SPEED;
    private boolean javascriptEnabled = true;
    private boolean desktopModeEnabled = false;

    private static class TabData {
        GeckoSession session;
        String title = "";
        String url = "";
        boolean isPrivate = false;
        float zoomLevel = DEFAULT_ZOOM;
    }

    private final List<TabData> tabs = new ArrayList<>();
    private int activeTabIndex = -1;

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private class CursorController {
        static final float CURSOR_MAX_MULTIPLIER = 3.0f;
        static final long CURSOR_RAMP_DURATION_MS = 600L;
        static final float EDGE_SCROLL_THRESHOLD = 2f;
        static final long SCROLL_DISPATCH_INTERVAL_MS = 40L;

        final GeckoView targetGeckoView;
        final CursorView targetCursorView;
        GeckoSession targetSession;
        boolean localCursorModeEnabled;
        boolean isPopup = false;

        View topBarView = null;
        Runnable onReachTop = null;
        final List<View> extraHitTargets = new ArrayList<>();

        final Set<Integer> heldDirectionKeys = new HashSet<>();
        long cursorMoveStartTime = 0L;
        long lastCursorFrameTimeNanos = 0L;
        boolean cursorFrameLoopRunning = false;

        float pendingScrollDeltaY = 0f;
        long lastScrollDispatchTime = 0L;

        final Handler centerLongPressHandler = new Handler(Looper.getMainLooper());
        Runnable centerLongPressRunnable;
        boolean centerLongPressTriggered = false;

        final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (heldDirectionKeys.isEmpty() || !localCursorModeEnabled) {
                    cursorFrameLoopRunning = false;
                    lastCursorFrameTimeNanos = 0L;
                    return;
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
                float speed = cursorSpeed * (1f + ramp * (CURSOR_MAX_MULTIPLIER - 1f)) * deltaMultiplier;
                float edgeScrollAmount = scrollSpeed * deltaMultiplier;

                float x = targetCursorView.getCursorX();
                float y = targetCursorView.getCursorY();
                int viewWidth = targetCursorView.getWidth();
                int viewHeight = targetCursorView.getHeight();

                if (heldDirectionKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) {
                    if (y <= EDGE_SCROLL_THRESHOLD) {
                        pendingScrollDeltaY -= edgeScrollAmount;
                    } else {
                        y = Math.max(0, y - speed);
                    }
                }
                if (heldDirectionKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) {
                    if (y >= viewHeight - EDGE_SCROLL_THRESHOLD) {
                        pendingScrollDeltaY += edgeScrollAmount;
                    } else {
                        y = Math.min(viewHeight, y + speed);
                    }
                }
                if (heldDirectionKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) x = Math.max(0, x - speed);
                if (heldDirectionKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) x = Math.min(viewWidth, x + speed);

                long nowMs = System.currentTimeMillis();
                if (pendingScrollDeltaY != 0f && nowMs - lastScrollDispatchTime >= SCROLL_DISPATCH_INTERVAL_MS) {
                    scrollPage(0, pendingScrollDeltaY);
                    pendingScrollDeltaY = 0f;
                    lastScrollDispatchTime = nowMs;
                }

                targetCursorView.setCursorPositionFast(x, y);
                Choreographer.getInstance().postFrameCallback(this);
            }
        };

        CursorController(GeckoView geckoView, CursorView cursorView, GeckoSession session, boolean startEnabled) {
            this.targetGeckoView = geckoView;
            this.targetCursorView = cursorView;
            this.targetSession = session;
            this.localCursorModeEnabled = startEnabled;
        }

        void setSession(GeckoSession session) {
            this.targetSession = session;
        }

        void startFrameLoopIfNeeded() {
            if (!cursorFrameLoopRunning) {
                cursorFrameLoopRunning = true;
                cursorMoveStartTime = System.currentTimeMillis();
                lastCursorFrameTimeNanos = 0L;
                pendingScrollDeltaY = 0f;
                lastScrollDispatchTime = 0L;
                Choreographer.getInstance().postFrameCallback(frameCallback);
            }
        }

        void stopFrameLoop() {
            heldDirectionKeys.clear();
            cursorFrameLoopRunning = false;
            lastCursorFrameTimeNanos = 0L;
            pendingScrollDeltaY = 0f;
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        }

        void toggleCursorMode() {
            localCursorModeEnabled = !localCursorModeEnabled;
            targetCursorView.setVisibility(localCursorModeEnabled ? View.VISIBLE : View.GONE);
            if (localCursorModeEnabled) {
                GeckoJSBridge.evaluateJavascriptNoResult(targetSession, "window.__tvTabNav && window.__tvTabNav.disable();");
                stopFrameLoop();
            } else {
                stopFrameLoop();
                GeckoJSBridge.evaluateJavascriptNoResult(targetSession, "window.__tvTabNav && window.__tvTabNav.enable();");
            }
            targetGeckoView.requestFocus();
        }

        void scrollPage(float deltaX, float deltaY) {
            if (targetSession == null) return;
            targetSession.getPanZoomController().scrollBy(
                    ScreenLength.fromPixels(deltaX),
                    ScreenLength.fromPixels(deltaY));
            if (!isPopup) {
                if (deltaY > 0) {
                    runOnUiThread(MainActivity.this::hideTopBar);
                } else if (deltaY < 0) {
                    runOnUiThread(MainActivity.this::showTopBar);
                }
            }
        }

        void tabNavNext() {
            GeckoJSBridge.evaluateJavascript(targetSession, "window.__tvTabNav ? window.__tvTabNav.next() : 'inactive';")
                    .then(result -> {
                        if ("bottom".equals(result) || "empty".equals(result)) {
                            scrollPage(0, 300);
                        }
                        return null;
                    });
        }

        void tabNavPrev() {
            GeckoJSBridge.evaluateJavascript(targetSession, "window.__tvTabNav ? window.__tvTabNav.prev() : 'inactive';")
                    .then(result -> {
                        if ("top".equals(result) && onReachTop != null) {
                            runOnUiThread(onReachTop);
                        } else if ("empty".equals(result)) {
                            scrollPage(0, -300);
                        }
                        return null;
                    });
        }

        void tabNavActivate() {
            GeckoJSBridge.evaluateJavascriptNoResult(targetSession, "window.__tvTabNav && window.__tvTabNav.activate();");
        }

        void simulateClick(float cursorX, float cursorY) {
            int[] cursorLoc = new int[2];
            targetCursorView.getLocationOnScreen(cursorLoc);
            float screenX = cursorLoc[0] + cursorX;
            float screenY = cursorLoc[1] + cursorY;

            if (topBarView instanceof ViewGroup && topBarView.getVisibility() == View.VISIBLE) {
                if (tryClickChildrenOf((ViewGroup) topBarView, screenX, screenY)) return;
            }

            for (View extra : extraHitTargets) {
                if (extra == null || extra.getVisibility() != View.VISIBLE) continue;
                int[] loc = new int[2];
                extra.getLocationOnScreen(loc);
                boolean inside = screenX >= loc[0] && screenX <= loc[0] + extra.getWidth()
                        && screenY >= loc[1] && screenY <= loc[1] + extra.getHeight();
                if (inside) {
                    extra.requestFocus();
                    extra.performClick();
                    return;
                }
            }

            int[] geckoLoc = new int[2];
            targetGeckoView.getLocationOnScreen(geckoLoc);
            float localX = screenX - geckoLoc[0];
            float localY = screenY - geckoLoc[1];

            if (localX < 0 || localY < 0 || localX > targetGeckoView.getWidth() || localY > targetGeckoView.getHeight()) {
                return;
            }

            long downTime = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, localX, localY, 0);
            MotionEvent up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, localX, localY, 0);
            targetGeckoView.dispatchTouchEvent(down);
            targetGeckoView.dispatchTouchEvent(up);
            down.recycle();
            up.recycle();
        }

        private boolean tryClickChildrenOf(ViewGroup group, float screenX, float screenY) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child == null || child.getVisibility() != View.VISIBLE) continue;

                int[] loc = new int[2];
                child.getLocationOnScreen(loc);
                boolean inside = screenX >= loc[0] && screenX <= loc[0] + child.getWidth()
                        && screenY >= loc[1] && screenY <= loc[1] + child.getHeight();

                if (inside) {
                    if (child == urlBar) {
                        focusUrlBar();
                    } else {
                        child.requestFocus();
                        child.performClick();
                    }
                    return true;
                }
            }
            return false;
        }

        void checkLongPressForElement() {
            String currentUrl = (!isPopup && !tabs.isEmpty() && activeTabIndex >= 0) ? tabs.get(activeTabIndex).url : null;
            if (currentUrl != null && hasSaveableExtension(currentUrl)) {
                offerSaveForUrl(currentUrl);
                return;
            }

            String js;
            if (localCursorModeEnabled) {
                int[] cursorLoc = new int[2];
                targetCursorView.getLocationOnScreen(cursorLoc);
                float screenX = cursorLoc[0] + targetCursorView.getCursorX();
                float screenY = cursorLoc[1] + targetCursorView.getCursorY();

                int[] geckoLoc = new int[2];
                targetGeckoView.getLocationOnScreen(geckoLoc);
                float localX = screenX - geckoLoc[0];
                float localY = screenY - geckoLoc[1];

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

            String helper =
                "window.__tvResolveElement = window.__tvResolveElement || function(el){" +
                "  var cur = el;" +
                "  for (var depth = 0; cur && depth < 4; depth++, cur = cur.parentElement) {" +
                "    if (cur.tagName === 'IMG' && cur.src) return 'img|' + cur.src;" +
                "    if ((cur.tagName === 'VIDEO' || cur.tagName === 'AUDIO')) {" +
                "      var src = cur.currentSrc || cur.src || (cur.querySelector('source[src]') ? cur.querySelector('source[src]').src : '');" +
                "      if (src) return 'media|' + src;" +
                "    }" +
                "    if (cur.tagName === 'A' && cur.href) return 'link|' + cur.href + '|' + (cur.innerText || cur.textContent || '');" +
                "    var bg = window.getComputedStyle(cur).backgroundImage;" +
                "    var m = bg && bg.match(/url\\(['\"]?(.*?)['\"]?\\)/);" +
                "    if (m && m[1]) return 'img|' + m[1];" +
                "  }" +
                "  return '';" +
                "};";

            GeckoJSBridge.evaluateJavascript(targetSession, helper + js).then(result -> {
                if (result == null) return null;
                String data = result.toString();
                if (data.isEmpty()) return null;
                String[] parts = data.split("\\|", -1);
                if (parts.length < 2) return null;
                String type = parts[0];
                String url = parts[1];
                if (type.equals("link")) {
                    String linkText = parts.length > 2 ? parts[2] : "";
                    runOnUiThread(() -> showLinkPopup(url, linkText));
                } else if (type.equals("img") || type.equals("media")) {
                    runOnUiThread(() -> offerSaveForUrl(url));
                }
                return null;
            });
        }

        boolean handleKeyEvent(KeyEvent event) {
            int keyCode = event.getKeyCode();

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        centerLongPressTriggered = false;
                        centerLongPressRunnable = () -> {
                            centerLongPressTriggered = true;
                            checkLongPressForElement();
                        };
                        centerLongPressHandler.postDelayed(centerLongPressRunnable, LONG_PRESS_DELAY_MS);
                    }
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (centerLongPressRunnable != null) {
                        centerLongPressHandler.removeCallbacks(centerLongPressRunnable);
                    }
                    if (!centerLongPressTriggered) {
                        if (localCursorModeEnabled) {
                            simulateClick(targetCursorView.getCursorX(), targetCursorView.getCursorY());
                        } else {
                            tabNavActivate();
                        }
                    }
                    return true;
                }
            }

            boolean isDirectionKey = keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;

            if (!localCursorModeEnabled) {
                if (isDirectionKey && event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            tabNavNext();
                            return true;
                        case KeyEvent.KEYCODE_DPAD_UP:
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            tabNavPrev();
                            return true;
                    }
                }
                return false;
            }

            if (isDirectionKey) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (heldDirectionKeys.isEmpty()) cursorMoveStartTime = System.currentTimeMillis();
                    heldDirectionKeys.add(keyCode);
                    startFrameLoopIfNeeded();
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    heldDirectionKeys.remove(keyCode);
                    if (heldDirectionKeys.isEmpty()) stopFrameLoop();
                }
                return true;
            }

            return false;
        }
    }

    private CursorController mainCursorController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        geckoView = findViewById(R.id.geckoView);
        urlBar = findViewById(R.id.urlBar);
        progressBar = findViewById(R.id.progressBar);
        backButton = findViewById(R.id.backButton);
        forwardButton = findViewById(R.id.forwardButton);
        goButton = findViewById(R.id.goButton);
        homeButton = findViewById(R.id.homeButton);
        bookmarkButton = findViewById(R.id.bookmarkButton);
        cursorModeButton = findViewById(R.id.cursorModeButton);
        extensionsButton = findViewById(R.id.extensionsButton);
        topBar = findViewById(R.id.topBar);
        cursorView = findViewById(R.id.cursorView);

        cursorSpeed = prefs().getFloat(KEY_CURSOR_SPEED, DEFAULT_CURSOR_SPEED);
        scrollSpeed = prefs().getFloat(KEY_SCROLL_SPEED, DEFAULT_SCROLL_SPEED);
        javascriptEnabled = prefs().getBoolean(KEY_JS_ENABLED, true);
        desktopModeEnabled = prefs().getBoolean(KEY_DESKTOP_MODE, false);

        setupRuntime();
        addNewTab(getHomepage(), false);
        setupUiListeners();
        updateCursorModeUi();

        geckoView.post(() ->
                cursorView.setCursorPosition(geckoView.getWidth() / 2f, geckoView.getHeight() / 2f));
    }

    private void setupRuntime() {
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(this);
            GeckoJSBridge.register(sRuntime, this);

            sRuntime.getWebExtensionController().setPromptDelegate(
                new WebExtensionController.PromptDelegate() {
                    @Override
                    public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(
                            WebExtension extension,
                            String[] permissions,
                            String[] origins,
                            String[] dataCollectionPermissions) {
                        return GeckoResult.fromValue(
                                new WebExtension.PermissionPromptResponse(true, true, false));
                    }
                }
            );
        }
        loadInstalledExtensions();
    }

    // ---------- Top Bar Visibility ----------

    private void showTopBar() {
        if (topBarVisible) return;
        topBarVisible = true;
        topBar.setVisibility(View.VISIBLE);
        topBar.setAlpha(0f);
        topBar.animate().alpha(1f).setDuration(200).start();
    }

    private void hideTopBar() {
        if (!topBarVisible || topBarHasKeyFocus) return;
        topBarVisible = false;
        topBar.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> topBar.setVisibility(View.GONE))
                .start();
    }

    // ---------- Tabs ----------

    private void addNewTab(String url, boolean isPrivate) {
        TabData tab = new TabData();
        tab.isPrivate = isPrivate;
        tab.url = url;

        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                .usePrivateMode(isPrivate)
                .allowJavascript(javascriptEnabled)
                .userAgentMode(desktopModeEnabled
                        ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                        : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .build();
        GeckoSession newSession = new GeckoSession(settings);
        tab.session = newSession;

        GeckoJSBridge.attachSession(newSession);
        attachDelegatesToTab(tab);

        newSession.open(sRuntime);
        tabs.add(tab);
        switchToTab(tabs.size() - 1);
        newSession.loadUri(url);
    }

    private void attachDelegatesToTab(TabData tab) {
        tab.session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(
                    GeckoSession session,
                    String url,
                    java.util.List<GeckoSession.PermissionDelegate.ContentPermission> perms,
                    Boolean hasUserGesture) {
                tab.url = url;
                if (isActiveTab(tab)) {
                    runOnUiThread(() -> {
                        urlBar.setText(url);
                        updateBookmarkStarIcon();
                    });
                }
                if (!tab.isPrivate) {
                    recordHistory(tab.title, url);
                }
            }

            @Override
            public void onCanGoBack(GeckoSession session, boolean value) {
                if (isActiveTab(tab)) canGoBack = value;
            }

            @Override
            public void onCanGoForward(GeckoSession session, boolean value) {
                if (isActiveTab(tab)) canGoForward = value;
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, LoadRequest request) {
                String url = request.uri;
                if (url != null && url.toLowerCase().endsWith(".xpi")) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Install Extension?")
                                .setMessage(url)
                                .setPositiveButton("Install", (d, w) -> installExtension(url))
                                .setNegativeButton("Cancel", null)
                                .show();
                    });
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
                session.loadUri(uri);
                return GeckoResult.fromValue(session);
            }
        });

        tab.session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                if (isActiveTab(tab)) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(0);
                    });
                }
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                if (isActiveTab(tab)) {
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                }
                injectTabNavScript(session);
                injectZoomLock(session);
                applyZoom(tab);
                if (isActiveTab(tab) && mainCursorController != null && !mainCursorController.localCursorModeEnabled) {
                    GeckoJSBridge.evaluateJavascriptNoResult(session,
                            "window.__tvTabNav && window.__tvTabNav.enable();");
                }
            }

            @Override
            public void onProgressChange(GeckoSession session, int progress) {
                if (isActiveTab(tab)) {
                    runOnUiThread(() -> progressBar.setProgress(progress));
                }
            }
        });

        tab.session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
                tab.title = title != null ? title : tab.url;
            }

            @Override
            public void onExternalResponse(GeckoSession session, WebResponse response) {
                runOnUiThread(() -> promptDownloadConfirmation(response));
            }
        });
    }

    private boolean isActiveTab(TabData tab) {
        return activeTabIndex >= 0 && activeTabIndex < tabs.size() && tabs.get(activeTabIndex) == tab;
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        showTopBar();

        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            sRuntime.getWebExtensionController().setTabActive(tabs.get(activeTabIndex).session, false);
        }

        activeTabIndex = index;
        TabData tab = tabs.get(index);
        geckoView.setSession(tab.session);
        sRuntime.getWebExtensionController().setTabActive(tab.session, true);
        urlBar.setText(tab.url);
        updateBookmarkStarIcon();

        if (mainCursorController == null) {
            mainCursorController = new CursorController(geckoView, cursorView, tab.session, cursorModeEnabled);
            mainCursorController.topBarView = topBar;
            mainCursorController.onReachTop = () -> {
                showTopBar();
                hideKeyboard();
                topBarHasKeyFocus = true;
                backButton.requestFocus();
            };
        } else {
            mainCursorController.setSession(tab.session);
        }

        updateTabsButtonLabel();
        geckoView.requestFocus();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        TabData closed = tabs.remove(index);
        closed.session.close();

        if (tabs.isEmpty()) {
            addNewTab(getHomepage(), false);
            return;
        }

        int newIndex = Math.min(index, tabs.size() - 1);
        if (index == activeTabIndex || activeTabIndex >= tabs.size()) {
            activeTabIndex = -1;
            switchToTab(newIndex);
        } else if (index < activeTabIndex) {
            activeTabIndex--;
        }
        updateTabsButtonLabel();
    }

    private void updateTabsButtonLabel() {
        // Reflected in the browser menu dialog title instead of a dedicated button.
    }

    private void showTabsSwitcher() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 16, 16, 16);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        View[] firstRowHolder = new View[1];

        LinearLayout listHolder = new LinearLayout(this);
        listHolder.setOrientation(LinearLayout.VERTICAL);
        container.addView(listHolder);

        Runnable[] rebuildHolder = new Runnable[1];
        Runnable rebuild = () -> {
            listHolder.removeAllViews();
            firstRowHolder[0] = null;
            for (int i = 0; i < tabs.size(); i++) {
                final int idx = i;
                TabData tab = tabs.get(i);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(12, 12, 12, 12);
                final int normalBg = i == activeTabIndex ? Color.parseColor("#333333") : Color.TRANSPARENT;
                row.setBackgroundColor(normalBg);

                TextView label = new TextView(this);
                String title = (tab.title != null && !tab.title.isEmpty() ? tab.title : "New Tab")
                        + (tab.isPrivate ? " (Private)" : "");
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
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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
            addNewTab(getHomepage(), privateBrowsingEnabled);
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
        });
        container.addView(newTabBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        dialogHolder[0] = new AlertDialog.Builder(this)
                .setTitle("Tabs (" + tabs.size() + ")")
                .setView(container)
                .setNegativeButton("Close", null)
                .create();
        dialogHolder[0].show();

        if (firstRowHolder[0] != null) {
            firstRowHolder[0].requestFocus();
        }
    }

    private void injectTabNavScript(GeckoSession session) {
        String js = "(function(){" +
            "if (window.__tvTabNav) { return; }" +
            "var items=[]; var idx=-1; var active=false;" +
            "var HIGHLIGHT='3px solid #3DFF71';" +
            "function collect(){ items=Array.prototype.slice.call(document.querySelectorAll(" +
            "'a, button, input, textarea, select, [role=button], [onclick], [tabindex]'" +
            ")).filter(function(el){ var r=el.getBoundingClientRect(); " +
            "return r.width>0 && r.height>0; }); }" +
            "function clearHighlight(){ if (idx>=0 && items[idx]) items[idx].style.outline=''; }" +
            "function highlight(i){ clearHighlight(); idx=i; if(items[idx]){ items[idx].style.outline=HIGHLIGHT; " +
            "items[idx].scrollIntoView({block:'center',behavior:'smooth'}); " +
            "try { items[idx].focus({preventScroll:true}); } catch(e){} } }" +
            "window.__tvTabNav={" +
            "enable:function(){ active=true; collect(); if(idx<0 && items.length) highlight(0); }," +
            "disable:function(){ active=false; clearHighlight(); }," +
            "next:function(){ if(!active) return 'inactive'; collect(); if(!items.length) return 'empty'; " +
            "if(idx>=items.length-1) return 'bottom'; highlight(idx+1); return 'ok'; }," +
            "prev:function(){ if(!active) return 'inactive'; collect(); if(!items.length) return 'empty'; " +
            "if(idx<=0) return 'top'; highlight(idx-1); return 'ok'; }," +
            "activate:function(){ if(!active||idx<0||!items[idx]) return 'noop'; " +
            "var el=items[idx]; " +
            "if(el.tagName==='INPUT'||el.tagName==='TEXTAREA'||el.isContentEditable){ el.focus({preventScroll:true}); } " +
            "else { el.click(); } return 'ok'; }" +
            "};" +
            "})();";
        GeckoJSBridge.evaluateJavascriptNoResult(session, js);
    }

    private void injectZoomLock(GeckoSession session) {
        String js = "(function(){" +
            "var meta = document.querySelector('meta[name=viewport]');" +
            "if (!meta) { meta = document.createElement('meta'); meta.name='viewport'; document.head.appendChild(meta); }" +
            "meta.content = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';" +
            "document.documentElement.style.touchAction = 'pan-x pan-y';" +
            "})();";
        GeckoJSBridge.evaluateJavascriptNoResult(session, js);
    }

    private void setupUiListeners() {
        goButton.setOnClickListener(v -> { if (!tabs.isEmpty()) tabs.get(activeTabIndex).session.reload(); });
        homeButton.setOnClickListener(v -> { if (!tabs.isEmpty()) tabs.get(activeTabIndex).session.loadUri(getHomepage()); });
        bookmarkButton.setOnClickListener(v -> toggleCurrentPageBookmark());
        urlBar.setOnEditorActionListener((v, actionId, event) -> { loadFromUrlBar(); return true; });
        backButton.setOnClickListener(v -> { if (canGoBack && !tabs.isEmpty()) tabs.get(activeTabIndex).session.goBack(); });
        forwardButton.setOnClickListener(v -> { if (canGoForward && !tabs.isEmpty()) tabs.get(activeTabIndex).session.goForward(); });
        cursorModeButton.setOnClickListener(v -> toggleCursorMode());
        extensionsButton.setOnClickListener(v -> showBrowserMenu());
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (canGoBack && !tabs.isEmpty()) {
                    tabs.get(activeTabIndex).session.goBack();
                } else {
                    handleExitRequest();
                }
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && event.getAction() == KeyEvent.ACTION_DOWN) {
            toggleCursorMode();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (canGoBack && !tabs.isEmpty()) tabs.get(activeTabIndex).session.goBack();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (canGoForward && !tabs.isEmpty()) tabs.get(activeTabIndex).session.goForward();
            return true;
        }

        if (topBarHasKeyFocus) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                View focused = topBar.findFocus();
                if (focused != null) focused.clearFocus();
                hideKeyboard();
                topBarHasKeyFocus = false;
                geckoView.requestFocus();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (mainCursorController != null && mainCursorController.handleKeyEvent(event)) {
            return true;
        }

        return super.dispatchKeyEvent(event);
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

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    private void focusUrlBar() {
        showTopBar();
        urlBar.requestFocus();
        urlBar.selectAll();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT);
        topBarHasKeyFocus = true;
    }

    private void toggleCursorMode() {
        cursorModeEnabled = !cursorModeEnabled;
        if (mainCursorController != null) mainCursorController.toggleCursorMode();
        updateCursorModeUi();
        topBarHasKeyFocus = false;
        geckoView.requestFocus();
    }

    private void updateCursorModeUi() {
        cursorModeButton.setText(cursorModeEnabled ? "Cursor: ON" : "Cursor: OFF (Tab)");
        cursorView.setVisibility(cursorModeEnabled ? View.VISIBLE : View.GONE);
    }

    // ---------- Browser Menu ----------

    private void showBrowserMenu() {
        String[] items = {
            "Tabs (" + tabs.size() + ")",
            "Bookmarks",
            "History",
            "Downloads",
            "Zoom In",
            "Zoom Out",
            "Reset Zoom",
            "Cursor Speed",
            "Scroll Speed",
            javascriptEnabled ? "JavaScript: ON (tap to disable)" : "JavaScript: OFF (tap to enable)",
            desktopModeEnabled ? "Desktop Site: ON (tap to disable)" : "Desktop Site: OFF (tap to enable)",
            privateBrowsingEnabled ? "Private Browsing: ON (tap to disable)" : "Private Browsing: OFF (tap to enable)",
            "Change Homepage",
            "Extensions",
            "Created by burnSYMBOL.com"
        };
        new AlertDialog.Builder(this)
                .setTitle("Menu")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: showTabsSwitcher(); break;
                        case 1: showBookmarksList(); break;
                        case 2: showHistoryManager(); break;
                        case 3: showDownloadsManager(); break;
                        case 4: zoomIn(); break;
                        case 5: zoomOut(); break;
                        case 6: resetZoom(); break;
                        case 7: showCursorSpeedDialog(); break;
                        case 8: showScrollSpeedDialog(); break;
                        case 9: toggleJavaScript(); break;
                        case 10: toggleDesktopMode(); break;
                        case 11: togglePrivateBrowsing(); break;
                        case 12: promptChangeHomepage(); break;
                        case 13: promptInstallExtension(); break;
                        case 14: openBurnSymbolWebsite(); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openBurnSymbolWebsite() {
        if (tabs.isEmpty()) return;
        tabs.get(activeTabIndex).session.loadUri("https://burnsymbol.com");
    }

    // ---------- JavaScript Toggle ----------

    private void toggleJavaScript() {
        javascriptEnabled = !javascriptEnabled;
        prefs().edit().putBoolean(KEY_JS_ENABLED, javascriptEnabled).apply();
        for (TabData tab : tabs) {
            if (tab.session != null) {
                tab.session.getSettings().setAllowJavascript(javascriptEnabled);
            }
        }
        if (!tabs.isEmpty()) {
            tabs.get(activeTabIndex).session.reload();
        }
        Toast.makeText(this, javascriptEnabled ? "JavaScript: ON" : "JavaScript: OFF", Toast.LENGTH_SHORT).show();
    }

    // ---------- Desktop Site Toggle ----------

    private void toggleDesktopMode() {
        desktopModeEnabled = !desktopModeEnabled;
        prefs().edit().putBoolean(KEY_DESKTOP_MODE, desktopModeEnabled).apply();

        int uaMode = desktopModeEnabled
                ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : GeckoSessionSettings.USER_AGENT_MODE_MOBILE;

        for (TabData tab : tabs) {
            if (tab.session != null) {
                tab.session.getSettings().setUserAgentMode(uaMode);
            }
        }

        if (!tabs.isEmpty()) {
            tabs.get(activeTabIndex).session.reload();
        }

        Toast.makeText(this,
                desktopModeEnabled ? "Requesting desktop site" : "Requesting mobile site",
                Toast.LENGTH_SHORT).show();
    }

    // ---------- Zoom ----------

    private void applyZoom(TabData tab) {
        String js = "document.documentElement.style.zoom = '" + tab.zoomLevel + "';";
        GeckoJSBridge.evaluateJavascriptNoResult(tab.session, js);
    }

    private void zoomIn() {
        if (tabs.isEmpty()) return;
        TabData tab = tabs.get(activeTabIndex);
        tab.zoomLevel = Math.min(MAX_ZOOM, roundZoom(tab.zoomLevel + ZOOM_STEP));
        applyZoom(tab);
        Toast.makeText(this, "Zoom: " + Math.round(tab.zoomLevel * 100) + "%", Toast.LENGTH_SHORT).show();
    }

    private void zoomOut() {
        if (tabs.isEmpty()) return;
        TabData tab = tabs.get(activeTabIndex);
        tab.zoomLevel = Math.max(MIN_ZOOM, roundZoom(tab.zoomLevel - ZOOM_STEP));
        applyZoom(tab);
        Toast.makeText(this, "Zoom: " + Math.round(tab.zoomLevel * 100) + "%", Toast.LENGTH_SHORT).show();
    }

    private void resetZoom() {
        if (tabs.isEmpty()) return;
        TabData tab = tabs.get(activeTabIndex);
        tab.zoomLevel = DEFAULT_ZOOM;
        applyZoom(tab);
        Toast.makeText(this, "Zoom reset to 100%", Toast.LENGTH_SHORT).show();
    }

    private float roundZoom(float value) {
        return Math.round(value * 100f) / 100f;
    }

    // ---------- Cursor Speed ----------

    private void adjustCursorSpeed(float delta) {
        cursorSpeed = Math.max(MIN_CURSOR_SPEED, Math.min(MAX_CURSOR_SPEED, cursorSpeed + delta));
        prefs().edit().putFloat(KEY_CURSOR_SPEED, cursorSpeed).apply();
    }

    private void showCursorSpeedDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 24, 32, 24);

        TextView speedLabel = new TextView(this);
        speedLabel.setTextColor(Color.WHITE);
        speedLabel.setTextSize(18f);
        speedLabel.setGravity(Gravity.CENTER);
        container.addView(speedLabel);

        TextView hint = new TextView(this);
        hint.setText("Use D-Pad Left/Right or the buttons below to adjust");
        hint.setTextColor(Color.parseColor("#AAAAAA"));
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 4, 0, 16);
        container.addView(hint);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button minusBtn = new Button(this);
        minusBtn.setText("-1");
        styleSpeedButton(minusBtn);

        Button plusBtn = new Button(this);
        plusBtn.setText("+1");
        styleSpeedButton(plusBtn);

        Runnable[] updateLabel = new Runnable[1];
        updateLabel[0] = () -> speedLabel.setText("Cursor Speed: " + (int) cursorSpeed);
        updateLabel[0].run();

        minusBtn.setOnClickListener(v -> {
            adjustCursorSpeed(-1f);
            updateLabel[0].run();
        });
        plusBtn.setOnClickListener(v -> {
            adjustCursorSpeed(1f);
            updateLabel[0].run();
        });

        btnRow.addView(minusBtn);
        btnRow.addView(plusBtn);
        container.addView(btnRow);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cursor Speed")
                .setView(container)
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                adjustCursorSpeed(-1f);
                updateLabel[0].run();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                adjustCursorSpeed(1f);
                updateLabel[0].run();
                return true;
            }
            return false;
        });

        dialog.show();
        minusBtn.requestFocus();
    }

    // ---------- Scroll Speed ----------

    private void adjustScrollSpeed(float delta) {
        scrollSpeed = Math.max(MIN_SCROLL_SPEED, Math.min(MAX_SCROLL_SPEED, scrollSpeed + delta));
        prefs().edit().putFloat(KEY_SCROLL_SPEED, scrollSpeed).apply();
    }

    private void showScrollSpeedDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 24, 32, 24);

        TextView speedLabel = new TextView(this);
        speedLabel.setTextColor(Color.WHITE);
        speedLabel.setTextSize(18f);
        speedLabel.setGravity(Gravity.CENTER);
        container.addView(speedLabel);

        TextView hint = new TextView(this);
        hint.setText("Use D-Pad Left/Right or the buttons below to adjust");
        hint.setTextColor(Color.parseColor("#AAAAAA"));
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 4, 0, 16);
        container.addView(hint);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button minusBtn = new Button(this);
        minusBtn.setText("-5");
        styleSpeedButton(minusBtn);

        Button plusBtn = new Button(this);
        plusBtn.setText("+5");
        styleSpeedButton(plusBtn);

        Runnable[] updateLabel = new Runnable[1];
        updateLabel[0] = () -> speedLabel.setText("Scroll Speed: " + (int) scrollSpeed);
        updateLabel[0].run();

        minusBtn.setOnClickListener(v -> {
            adjustScrollSpeed(-5f);
            updateLabel[0].run();
        });
        plusBtn.setOnClickListener(v -> {
            adjustScrollSpeed(5f);
            updateLabel[0].run();
        });

        btnRow.addView(minusBtn);
        btnRow.addView(plusBtn);
        container.addView(btnRow);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Scroll Speed")
                .setView(container)
                .setNegativeButton("Close", null)
                .create();

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                adjustScrollSpeed(-5f);
                updateLabel[0].run();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                adjustScrollSpeed(5f);
                updateLabel[0].run();
                return true;
            }
            return false;
        });

        dialog.show();
        minusBtn.requestFocus();
    }

    private void styleSpeedButton(Button b) {
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor("#555555"));
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            b.setBackgroundColor(Color.parseColor(hasFocus ? "#3DFF71" : "#555555"));
            b.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(8, 0, 8, 0);
        b.setLayoutParams(lp);
    }

    // ---------- Homepage ----------

    private String getHomepage() {
        return prefs().getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE);
    }

    private void setHomepage(String url) {
        prefs().edit().putString(KEY_HOMEPAGE, url).apply();
    }

    private void makeCurrentPageHomepage() {
        if (tabs.isEmpty()) return;
        String currentUrl = tabs.get(activeTabIndex).url;
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "No page loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        setHomepage(currentUrl);
        Toast.makeText(this, "Homepage set to current page", Toast.LENGTH_SHORT).show();
    }

    private void promptChangeHomepage() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://example.com");
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
            if (i != index) newArr.put(arr.opt(i));
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
        if (tabs.isEmpty()) return;
        String currentUrl = tabs.get(activeTabIndex).url;
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "No page loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        int existingIndex = findBookmarkIndexForUrl(currentUrl);
        if (existingIndex >= 0) {
            removeBookmark(existingIndex);
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
        } else {
            promptAddBookmark();
        }
    }

    private void promptAddBookmark() {
        if (tabs.isEmpty()) return;
        TabData tab = tabs.get(activeTabIndex);
        String currentUrl = tab.url;
        if (currentUrl == null || currentUrl.isEmpty()) {
            Toast.makeText(this, "No page loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setText(tab.title != null && !tab.title.isEmpty() ? tab.title : currentUrl);
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
                        if (!tabs.isEmpty()) tabs.get(activeTabIndex).session.loadUri(url);
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

    private void updateBookmarkStarIcon() {
        if (bookmarkButton == null || tabs.isEmpty() || activeTabIndex < 0) return;
        String currentUrl = tabs.get(activeTabIndex).url;
        boolean isBookmarked = findBookmarkIndexForUrl(currentUrl) >= 0;
        bookmarkButton.setText(isBookmarked ? "\u2605" : "\u2606");
    }

    // ---------- Private Browsing ----------

    private void togglePrivateBrowsing() {
        privateBrowsingEnabled = !privateBrowsingEnabled;
        Toast.makeText(this,
                privateBrowsingEnabled
                        ? "Private browsing ON — new tabs won't be saved to history"
                        : "Private browsing OFF",
                Toast.LENGTH_LONG).show();
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
            if (i != index) newArr.put(arr.opt(i));
        }
        saveHistory(newArr);
    }

    private void showHistoryManager() {
        JSONArray arr = loadHistory();
        if (arr.length() == 0) {
            Toast.makeText(this, "No history yet", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout listHolder = new LinearLayout(this);
        listHolder.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listHolder);
        container.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

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
                try { item = current.getJSONObject(i); } catch (JSONException e) { continue; }
                String title = item.optString("title", "page");
                String url = item.optString("url", "");

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(12, 12, 12, 12);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, 4);
                row.setLayoutParams(rowLp);
                row.setBackgroundColor(Color.parseColor("#222222"));

                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
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
                    if (!tabs.isEmpty()) tabs.get(activeTabIndex).session.loadUri(url);
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                });

                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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
        container.addView(clearAllBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dialogHolder[0] = new AlertDialog.Builder(this)
                .setTitle("History")
                .setView(container)
                .setNegativeButton("Close", null)
                .create();
        dialogHolder[0].show();
    }

    // ---------- Downloads ----------

    private void promptDownloadConfirmation(WebResponse response) {
        String url = response.uri;
        Map<String, String> headers = response.headers;
        String contentType = headers != null ? headers.get("Content-Type") : null;
        String contentDisposition = headers != null ? headers.get("Content-Disposition") : null;
        final String finalFileName = extractFileName(url, contentDisposition, contentType);

        new AlertDialog.Builder(this)
                .setTitle("Download this file?")
                .setMessage(finalFileName + "\n\n" + url)
                .setPositiveButton("Download", (d, w) -> startFileDownload(response, finalFileName))
                .setNegativeButton("Cancel", null)
                .show();
    }

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

    private File getDownloadBaseDir() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    private void startFileDownload(WebResponse response, String fileName) {
        InputStream body = response.body;
        if (body == null) {
            Toast.makeText(this, "No file data received", Toast.LENGTH_SHORT).show();
            return;
        }

        File baseDir = getDownloadBaseDir();
        File destDir = new File(baseDir, "TVBrowser");
        destDir.mkdirs();
        File destFile = new File(destDir, fileName);
        String fullPath = destFile.getAbsolutePath();

        recordDownload(fileName, fullPath);
        Toast.makeText(this, "Downloading: " + fileName, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try (InputStream in = body;
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                runOnUiThread(() -> {
                    updateDownloadStatus(fullPath, "complete");
                    Toast.makeText(this, "Download complete: " + fileName, Toast.LENGTH_SHORT).show();
                    android.media.MediaScannerConnection.scanFile(this, new String[]{fullPath}, null, null);
                });
            } catch (Exception e) {
                Log.e("TVBrowser", "startFileDownload failed", e);
                runOnUiThread(() -> {
                    updateDownloadStatus(fullPath, "failed");
                    Toast.makeText(this, "Download failed: " + fileName, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ---------- Long-press link popup ----------

    private void showLinkPopup(String url, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Link");
        String display = text.isEmpty() ? url : text + "\n" + url;
        builder.setMessage(display);
        builder.setPositiveButton("Copy Link", (d, w) -> copyToClipboard("Link", url));
        builder.setNeutralButton("Save Target", (d, w) -> offerSaveForUrl(url));
        builder.setNegativeButton("Close", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        requestDialogButtonFocus(dialog);
    }

    private void requestDialogButtonFocus(AlertDialog dialog) {
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) positive.requestFocus();
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
        }
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    // ---------- Image/Media Save ----------

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

        String fileName = extractFileName(url, null, guessedMime);

        new AlertDialog.Builder(this)
                .setTitle("Save this file?")
                .setMessage(fileName + "\n\n" + url)
                .setPositiveButton("Save", (d, w) -> startUrlDownload(url, fileName))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startUrlDownload(String url, String fileName) {
        File baseDir = getDownloadBaseDir();
        File destDir = new File(baseDir, "TVBrowser");
        destDir.mkdirs();
        File destFile = new File(destDir, fileName);
        String fullPath = destFile.getAbsolutePath();

        recordDownload(fileName, fullPath);
        Toast.makeText(this, "Downloading: " + fileName, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                runOnUiThread(() -> {
                    updateDownloadStatus(fullPath, "complete");
                    Toast.makeText(this, "Saved: " + fileName, Toast.LENGTH_SHORT).show();
                    android.media.MediaScannerConnection.scanFile(this, new String[]{fullPath}, null, null);
                });
            } catch (Exception e) {
                Log.e("TVBrowser", "startUrlDownload failed", e);
                runOnUiThread(() -> {
                    updateDownloadStatus(fullPath, "failed");
                    Toast.makeText(this, "Save failed: " + fileName, Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
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

    private void recordDownload(String fileName, String filePath) {
        JSONArray arr = loadDownloads();
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", fileName);
            obj.put("path", filePath);
            obj.put("status", "downloading");
            arr.put(obj);
            saveDownloads(arr);
        } catch (JSONException e) {
            Log.e("TVBrowser", "recordDownload failed", e);
        }
    }

    private void updateDownloadStatus(String path, String status) {
        JSONArray arr = loadDownloads();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                if (path.equals(obj.optString("path"))) {
                    obj.put("status", status);
                }
            } catch (JSONException e) {
                Log.e("TVBrowser", "updateDownloadStatus failed", e);
            }
        }
        saveDownloads(arr);
    }

    private void removeDownloadRecord(int index) {
        JSONArray arr = loadDownloads();
        JSONArray newArr = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i != index) newArr.put(arr.opt(i));
        }
        saveDownloads(newArr);
    }

    private void showDownloadsManager() {
        JSONArray arr = loadDownloads();
        if (arr.length() == 0) {
            Toast.makeText(this, "No downloads yet", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout listHolder = new LinearLayout(this);
        listHolder.setOrientation(LinearLayout.VERTICAL);
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
                try { item = current.getJSONObject(i); } catch (JSONException e) { continue; }
                String name = item.optString("name", "file");
                String path = item.optString("path", "");
                String status = item.optString("status", "downloading");

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(12, 12, 12, 12);
                row.setBackgroundColor(Color.parseColor("#222222"));
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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

                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);

                Button openBtn = makeDownloadActionButton("complete".equals(status) ? "Open" : "Not Ready");
                openBtn.setEnabled("complete".equals(status));
                openBtn.setOnClickListener(v -> openDownloadedFile(path));
                btnRow.addView(openBtn);

                Button deleteBtn = makeDownloadActionButton("Delete");
                deleteBtn.setOnClickListener(v -> {
                    deleteDownloadedFile(path);
                    removeDownloadRecord(idx);
                    Toast.makeText(this, "Deleted: " + name, Toast.LENGTH_SHORT).show();
                    if (rebuildHolder[0] != null) rebuildHolder[0].run();
                });
                btnRow.addView(deleteBtn);

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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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

            String extension = MimeTypeMap.getFileExtensionFromUrl(path);
            String mime = extension != null
                    ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase())
                    : null;
            if (mime == null) mime = "*/*";

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
            if (file.exists()) file.delete();
        } catch (Exception e) {
            Log.e("TVBrowser", "deleteDownloadedFile failed", e);
        }
    }

    // ---------- Extensions ----------

    private void loadInstalledExtensions() {
        sRuntime.getWebExtensionController().list().accept(list -> {
            installedExtensions.clear();
            for (WebExtension ext : list) {
                if (JS_BRIDGE_ID.equals(ext.id)) continue;
                installedExtensions.add(ext);
                registerExtensionActionDelegate(ext);
            }
        }, error -> {});
    }

    private void registerExtensionActionDelegate(WebExtension extension) {
        extension.setActionDelegate(new WebExtension.ActionDelegate() {
            @Override
            public void onBrowserAction(WebExtension extension, GeckoSession session, WebExtension.Action action) {
                if (session == null) {
                    extensionActions.put(extension.id, action);
                }
            }

            @Override
            public GeckoResult<GeckoSession> onOpenPopup(WebExtension extension, WebExtension.Action action) {
                GeckoSession popupSession = new GeckoSession();
                GeckoJSBridge.attachSession(popupSession);
                popupSession.open(sRuntime);
                runOnUiThread(() -> showExtensionPopupDialog(popupSession));
                return GeckoResult.fromValue(popupSession);
            }

            @Override
            public GeckoResult<GeckoSession> onTogglePopup(WebExtension extension, WebExtension.Action action) {
                GeckoSession popupSession = new GeckoSession();
                GeckoJSBridge.attachSession(popupSession);
                popupSession.open(sRuntime);
                runOnUiThread(() -> showExtensionPopupDialog(popupSession));
                return GeckoResult.fromValue(popupSession);
            }
        });
    }

    private void showExtensionPopupDialog(GeckoSession popupSession) {
        int widthPx = (int) (340 * getResources().getDisplayMetrics().density);
        int heightPx = (int) (500 * getResources().getDisplayMetrics().density);
        int closeBarHeightPx = (int) (48 * getResources().getDisplayMetrics().density);

        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new ViewGroup.LayoutParams(widthPx, heightPx));

        GeckoView popupGeckoView = new GeckoView(this);
        FrameLayout.LayoutParams geckoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx - closeBarHeightPx);
        geckoParams.topMargin = closeBarHeightPx;
        popupGeckoView.setLayoutParams(geckoParams);
        popupGeckoView.setSession(popupSession);
        popupGeckoView.setFocusableInTouchMode(true);
        popupGeckoView.setFocusable(true);

        CursorView popupCursorView = new CursorView(this);
        popupCursorView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx - closeBarHeightPx));
        ((FrameLayout.LayoutParams) popupCursorView.getLayoutParams()).topMargin = closeBarHeightPx;

        Button closeButton = new Button(this);
        closeButton.setText("Close Extension Popup");
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, closeBarHeightPx);
        closeButton.setLayoutParams(closeParams);

        container.addView(popupGeckoView);
        container.addView(popupCursorView);
        container.addView(closeButton);

        sRuntime.getWebExtensionController().setTabActive(popupSession, true);

        boolean startInCursorMode = cursorModeEnabled;
        CursorController popupController = new CursorController(popupGeckoView, popupCursorView, popupSession, startInCursorMode);
        popupController.isPopup = true;
        popupController.extraHitTargets.add(closeButton);
        popupCursorView.setVisibility(startInCursorMode ? View.VISIBLE : View.GONE);

        popupSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                injectTabNavScript(session);
                injectZoomLock(session);
                if (!popupController.localCursorModeEnabled) {
                    GeckoJSBridge.evaluateJavascriptNoResult(session,
                            "window.__tvTabNav && window.__tvTabNav.enable();");
                }
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(container)
                .setOnDismissListener(d -> {
                    popupController.stopFrameLoop();
                    popupSession.close();
                    if (!tabs.isEmpty()) {
                        sRuntime.getWebExtensionController().setTabActive(tabs.get(activeTabIndex).session, true);
                    }
                })
                .create();

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                dialog.dismiss();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && event.getAction() == KeyEvent.ACTION_DOWN) {
                popupController.toggleCursorMode();
                return true;
            }
            return popupController.handleKeyEvent(event);
        });

        dialog.show();
        popupGeckoView.post(() -> {
            popupCursorView.setCursorPosition(popupGeckoView.getWidth() / 2f, popupGeckoView.getHeight() / 2f);
            popupGeckoView.requestFocus();
        });
    }

    private void promptInstallExtension() {
        List<String> menuItems = new ArrayList<>();
        menuItems.add("Install: Dark Reader");
        menuItems.add("Install: uBlock Origin");
        menuItems.add("Install: Custom .xpi URL...");
        for (WebExtension ext : installedExtensions) {
            menuItems.add("Manage: " + ext.metaData.name);
        }

        String[] items = menuItems.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Extensions")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        installExtension(EXTENSION_URLS[0]);
                    } else if (which == 1) {
                        installExtension(EXTENSION_URLS[1]);
                    } else if (which == 2) {
                        promptCustomExtensionUrl();
                    } else {
                        WebExtension selected = installedExtensions.get(which - 3);
                        showManageExtensionDialog(selected);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManageExtensionDialog(WebExtension extension) {
        String[] options = {"Open Settings/Popup", "Remove Extension"};
        new AlertDialog.Builder(this)
                .setTitle(extension.metaData.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openExtensionSettings(extension);
                    } else {
                        removeExtension(extension);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openExtensionSettings(WebExtension extension) {
        WebExtension.Action action = extensionActions.get(extension.id);
        if (action != null) {
            action.click();
            return;
        }
        Toast.makeText(this, "No toolbar action available yet for " + extension.metaData.name
                + " — try loading a webpage first, then retry", Toast.LENGTH_LONG).show();
    }

    private void removeExtension(WebExtension extension) {
        sRuntime.getWebExtensionController().uninstall(extension).accept(
            v -> runOnUiThread(() -> {
                installedExtensions.remove(extension);
                extensionActions.remove(extension.id);
                Toast.makeText(this, "Removed: " + extension.metaData.name, Toast.LENGTH_SHORT).show();
            }),
            error -> runOnUiThread(() ->
                Toast.makeText(this, "Remove failed: " + error.getMessage(), Toast.LENGTH_LONG).show())
        );
    }

    private void promptCustomExtensionUrl() {
        final EditText input = new EditText(this);
        input.setHint("Paste .xpi extension URL");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(this)
                .setTitle("Custom Extension URL")
                .setView(input)
                .setPositiveButton("Install", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) installExtension(url);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void installExtension(String xpiUrl) {
        sRuntime.getWebExtensionController()
                .install(xpiUrl)
                .accept(
                    extension -> runOnUiThread(() -> {
                        installedExtensions.add(extension);
                        registerExtensionActionDelegate(extension);
                        if (!tabs.isEmpty()) tabs.get(activeTabIndex).session.reload();
                        Toast.makeText(this, "Installed: " + extension.metaData.name, Toast.LENGTH_SHORT).show();
                    }),
                    error -> runOnUiThread(() ->
                            Toast.makeText(this, "Install failed: " + error.getMessage(), Toast.LENGTH_LONG).show())
                );
    }

    private void loadFromUrlBar() {
        if (tabs.isEmpty()) return;
        String input = urlBar.getText().toString().trim();
        if (input.isEmpty()) return;

        String url = input;
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            if (input.contains(".") && !input.contains(" ")) {
                url = "https://" + input;
            } else {
                url = "https://www.google.com/search?q=" + input.replace(" ", "+");
            }
        }
        tabs.get(activeTabIndex).session.loadUri(url);
        hideKeyboard();
        topBarHasKeyFocus = false;
        geckoView.requestFocus();
    }

    @Override
    protected void onDestroy() {
        if (mainCursorController != null) mainCursorController.stopFrameLoop();
        for (TabData tab : tabs) {
            tab.session.close();
        }
        super.onDestroy();
    }
}