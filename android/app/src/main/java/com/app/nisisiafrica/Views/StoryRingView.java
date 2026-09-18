package com.app.nisisiafrica.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.app.nisisiafrica.R;

/**
 * Avatar ring: maroon (optionally segmented) while unseen; muted when fully seen.
 */
public class StoryRingView extends FrameLayout {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private int segmentCount = 1;
    private boolean unseen = true;
    private float strokeWidthPx;

    public StoryRingView(@NonNull Context context) {
        super(context);
        init();
    }

    public StoryRingView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StoryRingView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        strokeWidthPx = getResources().getDisplayMetrics().density * 2.5f;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(strokeWidthPx);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        setPadding((int) strokeWidthPx, (int) strokeWidthPx, (int) strokeWidthPx, (int) strokeWidthPx);
    }

    public void setRingState(int segments, boolean hasUnseen) {
        this.segmentCount = Math.max(1, segments);
        this.unseen = hasUnseen;
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        float inset = strokeWidthPx / 2f;
        arcBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);

        if (!unseen) {
            ringPaint.setColor(ContextCompat.getColor(getContext(), R.color.line));
            canvas.drawOval(arcBounds, ringPaint);
            return;
        }

        ringPaint.setColor(ContextCompat.getColor(getContext(), R.color.maroon_500));
        if (segmentCount <= 1) {
            canvas.drawOval(arcBounds, ringPaint);
            return;
        }

        float sweep = 360f / segmentCount;
        float gap = Math.min(10f, sweep * 0.18f);
        float start = -90f + gap / 2f;
        for (int i = 0; i < segmentCount; i++) {
            canvas.drawArc(arcBounds, start + i * sweep, sweep - gap, false, ringPaint);
        }
    }
}
