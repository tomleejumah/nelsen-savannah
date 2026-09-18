package com.app.nisisiafrica.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/** Picker for starting a DM with a mentor/tutor. */
public class MentorPickAdapter extends RecyclerView.Adapter<MentorPickAdapter.ViewHolder> {

    public interface OnPick {
        void onPick(MentorItem mentor);
    }

    private final List<MentorItem> items = new ArrayList<>();
    private final OnPick listener;

    public MentorPickAdapter(OnPick listener) {
        this.listener = listener;
    }

    public void submit(List<MentorItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_room, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MentorItem m = items.get(position);
        holder.name.setText(m.getMentorName() != null && !m.getMentorName().isEmpty()
                ? m.getMentorName() : "Mentor");
        String desc = m.getMentorDescription();
        holder.lastMsg.setText(desc != null && !desc.isEmpty() ? desc : "Mentor");
        holder.unread.setVisibility(View.GONE);
        Glide.with(holder.avatar.getContext())
                .load(m.getMentorImageUrl())
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(holder.avatar);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPick(m);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView avatar;
        TextView name, lastMsg, unread;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tvAvatar);
            name = itemView.findViewById(R.id.tvName);
            lastMsg = itemView.findViewById(R.id.tvLastMsg);
            unread = itemView.findViewById(R.id.tvUnread);
        }
    }
}
