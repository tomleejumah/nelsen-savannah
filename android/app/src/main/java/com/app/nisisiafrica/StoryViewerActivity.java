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

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.Utils.StoryViewsStore;
import com.app.nisisiafrica.data.Model.Story;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
    private TextView viewsLabel;
    private android.widget.ImageView menuButton;
    private MaterialButton cta;
    private final ArrayList<ProgressBar> progressBars = new ArrayList<>();
    private final Set<String> countedViews = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
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
        viewsLabel = findViewById(R.id.storyViews);
        menuButton = findViewById(R.id.storyMenu);

        View topChrome = findViewById(R.id.storyTopChrome);
        View bottomChrome = findViewById(R.id.storyBottomChrome);
        ViewCompat.setOnApplyWindowInsetsListener(topChrome, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(dp(12) + bars.left, bars.top + dp(8), dp(12) + bars.right, v.getPaddingBottom());
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomChrome, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(dp(18) + bars.left, v.getPaddingTop(), dp(18) + bars.right, bars.bottom + dp(18));
            return insets;
        });
        ViewCompat.requestApplyInsets(topChrome);

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
            // Don't steal taps from chrome buttons (close / CTA / menu).
            View top = findViewById(R.id.storyTopChrome);
            View bottom = findViewById(R.id.storyBottomChrome);
            if (hit(top, event) || hit(bottom, event)) return false;

            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) return true;
            if (action == MotionEvent.ACTION_UP) {
                float x = event.getX();
                // Left half → previous, right half → next (WhatsApp / IG).
                if (x < v.getWidth() / 2f) {
                    goToPrevious();
                } else {
                    goToNext();
                }
                return true;
            }
            return false;
        });
    }

    private static boolean hit(View child, MotionEvent event) {
        if (child == null || child.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        child.getLocationOnScreen(loc);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= loc[0] && rawX <= loc[0] + child.getWidth()
                && rawY >= loc[1] && rawY <= loc[1] + child.getHeight();
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

        headerName.setText(story.displayLabel());
        String logo = !TextUtils.isEmpty(story.logoUrl) ? story.logoUrl : story.mediaUrl;
        Glide.with(this).load(logo).placeholder(R.drawable.ic_image_placeholder).into(headerLogo);
        Glide.with(this).load(story.mediaUrl).placeholder(R.drawable.ic_image_placeholder).into(storyImage);

        if (!TextUtils.isEmpty(story.caption)) {
            caption.setText(story.caption);
            caption.setVisibility(View.VISIBLE);
        } else {
            caption.setVisibility(View.GONE);
        }

        if (!story.isPersonal() && !TextUtils.isEmpty(story.ctaUrl)) {
            cta.setVisibility(View.VISIBLE);
            cta.setOnClickListener(v -> {
                String url = normalizeUrl(story.ctaUrl);
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, "Can't open link", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            cta.setVisibility(View.GONE);
        }

        setupOwnerControls(story);
        registerView(story);

        startProgress(index);
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "https://" + url;
        return url;
    }

    /** Counts one view per story per viewing session and shows the tally to the owner. */
    private void registerView(Story story) {
        if (story == null || TextUtils.isEmpty(story.storyId)) return;
        StoryViewsStore.markSeen(this, story.storyId);
        String me = FirebaseAuth.getInstance().getUid();
        boolean isOwner = me != null && me.equals(story.ownerId);

        if (!isOwner && countedViews.add(story.storyId)) {
            FirebaseDatabase.getInstance().getReference("stories")
                    .child(story.storyId).child("views")
                    .setValue(ServerValue.increment(1));
            story.views += 1;
        }

        if (isOwner) {
            viewsLabel.setVisibility(View.VISIBLE);
            viewsLabel.setText(story.views + (story.views == 1 ? " view" : " views"));
        } else {
            viewsLabel.setVisibility(View.GONE);
        }
    }

    private boolean isCancelled = false;
    /** Skip resume-on-dismiss when Close/Delete is in flight. */
    private boolean ownerActionPending = false;

    /** Shows the three-dots menu (Close / Delete) only to the story's owner. */
    private void setupOwnerControls(Story story) {
        String me = FirebaseAuth.getInstance().getUid();
        boolean isOwner = me != null && me.equals(story.ownerId);
        if (!isOwner || TextUtils.isEmpty(story.storyId)) {
            menuButton.setVisibility(View.GONE);
            menuButton.setOnClickListener(null);
            return;
        }
        final String storyId = story.storyId;
        menuButton.setVisibility(View.VISIBLE);
        menuButton.setOnClickListener(v -> {
            if (currentAnimator != null) {
                isCancelled = true;
                currentAnimator.cancel();
            }
            androidx.appcompat.widget.PopupMenu popup =
                    new androidx.appcompat.widget.PopupMenu(this, menuButton, Gravity.END);
            popup.getMenu().add(0, 1, 0, "Close story");
            popup.getMenu().add(0, 2, 1, "Delete story");
            popup.setOnMenuItemClickListener(item -> {
                DatabaseReference ref = FirebaseDatabase.getInstance()
                        .getReference("stories").child(storyId);
                if (item.getItemId() == 1) {
                    ownerActionPending = true;
                    ref.child("active").setValue(false)
                            .addOnSuccessListener(unused -> {
                                android.widget.Toast.makeText(this, "Story closed",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                removeCurrentAndContinue(storyId);
                            })
                            .addOnFailureListener(e -> {
                                ownerActionPending = false;
                                android.widget.Toast.makeText(this,
                                        "Could not close story",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                if (!isFinishing()) startProgress(currentIndex);
                            });
                } else {
                    ownerActionPending = true;
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Delete story?")
                            .setMessage("This removes the story for everyone.")
                            .setPositiveButton("Delete", (d, w) ->
                                    ref.removeValue()
                                            .addOnSuccessListener(unused -> {
                                                android.widget.Toast.makeText(this,
                                                        "Story deleted",
                                                        android.widget.Toast.LENGTH_SHORT).show();
                                                removeCurrentAndContinue(storyId);
                                            })
                                            .addOnFailureListener(e -> {
                                                ownerActionPending = false;
                                                android.widget.Toast.makeText(this,
                                                        "Could not delete story",
                                                        android.widget.Toast.LENGTH_SHORT).show();
                                                if (!isFinishing()) startProgress(currentIndex);
                                            }))
                            .setNegativeButton("Cancel", (d, w) -> {
                                ownerActionPending = false;
                                if (!isFinishing()) startProgress(currentIndex);
                            })
                            .setOnCancelListener(d -> {
                                ownerActionPending = false;
                                if (!isFinishing()) startProgress(currentIndex);
                            })
                            .show();
                }
                return true;
            });
            popup.setOnDismissListener(menu -> {
                if (!isFinishing() && !ownerActionPending) {
                    startProgress(currentIndex);
                }
            });
            popup.show();
        });
    }

    /** Drop a closed/deleted story from this session and show the next, or exit. */
    private void removeCurrentAndContinue(String storyId) {
        ownerActionPending = false;
        if (stories == null) {
            finish();
            return;
        }
        int removeAt = -1;
        for (int i = 0; i < stories.size(); i++) {
            if (storyId.equals(stories.get(i).storyId)) {
                removeAt = i;
                break;
            }
        }
        if (removeAt >= 0) stories.remove(removeAt);
        if (stories.isEmpty()) {
            finish();
            return;
        }
        buildProgressBars();
        int next = Math.min(Math.max(removeAt, 0), stories.size() - 1);
        showStory(next);
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
