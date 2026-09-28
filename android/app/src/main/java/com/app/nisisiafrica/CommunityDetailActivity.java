package com.app.nisisiafrica;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.CommunityEventAdapter;
import com.app.nisisiafrica.Adapters.CommunityPostAdapter;
import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.data.Model.Community;
import com.app.nisisiafrica.data.Model.CommunityEvent;
import com.app.nisisiafrica.data.Model.CommunityPost;
import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.app.nisisiafrica.data.remote.StorageUploader;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommunityDetailActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID = "extra_community_id";
    public static final String EXTRA_COMMUNITY_NAME = "extra_community_name";

    private final CommunityRepository repository = new CommunityRepository();
    private String communityId;
    private String communityName;
    private String createdBy;
    private boolean isMember = false;
    private boolean showingPosts = true;
    private int rankPercent = 0;

    private TextView tvMembers, tvDescription, tvNoPosts, tvNoEvents, tvGroupName, tvOthers;
    private TextView tabPosts, tabEvents;
    private MaterialButton btnJoin, btnChangeLogo, btnCreateEvent;
    private ImageView imgGroupLogo, imgGroupBanner;
    private ImageButton btnBack;
    private LinearLayout eventsPanel;
    private OverlapImageListView overlapMembers;
    private CommunityPostAdapter postAdapter;
    private CommunityEventAdapter eventAdapter;
    private FloatingActionButton fabCreatePost;
    private ListenerRegistration communityReg, postsReg, eventsReg, rankReg;
    private ActivityResultLauncher<PickVisualMediaRequest> logoPicker;
    private ActivityResultLauncher<PickVisualMediaRequest> bannerPicker;
    private boolean pickingBanner = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_community_detail);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        communityId = getIntent().getStringExtra(EXTRA_COMMUNITY_ID);
        communityName = getIntent().getStringExtra(EXTRA_COMMUNITY_NAME);
        if (communityId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setTitle("");
        }

        tvMembers = findViewById(R.id.tvMembers);
        tvDescription = findViewById(R.id.tvDescription);
        tvNoPosts = findViewById(R.id.tvNoPosts);
        tvNoEvents = findViewById(R.id.tvNoEvents);
        tvGroupName = findViewById(R.id.tvGroupName);
        tvOthers = findViewById(R.id.tvOthersCount);
        tabPosts = findViewById(R.id.tabPosts);
        tabEvents = findViewById(R.id.tabEvents);
        btnJoin = findViewById(R.id.btnJoin);
        btnChangeLogo = findViewById(R.id.btnChangeLogo);
        btnCreateEvent = findViewById(R.id.btnCreateEvent);
        imgGroupLogo = findViewById(R.id.imgGroupLogo);
        imgGroupBanner = findViewById(R.id.imgGroupBanner);
        btnBack = findViewById(R.id.btnBack);
        eventsPanel = findViewById(R.id.eventsPanel);
        overlapMembers = findViewById(R.id.overlapMembers);
        fabCreatePost = findViewById(R.id.fabCreatePost);

        ViewCompat.setOnApplyWindowInsetsListener(btnBack, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            ViewGroupMargin(v, bars.top + dp(8));
            return insets;
        });
        ViewCompat.requestApplyInsets(btnBack);

        if (!TextUtils.isEmpty(communityName)) {
            tvGroupName.setText(stripPrefix(communityName));
        }

        logoPicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri != null) {
                        if (pickingBanner) uploadBanner(uri);
                        else uploadLogo(uri);
                    }
                });
        bannerPicker = logoPicker;

        btnBack.setOnClickListener(v -> finish());
        btnChangeLogo.setOnClickListener(v -> {
            pickingBanner = false;
            logoPicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });
        imgGroupLogo.setOnClickListener(v -> {
            if (canEditMedia()) btnChangeLogo.performClick();
        });
        imgGroupBanner.setOnClickListener(v -> {
            if (!canEditMedia()) return;
            pickingBanner = true;
            bannerPicker.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        RecyclerView rvPosts = findViewById(R.id.rvPosts);
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        postAdapter = new CommunityPostAdapter(post -> {
            Intent intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra(PostDetailActivity.EXTRA_COMMUNITY_ID, communityId);
            intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
            startActivity(intent);
        });
        rvPosts.setAdapter(postAdapter);

        RecyclerView rvEvents = findViewById(R.id.rvEvents);
        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        eventAdapter = new CommunityEventAdapter();
        rvEvents.setAdapter(eventAdapter);

        btnJoin.setOnClickListener(v -> toggleMembership());
        tabPosts.setOnClickListener(v -> showTab(true));
        tabEvents.setOnClickListener(v -> showTab(false));
        btnCreateEvent.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateEventActivity.class);
            intent.putExtra(CreateEventActivity.EXTRA_COMMUNITY_ID, communityId);
            intent.putExtra(CreateEventActivity.EXTRA_COMMUNITY_NAME, communityName);
            startActivity(intent);
        });

        fabCreatePost.setOnClickListener(v -> {
            if (!isMember) {
                Toast.makeText(this, "Join this group to post", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, CreatePostActivity.class);
            intent.putExtra(CreatePostActivity.EXTRA_COMMUNITY_ID, communityId);
            startActivity(intent);
        });

        refreshMembership();
        showTab(true);
    }

    private void ViewGroupMargin(View v, int top) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        lp.topMargin = top;
        v.setLayoutParams(lp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String stripPrefix(String name) {
        if (name == null) return "";
        if (name.startsWith("r/") || name.startsWith("R/")) return name.substring(2);
        return name;
    }

    private boolean canEditMedia() {
        String me = FirebaseAuth.getInstance().getUid();
        return Roles.isMentor() || Roles.canManageApp() || Roles.canManageSchoolUsers()
                || (me != null && me.equals(createdBy));
    }

    private void showTab(boolean posts) {
        showingPosts = posts;
        tabPosts.setTextColor(getColor(posts ? R.color.ns_maroon : R.color.ns_grey));
        tabEvents.setTextColor(getColor(posts ? R.color.ns_grey : R.color.ns_maroon));
        tabPosts.setTypeface(null, posts ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tabEvents.setTypeface(null, posts ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        findViewById(R.id.rvPosts).setVisibility(posts ? View.VISIBLE : View.GONE);
        tvNoPosts.setVisibility(posts && postAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        eventsPanel.setVisibility(posts ? View.GONE : View.VISIBLE);
        fabCreatePost.setVisibility(posts ? View.VISIBLE : View.GONE);
        btnCreateEvent.setVisibility(!posts && canEditMedia() ? View.VISIBLE : View.GONE);
    }

    private void uploadLogo(Uri uri) {
        btnChangeLogo.setEnabled(false);
        StorageUploader.upload(uri, "community_icons", (ok, url) -> {
            btnChangeLogo.setEnabled(true);
            if (!ok || url == null) {
                Toast.makeText(this, "Logo upload failed", Toast.LENGTH_SHORT).show();
                return;
            }
            repository.updateCommunityIcon(communityId, url, success -> {
                if (success) {
                    Glide.with(this).load(url).circleCrop().into(imgGroupLogo);
                    Toast.makeText(this, "Logo updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Could not save logo", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void uploadBanner(Uri uri) {
        StorageUploader.upload(uri, "banners", (ok, url) -> {
            if (!ok || url == null) {
                Toast.makeText(this, "Banner upload failed", Toast.LENGTH_SHORT).show();
                return;
            }
            repository.updateCommunityBanner(communityId, url, success -> {
                if (success) {
                    Glide.with(this).load(url).centerCrop().into(imgGroupBanner);
                    Toast.makeText(this, "Banner updated", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Could not save banner", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void bindLogo(String iconUrl) {
        if (!TextUtils.isEmpty(iconUrl)) {
            Glide.with(this).load(iconUrl).placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher).circleCrop().into(imgGroupLogo);
        } else {
            imgGroupLogo.setImageResource(R.mipmap.ic_launcher);
        }
    }

    private void bindBanner(String bannerUrl) {
        if (!TextUtils.isEmpty(bannerUrl)) {
            Glide.with(this).load(bannerUrl).centerCrop().into(imgGroupBanner);
        } else {
            imgGroupBanner.setImageDrawable(null);
            imgGroupBanner.setBackgroundColor(getColor(R.color.ns_maroon));
        }
    }

    private void bindMemberStack(Community c) {
        List<String> urls = new ArrayList<>();
        if (c.getRecentMemberAvatars() != null) {
            for (String url : c.getRecentMemberAvatars()) {
                if (!TextUtils.isEmpty(url)) urls.add(url);
            }
        }
        long others = Math.max(0, c.getMemberCount() - urls.size());
        if (urls.isEmpty()) {
            overlapMembers.setVisibility(View.GONE);
            tvOthers.setVisibility(View.GONE);
            return;
        }
        overlapMembers.setVisibility(View.VISIBLE);
        overlapMembers.setTag(urls);
        if (others > 0) {
            tvOthers.setVisibility(View.VISIBLE);
            tvOthers.setText(getString(R.string.group_others_count,
                    (int) Math.min(others, Integer.MAX_VALUE)));
        } else {
            tvOthers.setVisibility(View.GONE);
        }
        final ArrayList<Bitmap> bitmaps = new ArrayList<>();
        final int total = Math.min(urls.size(), 3);
        for (int i = 0; i < total; i++) {
            Glide.with(this).asBitmap().load(urls.get(i))
                    .apply(RequestOptions.circleCropTransform())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                                    @Nullable Transition<? super Bitmap> transition) {
                            bitmaps.add(resource);
                            if (bitmaps.size() == total && urls.equals(overlapMembers.getTag())) {
                                overlapMembers.setImageList(bitmaps);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        }
    }

    private void refreshChangeLogoVisibility() {
        boolean can = canEditMedia();
        btnChangeLogo.setVisibility(can ? View.VISIBLE : View.GONE);
        if (!showingPosts) {
            btnCreateEvent.setVisibility(can ? View.VISIBLE : View.GONE);
        }
    }

    private void bindStats(long memberCount) {
        String members = formatCount(memberCount) + " members";
        if (rankPercent > 0) {
            members += "  ·  " + getString(R.string.group_top_rank, rankPercent);
        }
        tvMembers.setText(members);
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000) {
            double m = count / 1_000_000.0;
            return m == Math.floor(m) ? String.format(Locale.US, "%.0fM", m)
                    : String.format(Locale.US, "%.1fM", m);
        }
        if (count >= 1_000) {
            double k = count / 1_000.0;
            return k == Math.floor(k) ? String.format(Locale.US, "%.0fk", k)
                    : String.format(Locale.US, "%.1fk", k);
        }
        return String.valueOf(count);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (Roles.isMentor() || Roles.canManageApp() || Roles.canManageSchoolUsers()) {
            getMenuInflater().inflate(R.menu.menu_community_detail, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_delete_group) {
            confirmDeleteGroup();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDeleteGroup() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.group_delete)
                .setMessage("Delete \"" + (communityName != null ? communityName : "this group")
                        + "\"? This cannot be undone.")
                .setPositiveButton(R.string.group_delete, (d, w) -> repository.deleteCommunity(communityId, success -> {
                    if (success) {
                        Toast.makeText(this, "Group deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Could not delete group", Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshMembership() {
        repository.isMember(communityId, member -> {
            isMember = member;
            btnJoin.setText(member ? "Joined" : "Join");
            postAdapter.setMember(member);
        });
    }

    private void toggleMembership() {
        btnJoin.setEnabled(false);
        if (isMember) {
            repository.leaveCommunity(communityId, success -> {
                btnJoin.setEnabled(true);
                if (success) {
                    isMember = false;
                    btnJoin.setText("Join");
                    postAdapter.setMember(false);
                }
            });
        } else {
            repository.joinCommunity(communityId, success -> {
                btnJoin.setEnabled(true);
                if (success) {
                    isMember = true;
                    btnJoin.setText("Joined");
                    postAdapter.setMember(true);
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        communityReg = repository.communityRef(communityId).addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) {
                if (snapshot != null && !snapshot.exists()) finish();
                return;
            }
            Community c = snapshot.toObject(Community.class);
            if (c == null) return;
            createdBy = c.getCreatedBy();
            communityName = c.getName();
            tvGroupName.setText(stripPrefix(communityName));
            tvDescription.setText(c.getDescription());
            tvDescription.setVisibility(TextUtils.isEmpty(c.getDescription()) ? View.GONE : View.VISIBLE);
            bindStats(c.getMemberCount());
            bindLogo(c.getIconUrl());
            bindBanner(c.getBannerUrl());
            bindMemberStack(c);
            postAdapter.setCommunityContext(communityId, communityName, c.getIconUrl(), isMember);
            refreshChangeLogoVisibility();
        });

        postsReg = repository.postsQuery(communityId).addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;
            List<CommunityPost> list = new ArrayList<>();
            snapshot.forEach(doc -> list.add(doc.toObject(CommunityPost.class)));
            postAdapter.submit(list);
            if (showingPosts) {
                tvNoPosts.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        eventsReg = repository.eventsQuery(communityId).addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;
            List<CommunityEvent> list = new ArrayList<>();
            snapshot.forEach(doc -> list.add(doc.toObject(CommunityEvent.class)));
            eventAdapter.submit(list);
            tvNoEvents.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });

        rankReg = repository.communitiesQuery().addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || snapshot.isEmpty()) return;
            int total = snapshot.size();
            int rank = 1;
            for (var doc : snapshot.getDocuments()) {
                if (communityId.equals(doc.getId())) break;
                rank++;
            }
            // Top X% by member-count order (already sorted DESC).
            rankPercent = Math.max(1, (int) Math.ceil(100.0 * rank / total));
            Community c = null;
            for (var doc : snapshot.getDocuments()) {
                if (communityId.equals(doc.getId())) {
                    c = doc.toObject(Community.class);
                    break;
                }
            }
            if (c != null) bindStats(c.getMemberCount());
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (communityReg != null) communityReg.remove();
        if (postsReg != null) postsReg.remove();
        if (eventsReg != null) eventsReg.remove();
        if (rankReg != null) rankReg.remove();
    }
}
