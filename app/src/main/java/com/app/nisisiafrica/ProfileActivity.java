package com.app.nisisiafrica;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;

import com.app.nisisiafrica.Model.CourseItem;
import com.app.nisisiafrica.Utils.FirebaseDataBaseHelper;
import com.app.nisisiafrica.Model.MentorItem;
import com.app.nisisiafrica.Model.UserData;
import com.app.nisisiafrica.Utils.EdgeBlurImageView;
import com.app.nisisiafrica.ViewModel.SharedUserViewModel;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;

public class ProfileActivity extends AppCompatActivity implements FirebaseCallback{
    private ConstraintLayout gradientOverlay;
    private EdgeBlurImageView dpImage;
    private TextView title;
    private CustomTarget<Bitmap> paletteTarget;
    private int defaultColor;
    private String id,role;
    boolean isFromMentor;
    private SharedUserViewModel sharedUserViewModel;
    private UserData userData;
    private TextView tv_username,tvDescription;
    BlurView blurViewName,blurViewDesc,blurViewDescHead,blurViewRc;
    private static final String TAG = "ProfileActivity";

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

        Intent intent = getIntent();
        if (intent != null) {
             isFromMentor = intent.getBooleanExtra(Constants.IS_MENTOR, false);
             id = isFromMentor ? intent.getStringExtra(Constants.MENTOR_ID) : intent.getStringExtra(Constants.USER_ID);
        }
        Log.d(TAG, "onCreate: "+id);

        title = findViewById(R.id.txtDescTittle);
        title.setText(isFromMentor ? "Mentor Profile" : "Profile");
        float radius = 20f;
        BlurTarget target = findViewById(R.id.target);
         blurViewName = findViewById(R.id.blurViewName);
         blurViewDescHead = findViewById(R.id.blurViewDescHead);
         blurViewDesc = findViewById(R.id.blurViewDesc);
         blurViewRc = findViewById(R.id.bottomRc);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        tv_username = findViewById(R.id.tv_username);
        tvDescription = findViewById(R.id.tv_Description);

        if (isFromMentor){

            FirebaseDataBaseHelper.INSTANCE.getMentorData(id,this);

//            FirebaseDataBaseHelper.INSTANCE.getMentorData(id, new FirebaseCallback() {
//                @Override
//                public void onUserDataReceived(@org.jetbrains.annotations.Nullable UserData userData) {
//
//                }
//
//                @Override
//                public void onMentorDataFetched(@org.jetbrains.annotations.Nullable MentorItem mentors) {
//                    blurViewDesc.setVisibility(
//                            TextUtils.isEmpty(mentors != null ? mentors.getMentorDescription() : null)
//                                    ? View.GONE
//                                    : View.VISIBLE
//                    );
//                    tvDescription.setText(mentors.getMentorDescription());
//                    tv_username.setText(mentors.getMentorName());
//
//                    loadAndStyle(mentors.getMentorImageUrl());
//                }
//
//                @Override
//                public void onMentorsIDFetched(@org.jetbrains.annotations.Nullable List<@org.jetbrains.annotations.Nullable String> mentorIds) {
//                }
//
//                @Override
//                public void onError(@org.jetbrains.annotations.Nullable Exception e) {
//                }
//            });
        }else {
            sharedUserViewModel = new ViewModelProvider(this).get(SharedUserViewModel.class);
            sharedUserViewModel.fetchingUserDataFromDB(id).observe(this, data -> {
                if (data != null) {
                    userData = data;
                    loadAndStyle(userData.getPhotoUrl());
                    blurViewDesc.setVisibility(
                            TextUtils.isEmpty(data.getBio())
                                    ? View.GONE
                                    : View.VISIBLE
                    );
                    tvDescription.setText(userData.getBio());
                    tv_username.setText(userData.getFirstName() + " " + userData.getLastName());

                    if (Objects.equals(userData.getUserRole(), "Mentee")) {
                        tv_username.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                    }
                }
            });

        }

        gradientOverlay = findViewById(R.id.root);
        dpImage = findViewById(R.id.dpImage);
        dpImage.setBlurRadius(80f);

        defaultColor = ContextCompat.getColor(this, android.R.color.darker_gray);

        setupBlur(target, radius, blurViewName, blurViewDescHead, blurViewDesc, blurViewRc);

        findViewById(R.id.iv_action).setOnClickListener(v -> {
            Intent intent1 = new Intent(ProfileActivity.this, EditProfileActivity.class);
            boolean isMentor = userData != null && Objects.equals(userData.getUserRole(), "Mentor");
            intent1.putExtra(Constants.IS_MENTOR, isMentor);
            intent1.putExtra(Constants.USER_ID, userData != null ? userData.getId() : id);
            startActivity(intent1);
        });

        ExtendedFloatingActionButton button = findViewById(R.id.btnNext);
        button.setVisibility(!isFromMentor ? View.GONE : View.VISIBLE);
        button.setText(!isFromMentor ? "" : "Book Now");
        button.setOnClickListener(v -> {
            Intent intent1 = new Intent(ProfileActivity.this, BookMentor.class);
            startActivity(intent1);
        });
    }
    private void setupBlur(BlurTarget target, float radius, BlurView... blurViews) {
        for (BlurView blurView : blurViews) {
            blurView.setupWith(target).setBlurRadius(radius);
            blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            blurView.setClipToOutline(true);
        }
    }

    @Override
    public void onUserDataReceived(@org.jetbrains.annotations.Nullable UserData userData) {

    }

    @Override
    public void onMentorDataFetched(@org.jetbrains.annotations.Nullable MentorItem mentors) {
        if (mentors != null) {
            blurViewDesc.setVisibility(
                    TextUtils.isEmpty(mentors.getMentorDescription())
                            ? View.GONE
                            : View.VISIBLE
            );
            tvDescription.setText(mentors.getMentorDescription());
            tv_username.setText(mentors.getMentorName());
            loadAndStyle(mentors.getMentorImageUrl());
        } else {
            // Handle null case
            blurViewDesc.setVisibility(View.GONE);
            tvDescription.setText("");
            tv_username.setText("");
        }
    }

    @Override
    public void onMentorsIDFetched(@org.jetbrains.annotations.Nullable List<@org.jetbrains.annotations.Nullable String> mentorIds) {
    }

    @Override
    public void onCoursesFetched(@NotNull List<@NotNull CourseItem> courses) {
//        FirebaseCallback.super.onCoursesFetched(courses);
    }

    @Override
    public void onMentorsFetched(@NotNull List<@NotNull MentorItem> mentors) {
//        FirebaseCallback.super.onMentorsFetched(mentors);
    }

    @Override
    public void onError(@org.jetbrains.annotations.Nullable Exception e) {
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
//        Window w = getWindow();
//        int start = w.getStatusBarColor();
//        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), start, c1);
//        va.setDuration(350);
//        va.addUpdateListener(animation -> w.setStatusBarColor((int) animation.getAnimatedValue()));
//        va.start();

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
