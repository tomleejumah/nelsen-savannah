package com.app.nisisiafrica.Adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.Community;
import com.app.nisisiafrica.data.Repository.CommunityRepository;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommunityAdapter extends RecyclerView.Adapter<CommunityAdapter.ViewHolder> {

    public interface OnCommunityClick {
        void onClick(Community community);
    }

    private final List<Community> items = new ArrayList<>();
    private final Map<String, Boolean> membership = new HashMap<>();
    private final OnCommunityClick listener;
    private final CommunityRepository repository = new CommunityRepository();
    private boolean editMode = false;

    public CommunityAdapter(OnCommunityClick listener) {
        this.listener = listener;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        notifyDataSetChanged();
    }

    public boolean isEditMode() {
        return editMode;
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
        String name = c.getName() != null ? c.getName() : "";
        if (name.startsWith("r/") || name.startsWith("R/")) {
            name = name.substring(2);
        }
        holder.name.setText(name);
        holder.desc.setVisibility(View.GONE);
        holder.meta.setText(formatMembers(c.getMemberCount()));
        if (holder.overlap != null) holder.overlap.setVisibility(View.GONE);

        if (!TextUtils.isEmpty(c.getIconUrl())) {
            Glide.with(holder.logo.getContext())
                    .load(c.getIconUrl())
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .centerCrop()
                    .into(holder.logo);
        } else {
            holder.logo.setImageResource(R.mipmap.ic_launcher);
        }

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
        TextView name, desc, meta, join;
        View overlap;
        ImageView logo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvCommunityName);
            desc = itemView.findViewById(R.id.tvCommunityDesc);
            meta = itemView.findViewById(R.id.tvCommunityMeta);
            join = itemView.findViewById(R.id.btnCommunityJoin);
            overlap = itemView.findViewById(R.id.overlapImage);
            logo = itemView.findViewById(R.id.imgCommunityLogo);
        }
    }
}
