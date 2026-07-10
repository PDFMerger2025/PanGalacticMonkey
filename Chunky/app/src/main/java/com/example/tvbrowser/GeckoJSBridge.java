package com.example.tvbrowser;

import org.mozilla.geckoview.*;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class GeckoJSBridge {
    private static final Map<GeckoSession, WebExtension.Port> ports = new ConcurrentHashMap<>();
    private static final Map<String, GeckoResult<String>> pending = new ConcurrentHashMap<>();
    private static final List<GeckoSession> pendingSessions = new CopyOnWriteArrayList<>();
    private static WebExtension sBridgeExtension;

    public static void register(GeckoRuntime runtime, android.content.Context debugContext) {
        runtime.getWebExtensionController()
            .ensureBuiltIn("resource://android/assets/js-bridge/", "js-bridge@tvbrowser")
            .accept(ext -> {
                sBridgeExtension = ext;
                for (GeckoSession queued : pendingSessions) {
                    doAttach(queued);
                }
                pendingSessions.clear();
            }, err -> android.util.Log.e("GeckoJSBridge", "register failed", err));
    }

    public static void attachSession(GeckoSession session) {
        if (sBridgeExtension == null) {
            pendingSessions.add(session);
            return;
        }
        doAttach(session);
    }

    private static void doAttach(GeckoSession session) {
        session.getWebExtensionController().setMessageDelegate(
            sBridgeExtension,
            new WebExtension.MessageDelegate() {
                @Override
                public void onConnect(WebExtension.Port port) {
                    ports.put(session, port);
                    port.setDelegate(new WebExtension.PortDelegate() {
                        @Override
                        public void onPortMessage(Object message, WebExtension.Port port) {
                            handleIncoming(message);
                        }
                        @Override
                        public void onDisconnect(WebExtension.Port port) {
                            ports.remove(session);
                        }
                    });
                }
            },
            "browser"
        );
    }

    private static void handleIncoming(Object message) {
        try {
            JSONObject obj = message instanceof JSONObject
                ? (JSONObject) message
                : new JSONObject(String.valueOf(message));
            String id = obj.optString("id", null);
            if (id == null) return;
            GeckoResult<String> result = pending.remove(id);
            if (result == null) return;
            Object r = obj.opt("result");
            result.complete(r == null ? null : String.valueOf(r));
        } catch (JSONException e) {
            android.util.Log.e("GeckoJSBridge", "bad message", e);
        }
    }

    public static GeckoResult<String> evaluateJavascript(GeckoSession session, String js) {
        GeckoResult<String> result = new GeckoResult<>();
        WebExtension.Port port = ports.get(session);
        if (port == null || js == null) {
            result.complete(null);
            return result;
        }
        String id = UUID.randomUUID().toString();
        pending.put(id, result);
        try {
            JSONObject payload = new JSONObject();
            payload.put("__tvEval", true);
            payload.put("id", id);
            payload.put("code", js);
            port.postMessage(payload);
        } catch (JSONException e) {
            pending.remove(id);
            result.complete(null);
        }
        return result;
    }

    public static void evaluateJavascriptNoResult(GeckoSession session, String js) {
        evaluateJavascript(session, js);
    }
}