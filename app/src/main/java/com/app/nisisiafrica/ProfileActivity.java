package com.app.nisisiafrica;

import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.palette.graphics.Palette;

import com.app.nisisiafrica.Utils.EdgeBlurImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class ProfileActivity extends AppCompatActivity {
    private View gradientOverlay;
    private EdgeBlurImageView dpImage;
    private TextView title;
    private CustomTarget<Bitmap> paletteTarget;
    private int defaultColor;
    ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_profile);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        View root = findViewById(R.id.root);
        gradientOverlay = findViewById(R.id.gradientOverlay);
        dpImage = findViewById(R.id.dpImage);
        dpImage.setBlurRadius(80f);

        defaultColor = ContextCompat.getColor(this, android.R.color.darker_gray);

        String imageUrl = "https://images.unsplash.com/photo-1756142007128-f431ede241cc?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHx0b3BpYy1mZWVkfDg4fHRvd0paRnNrcEdnfHxlbnwwfHx8fHw%3D";
        loadAndStyle(imageUrl);

        float radius = 20f;

        BlurTarget target = findViewById(R.id.target);
        BlurView blurView = findViewById(R.id.blurViewName);
        BlurView blurViewDescHead = findViewById(R.id.blurViewDescHead);
        BlurView blurViewDesc = findViewById(R.id.blurViewDesc);
        BlurView blurViewRc = findViewById(R.id.bottomRc);

        setupBlur(target, radius, blurView, blurViewDescHead, blurViewDesc, blurViewRc);

    }
    private void setupBlur(BlurTarget target, float radius, BlurView... blurViews) {
        for (BlurView blurView : blurViews) {
            blurView.setupWith(target).setBlurRadius(radius);
            blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            blurView.setClipToOutline(true);
        }
    }



    private void loadAndStyle(String url) {
        // Load as Bitmap for color extraction
        paletteTarget = new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                // Extract colors from the unprocessed bitmap (accurate for Palette)
                Palette.from(resource).generate(palette -> {
                    int dominant = palette != null ? palette.getDominantColor(defaultColor) : defaultColor;
                    int vibrant = palette != null ? palette.getVibrantColor(dominant) : dominant;
                    int muted = palette != null ? palette.getMutedColor(dominant) : dominant;
                    applyColorsAnimated(dominant, vibrant, muted);
                });

                // Load the non-blurred image into the profile photo (top section only)
                Glide.with(ProfileActivity.this)
                        .load(url)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.donation)
                        .into(dpImage);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        };

        Glide.with(this)
                .asBitmap()
                .load(url)
                .apply(RequestOptions.centerCropTransform())
                .into(paletteTarget);
    }

    private void applyColorsAnimated(int dominant, int vibrant, int muted) {

        int c1 = setAlpha(muted, 0.8f);
        int c2 = setAlpha(dominant, 0.7f);
        int c3 = setAlpha(vibrant, 0.6f);
        // For a simple 2-color gradient, use dominant to vibrant; for 3-color, use LinearGradient with positions
        GradientDrawable grad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{c1, c2, c3}
        );
        grad.setColors(new int[]{c1, c2, c3});
        grad.setGradientType(GradientDrawable.LINEAR_GRADIENT);
//        grad.setOrientation(GradientDrawable.Orientation.TL_BR);

        // Crossfade the overlay to the new gradient
        final Drawable before = gradientOverlay.getBackground();
        gradientOverlay.setBackground(grad);
        gradientOverlay.setAlpha(0f);
        gradientOverlay.animate().alpha(1f).setDuration(350).start();

        // Animate status bar to a darkened version of dominant (Spotify-style)
//        int newStatus = darken(dominant, 0.2f);
        Window w = getWindow();
        int start = w.getStatusBarColor();
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), start, c1);
        va.setDuration(350);
        va.addUpdateListener(animation -> w.setStatusBarColor((int) animation.getAnimatedValue()));
        va.start();

        // Set readable text color based on dominant todo
        int textColor = isDark(dominant) ? Color.WHITE : Color.BLACK;
    }

    // Your helper methods remain the same
    private int setAlpha(int color, float alphaFactor) {
        int a = Math.round(Color.alpha(color) * alphaFactor);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int darken(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, hsv[2] - factor);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private boolean isDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (paletteTarget != null) {
//            Glide.with(this).clear(paletteTarget);
        }
    }
}
