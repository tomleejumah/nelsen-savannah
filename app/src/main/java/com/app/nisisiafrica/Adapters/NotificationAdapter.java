package com.app.nisisiafrica.Adapters;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.R;
import com.app.nisisiafrica.data.Model.NotificationData;
import com.bumptech.glide.Glide;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationData notification);
    }

    private Context context;
    private List<NotificationData> notifications;
    private OnNotificationClickListener clickListener;

    public NotificationAdapter(Context context, List<NotificationData> notifications,
                               OnNotificationClickListener clickListener) {
        this.context = context;
        this.notifications = notifications;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationData notification = notifications.get(position);

        // Load sender avatar
        if (notification.getSenderAvatar() != null) {
            Glide.with(context)
                    .load(notification.getSenderAvatar())
                    .placeholder(R.drawable.ic_person)
                    .into(holder.imgSenderAvatar);
        } else {
            holder.imgSenderAvatar.setImageResource(R.drawable.ic_person);
        }

        // Load course thumbnail
        if (notification.getCourseImage() != null) {
            Glide.with(context)
                    .load(notification.getCourseImage())
                    .placeholder(R.drawable.default_course)
                    .into(holder.imgCourseThumbnail);
        } else {
            holder.imgCourseThumbnail.setImageResource(R.drawable.default_course);
        }

        // Build notification text: "John Doe liked your post React Course"
        String senderName = notification.getSenderName() != null ?
                notification.getSenderName() : "Someone";
        String courseName = notification.getCourseName() != null ?
                notification.getCourseName() : "your course";

        String notifText = senderName + " " + notification.getText() + " " + courseName;
        holder.tvNotificationText.setText(notifText);

        // Set timestamp
        String timeAgo = DateUtils.getRelativeTimeSpanString(
                notification.getTimestamp(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
        ).toString();
        holder.tvTimestamp.setText(timeAgo);

        // Show unread indicator
        if (!notification.getRead()) {
            holder.viewUnreadIndicator.setVisibility(View.VISIBLE);
            holder.itemView.setBackgroundColor(
                    context.getResources().getColor(R.color.unread_background));
        } else {
            holder.viewUnreadIndicator.setVisibility(View.GONE);
            holder.itemView.setBackgroundColor(
                    context.getResources().getColor(android.R.color.transparent));
        }

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            // Mark as read via API
            clickListener.onNotificationClick(notification);

            //todo Open course details
//            Intent intent = new Intent(context, CourseDetailActivity.class);
//            intent.putExtra("courseId", notification.getCourseID());
//            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgSenderAvatar;
        ImageView imgCourseThumbnail;
        TextView tvNotificationText;
        TextView tvTimestamp;
        View viewUnreadIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgSenderAvatar = itemView.findViewById(R.id.imgSenderAvatar);
            imgCourseThumbnail = itemView.findViewById(R.id.imgCourseThumbnail);
            tvNotificationText = itemView.findViewById(R.id.tvNotificationText);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            viewUnreadIndicator = itemView.findViewById(R.id.viewUnreadIndicator);
        }
    }
}