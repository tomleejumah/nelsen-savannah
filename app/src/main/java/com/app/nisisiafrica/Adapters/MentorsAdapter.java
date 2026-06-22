package com.app.nisisiafrica.Adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import de.hdodenhof.circleimageview.CircleImageView;
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
        private CircleImageView ivTutorProfile;
        private TextView tvTutorName;
        private TextView tvTutorDescription;
        private TextView tvStudentsCount;
        private AppCompatButton btnBookNow;
        private OverlapImageListView overlapImage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivTutorProfile = itemView.findViewById(R.id.iv_tutor_profile);
            tvTutorName = itemView.findViewById(R.id.tv_tutor_name);
            tvTutorDescription = itemView.findViewById(R.id.tv_tutor_description);
            tvStudentsCount = itemView.findViewById(R.id.tv_students_count);
            btnBookNow = itemView.findViewById(R.id.btn_book_now);
            overlapImage = itemView.findViewById(R.id.overlapImage);
        }

        void bind(MentorItem mentorItem) {
            Glide.with(mContext).load(mentorItem.getMentorImageUrl()).into(ivTutorProfile);
            tvTutorName.setText(mentorItem.getMentorName());
            tvTutorDescription.setText(mentorItem.getMentorDescription());

            // Dynamic mentee count + recent mentee avatars (replaces the static "124+").
            List<String> studentImages = mentorItem.getStudentImages();
            OverlapImages.load(overlapImage, studentImages);
            String count = mentorItem.getStudentsCount();
            long n = 0;
            if (count != null && !count.isEmpty()) {
                try { n = Long.parseLong(count.replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) {}
            }
            if (n <= 0 && studentImages != null) n = studentImages.size();
            tvStudentsCount.setText(n > 0 ? n + " mentees" : "New mentor");

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
    }
}