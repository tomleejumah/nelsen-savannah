package com.app.nisisiafrica;

import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.request.RequestOptions;
import jp.wasabeef.glide.transformations.BlurTransformation;

public class MentorProfileActivity extends AppCompatActivity {
    private View root;
    private View gradientOverlay;  // Use this for the dynamic gradient background
    private ImageView dpImage;  // Profile photo at top
    private TextView title;
    private CustomTarget<Bitmap> paletteTarget;
    private int defaultColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.archievement_layout);  // Your layout file

        root = findViewById(R.id.root);
        gradientOverlay = findViewById(R.id.gradientOverlay);
        dpImage = findViewById(R.id.dpImage);
        defaultColor = ContextCompat.getColor(this, android.R.color.darker_gray);

        String imageUrl = "https://images.unsplash.com/photo-1755812321862-fc8396cd7961?w=500&auto=format&fit=crop&q=60&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxmZWF0dXJlZC1waG90b3MtZmVlZHw0N3x8fGVufDB8fHx8fA%3D%3D";
        loadAndStyle(imageUrl);
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
                Glide.with(MentorProfileActivity.this)
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
        // Create gradient using extracted colors: muted/dominant at top (subtle under image), vibrant at bottom
        // Adjust order/alpha for Spotify-like fade (e.g., top subtle, bottom bold)
        int c1 = setAlpha(muted, 0.8f);  // Top: Muted for soft start
        int c2 = setAlpha(dominant, 0.7f);  // Middle: Dominant for balance
        int c3 = setAlpha(vibrant, 0.6f);  // Bottom: Vibrant for energy
        // For a simple 2-color gradient, use dominant to vibrant; for 3-color, use LinearGradient with positions
        GradientDrawable grad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{c1, c2, c3}
        );
        grad.setColors(new int[]{c1, c2, c3});  // Ensure colors are set
        grad.setGradientType(GradientDrawable.LINEAR_GRADIENT);

        // Crossfade the overlay to the new gradient
        final Drawable before = gradientOverlay.getBackground();
        gradientOverlay.setBackground(grad);
        gradientOverlay.setAlpha(0f);
        gradientOverlay.animate().alpha(1f).setDuration(350).start();

        // Animate status bar to a darkened version of dominant (Spotify-style)
        int newStatus = darken(dominant, 0.2f);
        Window w = getWindow();
        int start = w.getStatusBarColor();
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), start, newStatus);
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
            Glide.with(this).clear(paletteTarget);
        }
    }
}
