package com.app.nisisiafrica.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.app.nisisiafrica.R;

/**
 * Field Journal story stamp ring.
 * UNSEEN = solid maroon, SEEN = solid grey, MIXED = half maroon / half dashed grey.
 * Prefer {@link #setSegmentSeen(boolean[])} when per-story segments are known.
 */
public class RingStateView extends FrameLayout {

    public enum RingState {
        UNSEEN,
        SEEN,
        MIXED
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private final DashPathEffect dashEffect = new DashPathEffect(new float[]{8f, 6f}, 0f);

    private RingState state = RingState.UNSEEN;
    @Nullable
    private boolean[] segmentSeen;
    private float strokeWidthPx;

    public RingStateView(@NonNull Context context) {
        super(context);
        init();
    }

    public RingStateView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RingStateView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        strokeWidthPx = getResources().getDimension(R.dimen.ns_stamp_ring_stroke);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(strokeWidthPx);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        int pad = Math.round(strokeWidthPx);
        setPadding(pad, pad, pad, pad);
    }

    public void setRingState(@NonNull RingState state) {
        this.state = state;
        this.segmentSeen = null;
        applyTilt();
        invalidate();
    }

    /** One flag per story (oldest → newest). Derives UNSEEN / SEEN / MIXED + tilt. */
    public void setSegmentSeen(@Nullable boolean[] seenPerStory) {
        if (seenPerStory == null || seenPerStory.length == 0) {
            this.segmentSeen = new boolean[]{false};
            this.state = RingState.UNSEEN;
        } else {
            this.segmentSeen = seenPerStory.clone();
            boolean anyUnseen = false;
            boolean anySeen = false;
            for (boolean s : this.segmentSeen) {
                if (s) anySeen = true;
                else anyUnseen = true;
            }
            if (anyUnseen && anySeen) this.state = RingState.MIXED;
            else if (anySeen) this.state = RingState.SEEN;
            else this.state = RingState.UNSEEN;
        }
        applyTilt();
        invalidate();
    }

    private void applyTilt() {
        switch (state) {
            case UNSEEN:
                setRotation(-6f);
                break;
            case MIXED:
                setRotation(4f);
                break;
            case SEEN:
            default:
                setRotation(0f);
                break;
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        float inset = strokeWidthPx / 2f;
        arcBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);

        int unseen = ContextCompat.getColor(getContext(), R.color.ns_maroon);
        int seen = ContextCompat.getColor(getContext(), R.color.ns_grey);

        if (segmentSeen != null && segmentSeen.length > 1) {
            drawSegments(canvas, unseen, seen);
            return;
        }

        ringPaint.setPathEffect(null);
        switch (state) {
            case SEEN:
                ringPaint.setColor(seen);
                canvas.drawOval(arcBounds, ringPaint);
                break;
            case MIXED:
                ringPaint.setColor(unseen);
                canvas.drawArc(arcBounds, -90f, 180f, false, ringPaint);
                ringPaint.setColor(seen);
                ringPaint.setPathEffect(dashEffect);
                canvas.drawArc(arcBounds, 90f, 180f, false, ringPaint);
                ringPaint.setPathEffect(null);
                break;
            case UNSEEN:
            default:
                ringPaint.setColor(unseen);
                canvas.drawOval(arcBounds, ringPaint);
                break;
        }
    }

    private void drawSegments(Canvas canvas, int unseen, int seen) {
        int n = segmentSeen.length;
        float sweep = 360f / n;
        float gap = Math.min(12f, sweep * 0.2f);
        float start = -90f + gap / 2f;
        ringPaint.setPathEffect(null);
        for (int i = 0; i < n; i++) {
            boolean isSeen = segmentSeen[i];
            ringPaint.setColor(isSeen ? seen : unseen);
            if (isSeen) ringPaint.setPathEffect(dashEffect);
            else ringPaint.setPathEffect(null);
            canvas.drawArc(arcBounds, start + i * sweep, sweep - gap, false, ringPaint);
        }
        ringPaint.setPathEffect(null);
    }
}
