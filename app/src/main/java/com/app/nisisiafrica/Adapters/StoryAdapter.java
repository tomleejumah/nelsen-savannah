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

public class StoryAdapter extends RecyclerView.Adapter<StoryAdapter.ViewHolder> {

    public interface OnStoryClick {
        void onStoryClick(int position);
    }

    private final Context context;
    private final List<Story> stories = new ArrayList<>();
    private final OnStoryClick listener;

    public StoryAdapter(Context context, OnStoryClick listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submit(List<Story> newStories) {
        stories.clear();
        stories.addAll(newStories);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_story, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Story story = stories.get(position);
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
            if (listener != null) listener.onStoryClick(holder.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return stories.size();
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
