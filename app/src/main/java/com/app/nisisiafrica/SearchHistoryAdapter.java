package com.app.nisisiafrica;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {
    private List<String> history;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(String query);
    }

    public SearchHistoryAdapter(List<String> history, OnItemClickListener listener) {
        this.history = history;
        this.listener = listener;
    }

    public void updateList(List<String> newHistory) {
        this.history = newHistory;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String query = history.get(position);
        holder.text.setText(query);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(query));
    }

    @Override
    public int getItemCount() {
        return history.size();
    }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;
        ViewHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(android.R.id.text1);
        }
    }
}

