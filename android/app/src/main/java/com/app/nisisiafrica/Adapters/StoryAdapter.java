package com.app.nisisiafrica.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.Story;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Stories rail with a pinned "+ Add story" cell at index 0.
 * Story click positions are offset by 1 relative to the data list.
 */
public class StoryAdapter extends RecyclerView.Adapter<StoryAdapter.ViewHolder> {

    private static final int TYPE_ADD = 0;
    private static final int TYPE_STORY = 1;

    public interface OnStoryClick {
        void onStoryClick(int storyIndexInData);
    }

    public interface OnAddClick {
        void onAddClick();
    }

    private final Context context;
    private final List<Story> stories = new ArrayList<>();
    private final OnStoryClick listener;
    private OnAddClick addListener;

    public StoryAdapter(Context context, OnStoryClick listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setOnAddClick(OnAddClick addListener) {
        this.addListener = addListener;
    }

    public void submit(List<Story> newStories) {
        stories.clear();
        stories.addAll(newStories);
        notifyDataSetChanged();
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
            holder.itemView.setOnClickListener(v -> {
                if (addListener != null) addListener.onAddClick();
            });
            return;
        }

        int dataIndex = position - 1;
        Story story = stories.get(dataIndex);
        holder.company.setText(story.companyName != null ? story.companyName : "");
        if (story.logoUrl != null && !story.logoUrl.isEmpty()) {
            Glide.with(context)
                    .load(story.logoUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(holder.logo);
        } else if (story.mediaUrl != null && !story.mediaUrl.isEmpty()) {
            Glide.with(context)
                    .load(story.mediaUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(holder.logo);
        } else {
            holder.logo.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            int idx = holder.getBindingAdapterPosition() - 1;
            if (listener != null && idx >= 0) listener.onStoryClick(idx);
        });
    }

    @Override
    public int getItemCount() {
        return stories.size() + 1; // pinned + Add
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView logo;
        TextView company;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            logo = itemView.findViewById(R.id.storyLogo);
            company = itemView.findViewById(R.id.storyCompany);
        }
    }
}
