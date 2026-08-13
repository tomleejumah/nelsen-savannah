package com.app.nisisiafrica.Adapters;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.Community;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.ArrayList;
import java.util.List;

public class CommunityAdapter extends RecyclerView.Adapter<CommunityAdapter.ViewHolder> {

    public interface OnCommunityClick {
        void onClick(Community community);
    }

    private final List<Community> items = new ArrayList<>();
    private final OnCommunityClick listener;

    public CommunityAdapter(OnCommunityClick listener) {
        this.listener = listener;
    }

    public void submit(List<Community> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_community, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Community c = items.get(position);
        holder.name.setText(c.getName());
        holder.desc.setText(c.getDescription());
        holder.meta.setText(c.getMemberCount() + " members  \u00b7  " + c.getPostCount() + " posts");

        bindMemberAvatars(holder.overlap, c.getRecentMemberAvatars());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(c);
        });
    }

    /** Loads the most recent joiners' avatars into the overlapping circles view. */
    private void bindMemberAvatars(OverlapImageListView overlap, List<String> avatars) {
        overlap.setTag(avatars);
        List<String> urls = new ArrayList<>();
        if (avatars != null) {
            for (String url : avatars) {
                if (url != null && !url.isEmpty()) urls.add(url);
            }
        }
        if (urls.isEmpty()) {
            overlap.setVisibility(View.GONE);
            return;
        }
        overlap.setVisibility(View.VISIBLE);
        final ArrayList<Bitmap> bitmaps = new ArrayList<>();
        final int total = Math.min(urls.size(), 3);
        for (int i = 0; i < total; i++) {
            Glide.with(overlap.getContext())
                    .asBitmap()
                    .load(urls.get(i))
                    .apply(RequestOptions.circleCropTransform())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                                    @Nullable Transition<? super Bitmap> transition) {
                            bitmaps.add(resource);
                            if (bitmaps.size() == total && overlap.getTag() == avatars) {
                                overlap.setImageList(bitmaps);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, desc, meta;
        OverlapImageListView overlap;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvCommunityName);
            desc = itemView.findViewById(R.id.tvCommunityDesc);
            meta = itemView.findViewById(R.id.tvCommunityMeta);
            overlap = itemView.findViewById(R.id.overlapImage);
        }
    }
}
