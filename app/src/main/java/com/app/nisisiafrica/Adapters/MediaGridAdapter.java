package com.app.nisisiafrica.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.UserMedia;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class MediaGridAdapter extends RecyclerView.Adapter<MediaGridAdapter.MediaViewHolder> {

    private Context context;
    private List<UserMedia> mediaList;
    private OnMediaClickListener listener;

    public interface OnMediaClickListener {
        void onMediaClick(UserMedia media, int position);
    }

    public MediaGridAdapter(Context context, OnMediaClickListener listener) {
        this.context = context;
        this.mediaList = new ArrayList<>();
        this.listener = listener;
    }

    public void setMediaList(List<UserMedia> mediaList) {
        this.mediaList = mediaList;
        notifyDataSetChanged();
    }

    public void addMedia(List<UserMedia> newMedia) {
        int startPos = mediaList.size();
        mediaList.addAll(newMedia);
        notifyItemRangeInserted(startPos, newMedia.size());
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_media_grid, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        UserMedia media = mediaList.get(position);

        // Show type indicator
        String fileType = media.getFileType().toLowerCase();

        if (fileType.contains("pdf")) {
            // PDF - Show icon and filename
            media.getThumbnailUrl();
            if (!media.getThumbnailUrl().isEmpty()) {
                holder.mediaImage.setVisibility(View.VISIBLE);
                holder.pdfContainer.setVisibility(View.GONE);
                holder.videoIndicator.setVisibility(View.GONE);

                Glide.with(context)
                        .load(media.getThumbnailUrl())
                        .centerCrop()
                        .placeholder(R.drawable.ic_pdf)
                        .into(holder.mediaImage);
            } else {
                // Fallback to icon + filename
                holder.mediaImage.setVisibility(View.GONE);
                holder.pdfContainer.setVisibility(View.VISIBLE);
                holder.videoIndicator.setVisibility(View.GONE);
                holder.pdfFileName.setText(media.getFileName());
            }

        } else if (fileType.contains("mp4") || fileType.contains("video")) {
            // Video - Show thumbnail with play icon
            holder.mediaImage.setVisibility(View.VISIBLE);
            holder.pdfContainer.setVisibility(View.GONE);
            holder.videoIndicator.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(media.getMediaUrl())
                    .centerCrop()
                    .placeholder(R.drawable.ic_video_place_holder)
                    .into(holder.mediaImage);

        } else {
            // Image
            holder.mediaImage.setVisibility(View.VISIBLE);
            holder.pdfContainer.setVisibility(View.GONE);
            holder.videoIndicator.setVisibility(View.GONE);

            Glide.with(context)
                    .load(media.getMediaUrl())
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(holder.mediaImage);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMediaClick(media, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mediaList.size();
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView mediaImage;
        ImageView videoIndicator;
        CardView pdfContainer;
        TextView pdfFileName;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            mediaImage = itemView.findViewById(R.id.mediaImage);
            videoIndicator = itemView.findViewById(R.id.videoIndicator);
            pdfContainer = itemView.findViewById(R.id.pdfContainer);
            pdfFileName = itemView.findViewById(R.id.pdfFileName);
        }
    }
}
