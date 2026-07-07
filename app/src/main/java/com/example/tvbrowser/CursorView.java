package com.example.tvbrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CursorView extends View {

    private float x;
    private float y;
    private final Paint fillPaint;
    private final Paint strokePaint;
    private final float radius;

    public CursorView(Context context) {
        this(context, null);
    }

    public CursorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        radius = context.getResources().getDisplayMetrics().density * 12f;

        fillPaint = new Paint();
        fillPaint.setColor(Color.WHITE);
        fillPaint.setAlpha(160);
        fillPaint.setAntiAlias(true);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint();
        strokePaint.setColor(Color.BLACK);
        strokePaint.setAlpha(200);
        strokePaint.setAntiAlias(true);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(context.getResources().getDisplayMetrics().density * 1.5f);

        setClickable(false);
        setFocusable(false);
    }

    public void setCursorPosition(float newX, float newY) {
        this.x = newX;
        this.y = newY;
        invalidate();
    }

    public float getCursorX() {
        return x;
    }

    public float getCursorY() {
        return y;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(x, y, radius, fillPaint);
        canvas.drawCircle(x, y, radius, strokePaint);
    }
}
