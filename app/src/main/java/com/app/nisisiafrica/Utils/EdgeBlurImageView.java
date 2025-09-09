package com.app.nisisiafrica.Utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

public class EdgeBlurImageView extends AppCompatImageView {
    private Paint maskPaint;
    private RectF bounds;
    private float blurRadius = 60f;

    public EdgeBlurImageView(Context context) {
        super(context);
        init();
    }

    public EdgeBlurImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EdgeBlurImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bounds = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds.set(0, 0, w, h);

        float centerX = w / 2f;
        float centerY = h / 2f;
        float radius = Math.min(w, h) / 2f - blurRadius;

        RadialGradient gradient = new RadialGradient(
                centerX, centerY, radius + blurRadius,
                new int[]{0xFFFFFFFF, 0x00FFFFFF},
                new float[]{radius / (radius + blurRadius), 1.0f},
                Shader.TileMode.CLAMP
        );
        maskPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getDrawable() == null) return;

        int saveCount = canvas.saveLayer(bounds, null);

        super.onDraw(canvas);
        canvas.drawRect(bounds, maskPaint);
        canvas.restoreToCount(saveCount);
    }

    public void setBlurRadius(float radius) {
        this.blurRadius = radius;
        invalidate();
    }
}