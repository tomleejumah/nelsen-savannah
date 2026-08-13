package com.app.nisisiafrica;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.CommentAdapter;
import com.app.nisisiafrica.data.Model.CommunityPost;
import com.app.nisisiafrica.data.Model.PostComment;
import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.app.nisisiafrica.data.remote.NotificationSender;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class PostDetailActivity extends AppCompatActivity {

    public static final String EXTRA_COMMUNITY_ID = "extra_community_id";
    public static final String EXTRA_POST_ID = "extra_post_id";

    private final CommunityRepository repository = new CommunityRepository();
    private String communityId, postId;
    private String postAuthorId;
    private boolean hasUpvoted = false;

    private TextView tvMeta, tvTitle, tvBody, tvNoComments, tvReplyingTo;
    private MaterialButton btnUpvote;
    private ImageView ivPostImage;
    private LinearLayout replyBar;
    private CommentAdapter commentAdapter;
    private ListenerRegistration postReg, commentsReg;
    private String replyingToCommentId = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        communityId = getIntent().getStringExtra(EXTRA_COMMUNITY_ID);
        postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (communityId == null || postId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvMeta = findViewById(R.id.tvMeta);
        tvTitle = findViewById(R.id.tvTitle);
        tvBody = findViewById(R.id.tvBody);
        tvNoComments = findViewById(R.id.tvNoComments);
        btnUpvote = findViewById(R.id.btnUpvote);
        ivPostImage = findViewById(R.id.ivPostImage);
        replyBar = findViewById(R.id.replyBar);
        tvReplyingTo = findViewById(R.id.tvReplyingTo);

        RecyclerView rvComments = findViewById(R.id.rvComments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        commentAdapter = new CommentAdapter(repository, communityId, postId, comment -> {
            replyingToCommentId = comment.getId();
            tvReplyingTo.setText("Replying to " + comment.getAuthorName());
            replyBar.setVisibility(View.VISIBLE);
        });
        rvComments.setAdapter(commentAdapter);

        findViewById(R.id.btnCancelReply).setOnClickListener(v -> clearReply());

        btnUpvote.setOnClickListener(v -> {
            btnUpvote.setEnabled(false);
            repository.toggleUpvote(communityId, postId, (success, nowUpvoted) -> {
                btnUpvote.setEnabled(true);
                if (success) {
                    hasUpvoted = nowUpvoted;
                    applyUpvoteStyle();
                    if (nowUpvoted) {
                        NotificationSender.like(postAuthorId, postId, "liked your post");
                    }
                }
            });
        });

        EditText etComment = findViewById(R.id.etComment);
        ImageButton btnSend = findViewById(R.id.btnSendComment);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String authorName = user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()
                ? user.getDisplayName() : "User";

        btnSend.setOnClickListener(v -> {
            String body = etComment.getText().toString().trim();
            if (TextUtils.isEmpty(body)) return;
            btnSend.setEnabled(false);
            String parentId = replyingToCommentId;
            repository.addComment(communityId, postId, body, authorName, parentId, (success, idOrError) -> {
                btnSend.setEnabled(true);
                if (success) {
                    etComment.setText("");
                    clearReply();
                    NotificationSender.comment(postAuthorId, postId, "commented on your post", body);
                }
            });
        });

        repository.hasUpvoted(communityId, postId, upvoted -> {
            hasUpvoted = upvoted;
            applyUpvoteStyle();
        });
    }

    private void clearReply() {
        replyingToCommentId = "";
        replyBar.setVisibility(View.GONE);
    }

    private void applyUpvoteStyle() {
        int color = hasUpvoted ? 0xFF4F46E5 : 0xFF6B7280;
        btnUpvote.setTextColor(color);
        btnUpvote.setIconTint(android.content.res.ColorStateList.valueOf(color));
    }

    @Override
    protected void onStart() {
        super.onStart();
        postReg = repository.postRef(communityId, postId).addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;
            CommunityPost p = snapshot.toObject(CommunityPost.class);
            if (p == null) return;
            postAuthorId = p.getAuthorId();
            String time = p.getCreatedAt() != null
                    ? DateUtils.getRelativeTimeSpanString(p.getCreatedAt().getTime()).toString() : "";
            tvMeta.setText(p.getAuthorName() + (time.isEmpty() ? "" : "  \u00b7  " + time));
            tvTitle.setText(p.getTitle());
            tvTitle.setVisibility(TextUtils.isEmpty(p.getTitle()) ? View.GONE : View.VISIBLE);
            tvBody.setText(p.getBody());
            btnUpvote.setText(String.valueOf(p.getUpvoteCount()));
            if (!TextUtils.isEmpty(p.getImageUrl())) {
                ivPostImage.setVisibility(View.VISIBLE);
                Glide.with(this).load(p.getImageUrl())
                        .placeholder(R.drawable.ic_image_placeholder).into(ivPostImage);
            } else {
                ivPostImage.setVisibility(View.GONE);
            }
        });

        commentsReg = repository.commentsQuery(communityId, postId).addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;
            List<PostComment> list = new ArrayList<>();
            snapshot.forEach(doc -> list.add(doc.toObject(PostComment.class)));
            commentAdapter.submit(list);
            tvNoComments.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (postReg != null) postReg.remove();
        if (commentsReg != null) commentsReg.remove();
    }
}
