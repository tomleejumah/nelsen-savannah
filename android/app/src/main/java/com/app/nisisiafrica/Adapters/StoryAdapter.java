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
import com.app.nisisiafrica.Views.RingStateView;
import com.app.nisisiafrica.data.Model.StoryBucket;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Stories rail: pinned "+ Add", then Field Journal stamps per owner.
 */
public class StoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_ADD) {
            return new AddVH(inflater.inflate(R.layout.item_story_add, parent, false));
        }
        return new StoryVH(inflater.inflate(R.layout.item_story, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddVH) {
            holder.itemView.setOnClickListener(v -> {
                if (addListener != null) addListener.onAddClick();
            });
            return;
        }

        StoryVH h = (StoryVH) holder;
        int dataIndex = position - 1;
        StoryBucket bucket = buckets.get(dataIndex);
        h.company.setText(bucket.label);
        String cover = bucket.coverUrl();
        if (cover != null && !cover.isEmpty()) {
            Glide.with(context)
                    .load(cover)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(h.logo);
        } else {
            h.logo.setImageResource(R.drawable.ic_image_placeholder);
        }

        boolean[] seen = new boolean[bucket.stories.size()];
        for (int i = 0; i < bucket.stories.size(); i++) {
            String id = bucket.stories.get(i).storyId;
            seen[i] = id != null && seenIds != null && seenIds.contains(id);
        }
        h.ring.setSegmentSeen(seen);

        h.itemView.setOnClickListener(v -> {
            int idx = h.getBindingAdapterPosition() - 1;
            if (listener != null && idx >= 0) listener.onBucketClick(idx);
        });
    }

    @Override
    public int getItemCount() {
        return buckets.size() + 1;
    }

    static class AddVH extends RecyclerView.ViewHolder {
        AddVH(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class StoryVH extends RecyclerView.ViewHolder {
        final RingStateView ring;
        final CircleImageView logo;
        final TextView company;

        StoryVH(@NonNull View itemView) {
            super(itemView);
            ring = itemView.findViewById(R.id.storyRing);
            logo = itemView.findViewById(R.id.storyLogo);
            company = itemView.findViewById(R.id.storyCompany);
        }
    }
}
