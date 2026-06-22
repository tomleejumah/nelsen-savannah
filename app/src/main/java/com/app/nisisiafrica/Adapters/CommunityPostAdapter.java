package com.app.nisisiafrica.Adapters;

import android.text.format.DateUtils;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.CommunityPost;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class CommunityPostAdapter extends RecyclerView.Adapter<CommunityPostAdapter.ViewHolder> {

    public interface OnPostClick {
        void onClick(CommunityPost post);
    }

    private final List<CommunityPost> items = new ArrayList<>();
    private final OnPostClick listener;

    public CommunityPostAdapter(OnPostClick listener) {
        this.listener = listener;
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
        holder.meta.setText(p.getAuthorName() + (time.isEmpty() ? "" : "  \u00b7  " + time));
        holder.title.setText(p.getTitle());
        holder.title.setVisibility(p.getTitle() == null || p.getTitle().isEmpty() ? View.GONE : View.VISIBLE);
        holder.body.setText(p.getBody());
        holder.votes.setText(String.valueOf(p.getUpvoteCount()));
        holder.comments.setText(String.valueOf(p.getCommentCount()));
        if (!TextUtils.isEmpty(p.getImageUrl())) {
            holder.image.setVisibility(View.VISIBLE);
            Glide.with(holder.image.getContext())
                    .load(p.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(holder.image);
        } else {
            holder.image.setVisibility(View.GONE);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView meta, title, body, votes, comments;
        ImageView image;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            meta = itemView.findViewById(R.id.tvPostMeta);
            title = itemView.findViewById(R.id.tvPostTitle);
            body = itemView.findViewById(R.id.tvPostBody);
            votes = itemView.findViewById(R.id.tvPostVotes);
            comments = itemView.findViewById(R.id.tvPostComments);
            image = itemView.findViewById(R.id.ivPostImage);
        }
    }
}
