package com.app.nisisiafrica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.Utils.StoryViewsStore;
import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.Model.Story;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StoryViewerActivity extends AppCompatActivity {

    public static final String EXTRA_STORIES = "extra_stories";
    public static final String EXTRA_START_INDEX = "extra_start_index";
    public static final String EXTRA_STORY_ID = "extra_story_id";
    private static final long STORY_DURATION_MS = 5000L;

    private ArrayList<Story> stories;
    private int currentIndex = 0;
    private ObjectAnimator currentAnimator;

    private LinearLayout progressContainer;
    private android.widget.ImageView storyImage;
    private android.widget.VideoView storyVideo;
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

        progressContainer = findViewById(R.id.progressContainer);
        storyImage = findViewById(R.id.storyImage);
        storyVideo = findViewById(R.id.storyVideo);
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

        if (stories == null || stories.isEmpty()) {
            String storyId = getIntent().getStringExtra(EXTRA_STORY_ID);
            if (TextUtils.isEmpty(storyId)) {
                finish();
                return;
            }
            FirebaseDatabase.getInstance().getReference("stories").child(storyId).get()
                    .addOnSuccessListener(snapshot -> {
                        Story story = snapshot.getValue(Story.class);
                        if (story == null || !story.active
                                || (story.expiresAt > 0 && story.expiresAt <= System.currentTimeMillis())) {
                            Toast.makeText(this, "This story is no longer available", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        stories = new ArrayList<>();
                        stories.add(story);
                        currentIndex = 0;
                        buildProgressBars();
                        setupTouch();
                        showStory(0);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Could not load story", Toast.LENGTH_SHORT).show();
                        finish();
                    });
            return;
        }

        buildProgressBars();
        setupTouch();

        showStory(currentIndex);
    }

    private void shareStory(Story story) {
        if (story == null || TextUtils.isEmpty(story.storyId)) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        String url = "https://nelsen-savannah.co.ke/stories/" + Uri.encode(story.storyId);
        String text = !TextUtils.isEmpty(story.caption) ? story.caption + "\n\n" + url : url;
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "Share story"));
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
            if (action == MotionEvent.ACTION_DOWN) {
                pauseStoryPlayback();
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                resumeStoryPlayback();
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                resumeStoryPlayback();
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
        if (menuButton != null) {
            menuButton.setVisibility(View.VISIBLE);
            menuButton.setOnClickListener(v -> shareStory(story));
        }

        for (int i = 0; i < progressBars.size(); i++) {
            progressBars.get(i).setProgress(i < index ? 100 : 0);
        }

        headerName.setText(story.displayLabel());
        bindPosterAvatar(story);
        bindStoryMedia(story);

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

        if (!"video".equalsIgnoreCase(story.mediaType)) {
            startProgress(index, STORY_DURATION_MS);
        }
    }

    private void bindStoryMedia(Story story) {
        if ("video".equalsIgnoreCase(story.mediaType)) {
            storyImage.setVisibility(View.GONE);
            storyVideo.setVisibility(View.VISIBLE);
            storyVideo.setVideoURI(Uri.parse(story.mediaUrl));
            storyVideo.setOnPreparedListener(player -> {
                player.setLooping(false);
                startProgress(currentIndex, Math.max(1L, player.getDuration()));
                storyVideo.start();
            });
        } else {
            storyVideo.stopPlayback();
            storyVideo.setVisibility(View.GONE);
            storyImage.setVisibility(View.VISIBLE);
            Glide.with(this).load(story.mediaUrl)
                    .placeholder(R.drawable.ic_image_placeholder).into(storyImage);
        }
    }

    /** Holding a story freezes both the progress timer and video, like modern story viewers. */
    private void pauseStoryPlayback() {
        if (currentAnimator != null && currentAnimator.isRunning()) currentAnimator.pause();
        if (storyVideo != null && storyVideo.isPlaying()) storyVideo.pause();
    }

    private void resumeStoryPlayback() {
        if (currentAnimator != null && currentAnimator.isPaused()) currentAnimator.resume();
        Story story = stories != null && currentIndex >= 0 && currentIndex < stories.size()
                ? stories.get(currentIndex) : null;
        if (story != null && "video".equalsIgnoreCase(story.mediaType)
                && storyVideo != null && !storyVideo.isPlaying()) {
            storyVideo.start();
        }
    }

    /** Header chip must be the poster's DP — never the story media itself. */
    private void bindPosterAvatar(Story story) {
        if (headerLogo == null || story == null) return;
        headerLogo.setImageResource(R.drawable.ic_person);
        String logo = story.logoUrl;
        // Older personal posts wrongly stored mediaUrl as logoUrl — ignore that.
        if (!TextUtils.isEmpty(logo)
                && (TextUtils.isEmpty(story.mediaUrl) || !logo.equals(story.mediaUrl))) {
            Glide.with(this).load(logo).placeholder(R.drawable.ic_person).circleCrop().into(headerLogo);
            return;
        }
        if (TextUtils.isEmpty(story.ownerId)) return;
        FirebaseDatabase.getInstance().getReference("users")
                .child(story.ownerId)
                .child("photoUrl")
                .get()
                .addOnSuccessListener(snap -> {
                    String photo = snap.getValue(String.class);
                    if (TextUtils.isEmpty(photo) || "default".equals(photo)) return;
                    if (isFinishing()) return;
                    Glide.with(this).load(photo).placeholder(R.drawable.ic_person).circleCrop()
                            .into(headerLogo);
                });
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "https://" + url;
        return url;
    }

    /** Records one authenticated receipt per viewer; only the owner can read receipts. */
    private void registerView(Story story) {
        if (story == null || TextUtils.isEmpty(story.storyId)) return;
        StoryViewsStore.markSeen(this, story.storyId);
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String me = user != null ? user.getUid() : null;
        boolean isOwner = me != null && me.equals(story.ownerId);

        if (!isOwner && user != null && countedViews.add(story.storyId)) {
            user.getIdToken(false).addOnSuccessListener(tokenResult ->
                    ApiClient.getLmsService()
                            .recordStoryView("Bearer " + tokenResult.getToken(), story.storyId)
                            .enqueue(new Callback<LmsModels.MapEnvelope>() {
                                @Override
                                public void onResponse(
                                        @androidx.annotation.NonNull Call<LmsModels.MapEnvelope> call,
                                        @androidx.annotation.NonNull Response<LmsModels.MapEnvelope> response) {
                                    // The API transaction updates both the unique receipt and aggregate count.
                                }

                                @Override
                                public void onFailure(
                                        @androidx.annotation.NonNull Call<LmsModels.MapEnvelope> call,
                                        @androidx.annotation.NonNull Throwable t) {
                                    countedViews.remove(story.storyId);
                                }
                            }));
        }

        if (isOwner) {
            viewsLabel.setVisibility(View.VISIBLE);
            viewsLabel.setText(story.views + (story.views == 1 ? " view" : " views") + " · Viewed by");
            viewsLabel.setOnClickListener(v -> showViewerReceipts(story));
        } else {
            viewsLabel.setVisibility(View.GONE);
            viewsLabel.setOnClickListener(null);
        }
    }

    private void showViewerReceipts(Story story) {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || story == null || TextUtils.isEmpty(story.storyId)) return;

        if (currentAnimator != null) {
            isCancelled = true;
            currentAnimator.cancel();
        }

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("Viewed by");
        title.setTextSize(20f);
        title.setTextColor(ContextCompat.getColor(this, R.color.ink));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText("Loading viewers…");
        status.setTextSize(14f);
        status.setTextColor(ContextCompat.getColor(this, R.color.muted));
        status.setPadding(0, dp(12), 0, dp(8));
        root.addView(status);

        sheet.setContentView(root);
        sheet.setOnDismissListener(d -> {
            if (!isFinishing() && !ownerActionPending) startProgress(currentIndex, currentStoryDurationMs());
        });
        sheet.show();

        user.getIdToken(false).addOnSuccessListener(tokenResult ->
                ApiClient.getLmsService()
                        .storyViewers("Bearer " + tokenResult.getToken(), story.storyId)
                        .enqueue(new Callback<LmsModels.StoryViewersEnvelope>() {
                            @Override
                            public void onResponse(
                                    @androidx.annotation.NonNull Call<LmsModels.StoryViewersEnvelope> call,
                                    @androidx.annotation.NonNull Response<LmsModels.StoryViewersEnvelope> response) {
                                if (isFinishing()) return;
                                LmsModels.StoryViewersEnvelope body = response.body();
                                if (!response.isSuccessful() || body == null || !body.ok || body.data == null) {
                                    status.setText("Could not load viewers");
                                    return;
                                }

                                story.views = body.data.views;
                                viewsLabel.setText(story.views + (story.views == 1 ? " view" : " views") + " · Viewed by");
                                root.removeView(status);

                                if (body.data.viewers == null || body.data.viewers.isEmpty()) {
                                    TextView empty = new TextView(StoryViewerActivity.this);
                                    empty.setText("No views yet");
                                    empty.setTextColor(ContextCompat.getColor(StoryViewerActivity.this, R.color.muted));
                                    empty.setPadding(0, dp(18), 0, 0);
                                    root.addView(empty);
                                    return;
                                }

                                for (LmsModels.StoryViewerDto viewer : body.data.viewers) {
                                    root.addView(viewerRow(viewer));
                                }
                            }

                            @Override
                            public void onFailure(
                                    @androidx.annotation.NonNull Call<LmsModels.StoryViewersEnvelope> call,
                                    @androidx.annotation.NonNull Throwable t) {
                                if (!isFinishing()) status.setText("Could not load viewers");
                            }
                        }))
                .addOnFailureListener(e -> status.setText("Could not authenticate viewer list"));
    }

    private View viewerRow(LmsModels.StoryViewerDto viewer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        CircleImageView avatar = new CircleImageView(this);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        avatar.setLayoutParams(avatarLp);
        avatar.setImageResource(R.drawable.ic_person);
        if (viewer != null && !TextUtils.isEmpty(viewer.photoUrl)) {
            Glide.with(this).load(viewer.photoUrl).placeholder(R.drawable.ic_person).into(avatar);
        }
        row.addView(avatar);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(dp(12));
        text.setLayoutParams(textLp);

        TextView name = new TextView(this);
        name.setText(viewer != null && !TextUtils.isEmpty(viewer.name) ? viewer.name : "Nelsen user");
        name.setTextSize(15f);
        name.setTextColor(ContextCompat.getColor(this, R.color.ink));
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        text.addView(name);

        TextView time = new TextView(this);
        long viewedAt = viewer != null ? viewer.viewedAt : 0L;
        time.setText(viewedAt > 0
                ? DateUtils.getRelativeTimeSpanString(
                        viewedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS)
                : "");
        time.setTextSize(12f);
        time.setTextColor(ContextCompat.getColor(this, R.color.muted));
        text.addView(time);

        row.addView(text);
        return row;
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
                                if (!isFinishing()) startProgress(currentIndex, currentStoryDurationMs());
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
                                                if (!isFinishing()) startProgress(currentIndex, currentStoryDurationMs());
                                            }))
                            .setNegativeButton("Cancel", (d, w) -> {
                                ownerActionPending = false;
                                if (!isFinishing()) startProgress(currentIndex, currentStoryDurationMs());
                            })
                            .setOnCancelListener(d -> {
                                ownerActionPending = false;
                                if (!isFinishing()) startProgress(currentIndex, currentStoryDurationMs());
                            })
                            .show();
                }
                return true;
            });
            popup.setOnDismissListener(menu -> {
                if (!isFinishing() && !ownerActionPending) {
                    startProgress(currentIndex, currentStoryDurationMs());
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

    private long currentStoryDurationMs() {
        if (stories != null && currentIndex >= 0 && currentIndex < stories.size()
                && "video".equalsIgnoreCase(stories.get(currentIndex).mediaType)
                && storyVideo != null && storyVideo.getDuration() > 0) {
            return storyVideo.getDuration();
        }
        return STORY_DURATION_MS;
    }

    private void startProgress(int index, long durationMs) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }
        ProgressBar bar = progressBars.get(index);
        bar.setProgress(0);
        currentAnimator = ObjectAnimator.ofInt(bar, "progress", 0, 100);
        currentAnimator.setDuration(durationMs);
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
