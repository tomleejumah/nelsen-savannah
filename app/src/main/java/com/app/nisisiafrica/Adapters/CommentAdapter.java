package com.app.nisisiafrica.Adapters;

import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.PostComment;
import com.app.nisisiafrica.data.Repository.CommunityRepository;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    public interface OnReply {
        void onReply(PostComment comment);
    }

    private final List<PostComment> items = new ArrayList<>();
    private final CommunityRepository repository;
    private final String communityId;
    private final String postId;
    private final OnReply onReply;

    private static final int LIKED_COLOR = Color.parseColor("#4F46E5");
    private static final int UNLIKED_COLOR = Color.parseColor("#9CA3AF");

    public CommentAdapter(CommunityRepository repository, String communityId, String postId, OnReply onReply) {
        this.repository = repository;
        this.communityId = communityId;
        this.postId = postId;
        this.onReply = onReply;
    }

    /**
     * Orders comments as top-level threads followed by their replies so the list
     * reads naturally even though it's a flat RecyclerView.
     */
    public void submit(List<PostComment> list) {
        items.clear();
        List<PostComment> topLevel = new ArrayList<>();
        for (PostComment c : list) {
            if (c.getParentId() == null || c.getParentId().isEmpty()) topLevel.add(c);
        }
        for (PostComment parent : topLevel) {
            items.add(parent);
            for (PostComment c : list) {
                if (parent.getId().equals(c.getParentId())) items.add(c);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PostComment c = items.get(position);
        boolean isReply = c.getParentId() != null && !c.getParentId().isEmpty();

        holder.root.setPadding(
                dp(holder.itemView, isReply ? 40 : 16), holder.root.getPaddingTop(),
                dp(holder.itemView, 16), holder.root.getPaddingBottom());

        holder.author.setText(c.getAuthorName());
        holder.body.setText(c.getBody());
        holder.likes.setText(String.valueOf(c.getLikeCount()));
        if (c.getCreatedAt() != null) {
            holder.time.setText(DateUtils.getRelativeTimeSpanString(c.getCreatedAt().getTime()));
        } else {
            holder.time.setText("");
        }

        // Reflect the current user's like state.
        holder.like.setColorFilter(UNLIKED_COLOR);
        repository.hasLikedComment(communityId, postId, c.getId(), liked ->
                holder.like.setColorFilter(liked ? LIKED_COLOR : UNLIKED_COLOR));

        holder.like.setOnClickListener(v ->
                repository.toggleCommentLike(communityId, postId, c.getId(), (success, nowLiked) -> {
                    if (!success) return;
                    holder.like.setColorFilter(nowLiked ? LIKED_COLOR : UNLIKED_COLOR);
                    long count = c.getLikeCount() + (nowLiked ? 1 : -1);
                    if (count < 0) count = 0;
                    c.setLikeCount(count);
                    holder.likes.setText(String.valueOf(count));
                }));

        // Replies attach to the top-level thread.
        PostComment replyTarget = isReply ? findParent(c.getParentId()) : c;
        holder.reply.setOnClickListener(v -> {
            if (onReply != null && replyTarget != null) onReply.onReply(replyTarget);
        });
    }

    private PostComment findParent(String parentId) {
        for (PostComment c : items) {
            if (c.getId().equals(parentId)) return c;
        }
        return null;
    }

    private int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View root;
        TextView author, body, time, likes, reply;
        ImageView like;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.commentRoot);
            author = itemView.findViewById(R.id.tvCommentAuthor);
            body = itemView.findViewById(R.id.tvCommentBody);
            time = itemView.findViewById(R.id.tvCommentTime);
            likes = itemView.findViewById(R.id.tvCommentLikes);
            reply = itemView.findViewById(R.id.btnReplyComment);
            like = itemView.findViewById(R.id.btnLikeComment);
        }
    }
}
