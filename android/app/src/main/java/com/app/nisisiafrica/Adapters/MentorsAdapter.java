package com.app.nisisiafrica.Adapters;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.BookMentor;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.OverlapImages;
import com.bumptech.glide.Glide;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.List;
import java.util.Locale;

public class MentorsAdapter extends PagingDataAdapter<MentorItem, MentorsAdapter.ViewHolder> {
    private boolean isExpanded;
    private Context mContext;
    private static final String TAG = "MentorsAdapter";

    public MentorsAdapter(boolean isExpanded, Context mContext) {
        super(DIFF_CALLBACK);
        this.isExpanded = isExpanded;
        this.mContext = mContext;
    }

    private static final DiffUtil.ItemCallback<MentorItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<MentorItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull MentorItem oldItem, @NonNull MentorItem newItem) {
            return oldItem.getMentorId().equals(newItem.getMentorId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull MentorItem oldItem, @NonNull MentorItem newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public MentorsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mentor, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MentorItem item = getItem(position);
        if (item != null) {
            holder.bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return isExpanded ? super.getItemCount() : Math.min(super.getItemCount(), 3);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivTutorProfile;
        private TextView tvMentorInitials;
        private TextView tvTutorName;
        private TextView tvTutorDescription;
        private TextView tvStudentsCount;
        private TextView tvMentorTag;
        private AppCompatButton btnBookNow;
        private OverlapImageListView overlapImage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTutorProfile = itemView.findViewById(R.id.iv_tutor_profile);
            tvMentorInitials = itemView.findViewById(R.id.tvMentorInitials);
            tvTutorName = itemView.findViewById(R.id.tv_tutor_name);
            tvTutorDescription = itemView.findViewById(R.id.tv_tutor_description);
            tvStudentsCount = itemView.findViewById(R.id.tv_students_count);
            tvMentorTag = itemView.findViewById(R.id.tvMentorTag);
            btnBookNow = itemView.findViewById(R.id.btn_book_now);
            overlapImage = itemView.findViewById(R.id.overlapImage);
        }

        void bind(MentorItem mentorItem) {
            tvTutorName.setText(mentorItem.getMentorName());
            tvTutorDescription.setText(mentorItem.getMentorDescription());
            bindAvatar(mentorItem);

            List<String> studentImages = mentorItem.getStudentImages();
            if (overlapImage != null && overlapImage.getVisibility() == View.VISIBLE) {
                OverlapImages.load(overlapImage, studentImages);
            }
            String count = mentorItem.getStudentsCount();
            long n = 0;
            if (count != null && !count.isEmpty()) {
                try { n = Long.parseLong(count.replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) {}
            }
            if (n <= 0 && studentImages != null) n = studentImages.size();

            Double rating = mentorItem.getAverageRating();
            boolean hasHistory = (rating != null && rating > 0) || n > 0;
            if (hasHistory && rating != null && rating > 0) {
                tvMentorTag.setText(String.format(Locale.getDefault(), "★ %.1f · %d students", rating, n));
            } else if (hasHistory) {
                tvMentorTag.setText(n + " students");
            } else {
                tvMentorTag.setText("New mentor");
            }
            if (tvStudentsCount != null) tvStudentsCount.setText("");

            boolean hideBook = !com.app.nisisiafrica.Utils.Roles.browsesMentors();
            btnBookNow.setVisibility(hideBook ? View.GONE : View.VISIBLE);

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(mContext, ProfileActivity.class);
                intent.putExtra(Constants.IS_MENTOR, true);
                intent.putExtra(Constants.MENTOR_ID, mentorItem.getMentorId());
                Log.d(TAG, "onBindViewHolder: " + mentorItem.getMentorId());
                mContext.startActivity(intent);
            });

            btnBookNow.setOnClickListener(v -> {
                Intent intent = new Intent(mContext, BookMentor.class);
                intent.putExtra(Constants.MENTOR_ID, mentorItem.getMentorId());
                intent.putExtra(Constants.MENTOR_NAME, mentorItem.getMentorName());
                mContext.startActivity(intent);
            });
        }

        private void bindAvatar(MentorItem mentorItem) {
            String url = mentorItem.getMentorImageUrl();
            String initials = initialsFor(mentorItem.getMentorName());
            tvMentorInitials.setText(initials);
            if (!TextUtils.isEmpty(url) && !"default".equals(url)) {
                ivTutorProfile.setVisibility(View.VISIBLE);
                tvMentorInitials.setVisibility(View.GONE);
                ivTutorProfile.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                ivTutorProfile.setClipToOutline(true);
                Glide.with(mContext).load(url).centerCrop().into(ivTutorProfile);
            } else {
                ivTutorProfile.setVisibility(View.GONE);
                tvMentorInitials.setVisibility(View.VISIBLE);
            }
        }

        private String initialsFor(String name) {
            if (TextUtils.isEmpty(name)) return "?";
            String[] parts = name.trim().split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.getDefault());
            }
            String a = parts[0].substring(0, 1);
            String b = parts[parts.length - 1].substring(0, 1);
            return (a + b).toUpperCase(Locale.getDefault());
        }
    }
}
