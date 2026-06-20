package com.app.nisisiafrica.Adapters;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.PostComment;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    private final List<PostComment> items = new ArrayList<>();

    public void submit(List<PostComment> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PostComment c = items.get(position);
        holder.author.setText(c.getAuthorName());
        holder.body.setText(c.getBody());
        if (c.getCreatedAt() != null) {
            holder.time.setText(DateUtils.getRelativeTimeSpanString(c.getCreatedAt().getTime()));
        } else {
            holder.time.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView author, body, time;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            author = itemView.findViewById(R.id.tvCommentAuthor);
            body = itemView.findViewById(R.id.tvCommentBody);
            time = itemView.findViewById(R.id.tvCommentTime);
        }
    }
}
