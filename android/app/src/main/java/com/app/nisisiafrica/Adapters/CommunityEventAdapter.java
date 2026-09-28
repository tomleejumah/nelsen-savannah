package com.app.nisisiafrica.Adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.CommunityEvent;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CommunityEventAdapter extends RecyclerView.Adapter<CommunityEventAdapter.ViewHolder> {

    private final List<CommunityEvent> items = new ArrayList<>();

    public void submit(List<CommunityEvent> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_community_event, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommunityEvent e = items.get(position);
        holder.title.setText(e.getTitle());
        if (e.getStartsAt() > 0) {
            holder.when.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(e.getStartsAt())));
        } else {
            holder.when.setText("");
        }
        String place = "online".equalsIgnoreCase(e.getMode()) ? "Online" : e.getLocation();
        String meta = "";
        if (!TextUtils.isEmpty(place)) meta = place;
        if (!TextUtils.isEmpty(e.getCreatedByName())) {
            meta += (meta.isEmpty() ? "" : " · ") + e.getCreatedByName();
        }
        holder.meta.setText(meta);
        holder.meta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        holder.body.setText(e.getDescription());
        holder.body.setVisibility(TextUtils.isEmpty(e.getDescription()) ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, when, meta, body;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvEventTitle);
            when = itemView.findViewById(R.id.tvEventWhen);
            meta = itemView.findViewById(R.id.tvEventMeta);
            body = itemView.findViewById(R.id.tvEventBody);
        }
    }
}
