package com.app.nisisiafrica.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.StoryViewsStore;
import com.app.nisisiafrica.Views.StoryRingView;
import com.app.nisisiafrica.data.Model.StoryBucket;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Stories rail: pinned "+ Add", then one cell per owner (multi-status compacted).
 */
public class StoryAdapter extends RecyclerView.Adapter<StoryAdapter.ViewHolder> {

    private static final int TYPE_ADD = 0;
    private static final int TYPE_STORY = 1;

    public interface OnBucketClick {
        void onBucketClick(int bucketIndex);
    }

    public interface OnAddClick {
        void onAddClick();
    }

    private final Context context;
    private final List<StoryBucket> buckets = new ArrayList<>();
    private final OnBucketClick listener;
    private OnAddClick addListener;
    private Set<String> seenIds;

    public StoryAdapter(Context context, OnBucketClick listener) {
        this.context = context;
        this.listener = listener;
        this.seenIds = StoryViewsStore.seenIds(context);
    }

    public void setOnAddClick(OnAddClick addListener) {
        this.addListener = addListener;
    }

    public void submit(List<StoryBucket> newBuckets) {
        buckets.clear();
        if (newBuckets != null) buckets.addAll(newBuckets);
        seenIds = StoryViewsStore.seenIds(context);
        notifyDataSetChanged();
    }

    public void refreshSeenState() {
        seenIds = StoryViewsStore.seenIds(context);
        notifyDataSetChanged();
    }

    public List<StoryBucket> getBuckets() {
        return buckets;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_ADD : TYPE_STORY;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_story, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ADD) {
            holder.company.setText("+ Add");
            holder.logo.setImageResource(R.drawable.ic_add_circle);
            holder.ring.setSegmentSeen(new boolean[]{true});
            holder.itemView.setOnClickListener(v -> {
                if (addListener != null) addListener.onAddClick();
            });
            return;
        }

        int dataIndex = position - 1;
        StoryBucket bucket = buckets.get(dataIndex);
        holder.company.setText(bucket.label);
        String cover = bucket.coverUrl();
        if (cover != null && !cover.isEmpty()) {
            Glide.with(context)
                    .load(cover)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(holder.logo);
        } else {
            holder.logo.setImageResource(R.drawable.ic_image_placeholder);
        }

        // One ring segment per story — white if viewed, maroon if not.
        boolean[] seen = new boolean[bucket.stories.size()];
        for (int i = 0; i < bucket.stories.size(); i++) {
            String id = bucket.stories.get(i).storyId;
            seen[i] = id != null && seenIds != null && seenIds.contains(id);
        }
        holder.ring.setSegmentSeen(seen);

        holder.itemView.setOnClickListener(v -> {
            int idx = holder.getBindingAdapterPosition() - 1;
            if (listener != null && idx >= 0) listener.onBucketClick(idx);
        });
    }

    @Override
    public int getItemCount() {
        return buckets.size() + 1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        StoryRingView ring;
        CircleImageView logo;
        TextView company;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ring = itemView.findViewById(R.id.storyRing);
            logo = itemView.findViewById(R.id.storyLogo);
            company = itemView.findViewById(R.id.storyCompany);
        }
    }
}
