package com.app.nisisiafrica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.app.nisisiafrica.data.Model.Story;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

public class StoryViewerActivity extends AppCompatActivity {

    public static final String EXTRA_STORIES = "extra_stories";
    public static final String EXTRA_START_INDEX = "extra_start_index";
    private static final long STORY_DURATION_MS = 5000L;

    private ArrayList<Story> stories;
    private int currentIndex = 0;
    private ObjectAnimator currentAnimator;

    private LinearLayout progressContainer;
    private android.widget.ImageView storyImage;
    private CircleImageView headerLogo;
    private TextView headerName;
    private TextView caption;
    private MaterialButton cta;
    private final ArrayList<ProgressBar> progressBars = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_viewer);

        stories = getIntent().getParcelableArrayListExtra(EXTRA_STORIES);
        currentIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);

        if (stories == null || stories.isEmpty()) {
            finish();
            return;
        }

        progressContainer = findViewById(R.id.progressContainer);
        storyImage = findViewById(R.id.storyImage);
        headerLogo = findViewById(R.id.storyHeaderLogo);
        headerName = findViewById(R.id.storyHeaderName);
        caption = findViewById(R.id.storyCaption);
        cta = findViewById(R.id.storyCta);

        findViewById(R.id.storyClose).setOnClickListener(v -> finish());

        buildProgressBars();
        setupTouch();

        showStory(currentIndex);
    }

    private void buildProgressBars() {
        progressContainer.removeAllViews();
        progressBars.clear();
        for (int i = 0; i < stories.size(); i++) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(0);
            bar.setProgressDrawable(ContextCompat.getDrawable(this, R.drawable.story_progress));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, dp(3), 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            bar.setLayoutParams(lp);
            progressContainer.addView(bar);
            progressBars.add(bar);
        }
    }

    private void setupTouch() {
        View root = findViewById(R.id.storyRoot);
        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getX();
                if (x < v.getWidth() / 3f) {
                    goToPrevious();
                } else {
                    goToNext();
                }
                return true;
            }
            return false;
        });
    }

    private void showStory(int index) {
        if (index < 0 || index >= stories.size()) {
            finish();
            return;
        }
        currentIndex = index;
        Story story = stories.get(index);

        for (int i = 0; i < progressBars.size(); i++) {
            progressBars.get(i).setProgress(i < index ? 100 : 0);
        }

        headerName.setText(story.companyName != null ? story.companyName : "");
        String logo = !TextUtils.isEmpty(story.logoUrl) ? story.logoUrl : story.mediaUrl;
        Glide.with(this).load(logo).placeholder(R.drawable.ic_image_placeholder).into(headerLogo);
        Glide.with(this).load(story.mediaUrl).placeholder(R.drawable.ic_image_placeholder).into(storyImage);

        if (!TextUtils.isEmpty(story.caption)) {
            caption.setText(story.caption);
            caption.setVisibility(View.VISIBLE);
        } else {
            caption.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(story.ctaUrl)) {
            cta.setVisibility(View.VISIBLE);
            cta.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(story.ctaUrl)));
                } catch (Exception ignored) {
                }
            });
        } else {
            cta.setVisibility(View.GONE);
        }

        startProgress(index);
    }

    private void startProgress(int index) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }
        ProgressBar bar = progressBars.get(index);
        bar.setProgress(0);
        currentAnimator = ObjectAnimator.ofInt(bar, "progress", 0, 100);
        currentAnimator.setDuration(STORY_DURATION_MS);
        currentAnimator.setInterpolator(null);
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isCancelled) goToNext();
                isCancelled = false;
            }
        });
        currentAnimator.start();
    }

    private boolean isCancelled = false;

    private void goToNext() {
        if (currentAnimator != null) {
            isCancelled = true;
            currentAnimator.cancel();
        }
        if (currentIndex + 1 < stories.size()) {
            showStory(currentIndex + 1);
        } else {
            finish();
        }
    }

    private void goToPrevious() {
        if (currentAnimator != null) {
            isCancelled = true;
            currentAnimator.cancel();
        }
        if (currentIndex - 1 >= 0) {
            showStory(currentIndex - 1);
        } else {
            showStory(currentIndex);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentAnimator != null) {
            isCancelled = true;
            currentAnimator.cancel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentAnimator != null) {
            isCancelled = true;
            currentAnimator.cancel();
        }
    }
}
