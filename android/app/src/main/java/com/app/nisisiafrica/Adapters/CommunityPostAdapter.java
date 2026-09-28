package com.app.nisisiafrica.Adapters;

import android.content.Intent;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.CommunityPost;
import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class CommunityPostAdapter extends RecyclerView.Adapter<CommunityPostAdapter.ViewHolder> {

    public interface OnPostClick {
        void onClick(CommunityPost post);
    }

    private final List<CommunityPost> items = new ArrayList<>();
    private final OnPostClick listener;
    private final CommunityRepository repository = new CommunityRepository();
    private String communityId = "";
    private String communityName = "";
    private String communityIconUrl = "";
    private boolean isMember = false;

    public CommunityPostAdapter(OnPostClick listener) {
        this.listener = listener;
    }

    public void setCommunityContext(String communityId, String name, String iconUrl, boolean member) {
        this.communityId = communityId != null ? communityId : "";
        this.communityName = name != null ? name : "";
        if (this.communityName.startsWith("r/") || this.communityName.startsWith("R/")) {
            this.communityName = this.communityName.substring(2);
        }
        this.communityIconUrl = iconUrl != null ? iconUrl : "";
        this.isMember = member;
        notifyDataSetChanged();
    }

    public void setMember(boolean member) {
        this.isMember = member;
        notifyDataSetChanged();
    }

    public void submit(List<CommunityPost> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommunityPost p = items.get(position);
        String time = "";
        if (p.getCreatedAt() != null) {
            time = DateUtils.getRelativeTimeSpanString(p.getCreatedAt().getTime()).toString();
        }
        String author = p.getAuthorName() != null ? p.getAuthorName() : "";
        if (author.startsWith("u/") || author.startsWith("U/")) {
            author = author.substring(2);
        }
        holder.community.setText(communityName);
        holder.meta.setText(author + (time.isEmpty() ? "" : " · " + time));
        holder.joined.setText(isMember ? "Joined" : "Join");
        holder.title.setText(p.getTitle());
        holder.title.setVisibility(TextUtils.isEmpty(p.getTitle()) ? View.GONE : View.VISIBLE);
        holder.body.setText(p.getBody());
        holder.body.setVisibility(TextUtils.isEmpty(p.getBody()) ? View.GONE : View.VISIBLE);
        holder.votes.setText(String.valueOf(p.getUpvoteCount()));
        holder.comments.setText(String.valueOf(p.getCommentCount()));

        if (!TextUtils.isEmpty(communityIconUrl)) {
            Glide.with(holder.icon.getContext())
                    .load(communityIconUrl)
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .circleCrop()
                    .into(holder.icon);
        } else {
            holder.icon.setImageResource(R.mipmap.ic_launcher);
        }

        if (!TextUtils.isEmpty(p.getImageUrl())) {
            holder.image.setVisibility(View.VISIBLE);
            Glide.with(holder.image.getContext())
                    .load(p.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.image);
        } else {
            holder.image.setVisibility(View.GONE);
        }

        holder.upvote.setOnClickListener(v -> {
            if (TextUtils.isEmpty(communityId) || TextUtils.isEmpty(p.getId())) return;
            repository.toggleUpvote(communityId, p.getId(), (ok, nowUpvoted) -> {});
        });
        holder.voteBar.setOnClickListener(v -> holder.upvote.performClick());

        holder.share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            String text = (!TextUtils.isEmpty(p.getTitle()) ? p.getTitle() + "\n" : "")
                    + (p.getBody() != null ? p.getBody() : "");
            send.putExtra(Intent.EXTRA_TEXT, text.trim());
            v.getContext().startActivity(Intent.createChooser(send, v.getContext().getString(R.string.share)));
        });

        View.OnClickListener open = v -> {
            if (listener != null) listener.onClick(p);
        };
        holder.itemView.setOnClickListener(open);
        holder.commentBar.setOnClickListener(open);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView community, meta, title, body, votes, comments, joined;
        ShapeableImageView icon, image;
        ImageView upvote, share;
        View voteBar, commentBar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            community = itemView.findViewById(R.id.tvPostCommunity);
            meta = itemView.findViewById(R.id.tvPostMeta);
            title = itemView.findViewById(R.id.tvPostTitle);
            body = itemView.findViewById(R.id.tvPostBody);
            votes = itemView.findViewById(R.id.tvPostVotes);
            comments = itemView.findViewById(R.id.tvPostComments);
            joined = itemView.findViewById(R.id.btnPostJoined);
            icon = itemView.findViewById(R.id.imgPostCommunity);
            image = itemView.findViewById(R.id.ivPostImage);
            upvote = itemView.findViewById(R.id.btnUpvote);
            share = itemView.findViewById(R.id.btnSharePost);
            voteBar = itemView.findViewById(R.id.voteBar);
            commentBar = itemView.findViewById(R.id.commentBar);
        }
    }
}
