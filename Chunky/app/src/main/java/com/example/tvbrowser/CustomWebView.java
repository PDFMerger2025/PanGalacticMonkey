package com.example.tvbrowser;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public class CustomWebView extends FrameLayout {
    private GeckoView geckoView;
    private GeckoSession session;

    public CustomWebView(Context context) {
        super(context);
        init();
    }

    public CustomWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        geckoView = new GeckoView(getContext());
        geckoView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(geckoView);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setSession(GeckoSession session) {
        this.session = session;
        geckoView.setSession(session);
    }

    public GeckoSession getSession() {
        return session;
    }

    public GeckoView getGeckoView() {
        return geckoView;
    }

    // Do NOT override requestFocus(), getHeight(), or getWidth() – they are final.
}