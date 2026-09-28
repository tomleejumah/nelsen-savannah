package com.app.nisisiafrica.Adapters;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.Community;
import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommunityAdapter extends RecyclerView.Adapter<CommunityAdapter.ViewHolder> {

    public interface OnCommunityClick {
        void onClick(Community community);
    }

    private final List<Community> all = new ArrayList<>();
    private final List<Community> items = new ArrayList<>();
    private final Map<String, Boolean> membership = new HashMap<>();
    private final OnCommunityClick listener;
    private final CommunityRepository repository = new CommunityRepository();
    private String query = "";

    public CommunityAdapter(OnCommunityClick listener) {
        this.listener = listener;
    }

    public void submit(List<Community> list) {
        all.clear();
        all.addAll(list);
        applyFilter();
    }

    public void filter(String q) {
        query = q != null ? q.trim() : "";
        applyFilter();
    }

    private void applyFilter() {
        items.clear();
        if (query.isEmpty()) {
            items.addAll(all);
        } else {
            String needle = query.toLowerCase(Locale.US);
            for (Community c : all) {
                String name = c.getName() != null ? c.getName() : "";
                if (name.toLowerCase(Locale.US).contains(needle)) items.add(c);
            }
        }
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
        String name = c.getName() != null ? c.getName() : "";
        if (name.startsWith("r/") || name.startsWith("R/")) name = name.substring(2);
        holder.name.setText(name);
        holder.desc.setVisibility(View.GONE);
        holder.meta.setText(formatMembers(c.getMemberCount()));

        if (!TextUtils.isEmpty(c.getIconUrl())) {
            Glide.with(holder.logo.getContext())
                    .load(c.getIconUrl())
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .circleCrop()
                    .into(holder.logo);
        } else {
            holder.logo.setImageResource(R.mipmap.ic_launcher);
        }

        bindMemberAvatars(holder, c);

        Boolean known = membership.get(c.getId());
        if (known != null) {
            holder.join.setText(known ? "Joined" : "Join");
        } else {
            holder.join.setText("…");
            final String id = c.getId();
            repository.isMember(id, member -> {
                membership.put(id, member);
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && id.equals(items.get(pos).getId())) {
                    holder.join.setText(member ? "Joined" : "Join");
                }
            });
        }

        holder.join.setOnClickListener(v -> {
            Boolean member = membership.get(c.getId());
            boolean currently = member != null && member;
            holder.join.setEnabled(false);
            if (currently) {
                repository.leaveCommunity(c.getId(), ok -> {
                    holder.join.setEnabled(true);
                    if (ok) {
                        membership.put(c.getId(), false);
                        holder.join.setText("Join");
                    }
                });
            } else {
                repository.joinCommunity(c.getId(), ok -> {
                    holder.join.setEnabled(true);
                    if (ok) {
                        membership.put(c.getId(), true);
                        holder.join.setText("Joined");
                    }
                });
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(c);
        });
    }

    private void bindMemberAvatars(ViewHolder holder, Community c) {
        List<String> avatars = c.getRecentMemberAvatars();
        List<String> urls = new ArrayList<>();
        if (avatars != null) {
            for (String url : avatars) {
                if (!TextUtils.isEmpty(url)) urls.add(url);
            }
        }
        long others = Math.max(0, c.getMemberCount() - urls.size());
        if (urls.isEmpty()) {
            holder.overlap.setVisibility(View.GONE);
            holder.others.setVisibility(View.GONE);
            return;
        }
        holder.overlap.setVisibility(View.VISIBLE);
        holder.overlap.setTag(urls);
        if (others > 0) {
            holder.others.setVisibility(View.VISIBLE);
            holder.others.setText(holder.itemView.getContext()
                    .getString(R.string.group_others_count, (int) Math.min(others, Integer.MAX_VALUE)));
        } else {
            holder.others.setVisibility(View.GONE);
        }
        final ArrayList<Bitmap> bitmaps = new ArrayList<>();
        final int total = Math.min(urls.size(), 3);
        for (int i = 0; i < total; i++) {
            Glide.with(holder.overlap.getContext())
                    .asBitmap()
                    .load(urls.get(i))
                    .apply(RequestOptions.circleCropTransform())
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                                    @Nullable Transition<? super Bitmap> transition) {
                            bitmaps.add(resource);
                            if (bitmaps.size() == total && urls.equals(holder.overlap.getTag())) {
                                holder.overlap.setImageList(bitmaps);
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {}
                    });
        }
    }

    private static String formatMembers(long count) {
        if (count >= 1_000_000) {
            double m = count / 1_000_000.0;
            return (m == Math.floor(m) ? String.format(Locale.US, "%.0fM", m)
                    : String.format(Locale.US, "%.1fM", m)) + " members";
        }
        if (count >= 1_000) {
            double k = count / 1_000.0;
            return (k == Math.floor(k) ? String.format(Locale.US, "%.0fk", k)
                    : String.format(Locale.US, "%.1fk", k)) + " members";
        }
        return count + " members";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, desc, meta, join, others;
        OverlapImageListView overlap;
        ImageView logo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvCommunityName);
            desc = itemView.findViewById(R.id.tvCommunityDesc);
            meta = itemView.findViewById(R.id.tvCommunityMeta);
            join = itemView.findViewById(R.id.btnCommunityJoin);
            others = itemView.findViewById(R.id.tvOthersCount);
            overlap = itemView.findViewById(R.id.overlapImage);
            logo = itemView.findViewById(R.id.imgCommunityLogo);
        }
    }
}
