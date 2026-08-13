package com.app.nisisiafrica.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.Chatroom;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import kotlin.Unit;

/**
 * Lightweight, non-paged adapter used only while searching the chat list.
 */
public class ChatSearchAdapter extends RecyclerView.Adapter<ChatSearchAdapter.ViewHolder> {

    public interface OnClick {
        void onClick(Chatroom chatroom);
    }

    private final List<Chatroom> items = new ArrayList<>();
    private final OnClick listener;

    public ChatSearchAdapter(OnClick listener) {
        this.listener = listener;
    }

    public void submit(List<Chatroom> list) {
        items.clear();
        items.addAll(list);
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
        Chatroom room = items.get(position);
        String currentUserId = FirebaseAuth.getInstance().getUid();
        if (currentUserId == null) currentUserId = "";
        holder.name.setText(room.getOtherUserName(currentUserId));
        holder.lastMsg.setText(room.getLastMessage() != null ? room.getLastMessage() : "No messages yet");
        holder.unread.setVisibility(View.GONE);

        String otherUserId = room.getOtherUserId(currentUserId);
        if (otherUserId != null) {
            FirebaseRemoteDataSource.INSTANCE.getMentorData(otherUserId, mentor -> {
                Glide.with(holder.avatar.getContext())
                        .load(mentor != null ? mentor.getMentorImageUrl() : null)
                        .placeholder(R.drawable.ic_person)
                        .into(holder.avatar);
                return Unit.INSTANCE;
            }, e -> Unit.INSTANCE);
        } else {
            holder.avatar.setImageResource(R.drawable.ic_person);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(room);
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
