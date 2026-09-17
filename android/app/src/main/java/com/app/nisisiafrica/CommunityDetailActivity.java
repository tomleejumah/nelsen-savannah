package com.app.nisisiafrica;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.CommunityPostAdapter;
import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.data.Model.Community;
import com.app.nisisiafrica.data.Model.CommunityPost;
import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class CommunityDetailActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID = "extra_community_id";
    public static final String EXTRA_COMMUNITY_NAME = "extra_community_name";

    private final CommunityRepository repository = new CommunityRepository();
    private String communityId;
    private String communityName;
    private boolean isMember = false;

    private TextView tvMembers, tvDescription, tvNoPosts;
    private MaterialButton btnJoin;
    private CommunityPostAdapter postAdapter;
    private ListenerRegistration communityReg, postsReg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community_detail);

        communityId = getIntent().getStringExtra(EXTRA_COMMUNITY_ID);
        communityName = getIntent().getStringExtra(EXTRA_COMMUNITY_NAME);
        if (communityId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setTitle(communityName != null ? communityName : "Group");
        toolbar.setNavigationOnClickListener(v -> finish());
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvMembers = findViewById(R.id.tvMembers);
        tvDescription = findViewById(R.id.tvDescription);
        tvNoPosts = findViewById(R.id.tvNoPosts);
        btnJoin = findViewById(R.id.btnJoin);

        RecyclerView rvPosts = findViewById(R.id.rvPosts);
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        postAdapter = new CommunityPostAdapter(post -> {
            Intent intent = new Intent(this, PostDetailActivity.class);
            intent.putExtra(PostDetailActivity.EXTRA_COMMUNITY_ID, communityId);
            intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
            startActivity(intent);
        });
        rvPosts.setAdapter(postAdapter);

        btnJoin.setOnClickListener(v -> toggleMembership());

        FloatingActionButton fab = findViewById(R.id.fabCreatePost);
        fab.setOnClickListener(v -> {
            if (!isMember) {
                Toast.makeText(this, "Join this group to post", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, CreatePostActivity.class);
            intent.putExtra(CreatePostActivity.EXTRA_COMMUNITY_ID, communityId);
            startActivity(intent);
        });

        refreshMembership();
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
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_delete_group) {
            confirmDeleteGroup();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDeleteGroup() {
        new AlertDialog.Builder(this)
                .setTitle("Delete group")
                .setMessage("Delete \"" + (communityName != null ? communityName : "this group")
                        + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> repository.deleteCommunity(communityId, success -> {
                    if (success) {
                        Toast.makeText(this, "Group deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Could not delete group", Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshMembership() {
        repository.isMember(communityId, member -> {
            isMember = member;
            btnJoin.setText(member ? "Joined" : "Join");
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
                }
            });
        } else {
            repository.joinCommunity(communityId, success -> {
                btnJoin.setEnabled(true);
                if (success) {
                    isMember = true;
                    btnJoin.setText("Joined");
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
            tvDescription.setText(c.getDescription());
            tvMembers.setText(c.getMemberCount() + " members  \u00b7  " + c.getPostCount() + " posts");
        });

        postsReg = repository.postsQuery(communityId).addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;
            List<CommunityPost> list = new ArrayList<>();
            snapshot.forEach(doc -> list.add(doc.toObject(CommunityPost.class)));
            postAdapter.submit(list);
            tvNoPosts.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (communityReg != null) communityReg.remove();
        if (postsReg != null) postsReg.remove();
    }
}
