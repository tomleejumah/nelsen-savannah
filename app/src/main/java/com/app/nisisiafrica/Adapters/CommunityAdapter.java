package com.app.nisisiafrica.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.Community;

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
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(c);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, desc, meta;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvCommunityName);
            desc = itemView.findViewById(R.id.tvCommunityDesc);
            meta = itemView.findViewById(R.id.tvCommunityMeta);
        }
    }
}
