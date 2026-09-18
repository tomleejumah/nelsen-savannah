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
 * Avatar ring: one arc per story — maroon = not viewed, white = viewed.
 * Full maroon only when every segment is unseen; full white when all viewed.
 */
public class StoryRingView extends FrameLayout {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    /** Parallel to bucket stories (oldest → newest). true = viewed. */
    private boolean[] segmentSeen = new boolean[]{false};
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

    /** @deprecated use {@link #setSegmentSeen(boolean[])} */
    public void setRingState(int segments, boolean hasUnseen) {
        boolean[] seen = new boolean[Math.max(1, segments)];
        if (!hasUnseen) {
            for (int i = 0; i < seen.length; i++) seen[i] = true;
        }
        setSegmentSeen(seen);
    }

    public void setSegmentSeen(@Nullable boolean[] seenPerStory) {
        if (seenPerStory == null || seenPerStory.length == 0) {
            this.segmentSeen = new boolean[]{false};
        } else {
            this.segmentSeen = seenPerStory.clone();
        }
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        float inset = strokeWidthPx / 2f;
        arcBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);

        int unseenColor = ContextCompat.getColor(getContext(), R.color.maroon_500);
        int seenColor = ContextCompat.getColor(getContext(), R.color.white);
        int n = segmentSeen.length;

        if (n == 1) {
            ringPaint.setColor(segmentSeen[0] ? seenColor : unseenColor);
            canvas.drawOval(arcBounds, ringPaint);
            return;
        }

        float sweep = 360f / n;
        float gap = Math.min(12f, sweep * 0.2f);
        float start = -90f + gap / 2f;
        for (int i = 0; i < n; i++) {
            ringPaint.setColor(segmentSeen[i] ? seenColor : unseenColor);
            canvas.drawArc(arcBounds, start + i * sweep, sweep - gap, false, ringPaint);
        }
    }
}
