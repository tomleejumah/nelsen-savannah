package com.app.nisisiafrica.Adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.EditProfileActivity;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.ViewAllActivity;
import com.bumptech.glide.Glide;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CoursesAdapter extends PagingDataAdapter<CourseItem, RecyclerView.ViewHolder> {
    private static final int TYPE_COMPACT = 0;
    private static final int TYPE_EXPANDED = 1;
    private static final int TYPE_UPDATE_PROFILE = 2;
    private final Context mContext;

    private static final DiffUtil.ItemCallback<CourseItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<CourseItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull CourseItem oldItem, @NonNull CourseItem newItem) {
            return oldItem.getCourseLink().equals(newItem.getCourseLink()); // Use unique identifier
        }

        @Override
        public boolean areContentsTheSame(@NonNull CourseItem oldItem, @NonNull CourseItem newItem) {
            return oldItem.equals(newItem);
        }
    };

    public CoursesAdapter(Context mContext) {
        super(DIFF_CALLBACK);
        this.mContext = mContext;
    }

    @Override
    public int getItemViewType(int position) {
        if (mContext instanceof MainActivity) {
            return TYPE_COMPACT;
        } else if (mContext instanceof ViewAllActivity) {
            return TYPE_EXPANDED;
        } else if (mContext instanceof EditProfileActivity) {
            return TYPE_UPDATE_PROFILE;
        }
        return -1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_COMPACT) {
            View view = inflater.inflate(R.layout.item_course, parent, false);
            return new CompactViewHolder(view);
        } else if (viewType == TYPE_EXPANDED) {
            View view = inflater.inflate(R.layout.item_course_flex, parent, false);
            return new CompactViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_update_course, parent, false);
            return new UpdateProfileViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CourseItem item = getItem(position);
        if (item == null) return;

        if (holder instanceof CompactViewHolder) {
            ((CompactViewHolder) holder).bind(item);

            holder.itemView.setOnClickListener(v -> {
                String url = item.getCourseLink();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                mContext.startActivity(browserIntent);
            });
        } else if (holder instanceof UpdateProfileViewHolder) {
            ((UpdateProfileViewHolder) holder).bind(item);
            holder.itemView.setOnClickListener(v -> {
                Toast.makeText(mContext, "working on update course feature", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public int getItemCount() {
        if (super.getItemCount() == 0) return 0;
        int viewType = getItemViewType(0);
        return (viewType == TYPE_COMPACT)
                ? Math.min(super.getItemCount(), 5)
                : super.getItemCount();
    }

    class CompactViewHolder extends RecyclerView.ViewHolder {
        TextView tv_lessons, tv_duration, tv_course_title, tv_tutor_name;
        CircleImageView iv_tutor_avatar, likeBtn;
        ImageView iv_course_image;

        public CompactViewHolder(@NonNull View itemView) {
            super(itemView);
            tv_duration = itemView.findViewById(R.id.tv_duration);
            tv_lessons = itemView.findViewById(R.id.tv_lessons);
            tv_course_title = itemView.findViewById(R.id.tv_course_title);
            tv_tutor_name = itemView.findViewById(R.id.tv_tutor_name);
            iv_course_image = itemView.findViewById(R.id.iv_course_image);
            iv_tutor_avatar = itemView.findViewById(R.id.iv_tutor_avatar);
            likeBtn = itemView.findViewById(R.id.likeBtn);
        }

        void bind(CourseItem courseItem) {
            tv_duration.setText(courseItem.getDuration() + " Hours");
            tv_lessons.setText(courseItem.getLessons() + " Lessons");
            tv_course_title.setText(courseItem.getCourseTitle());
            tv_tutor_name.setText(courseItem.getTutorName());

            Glide.with(mContext).load(courseItem.getCourseImageUrl()).into(iv_course_image);
            Glide.with(mContext).load(courseItem.getTutorAvatarUrl()).into(iv_tutor_avatar);
        }
    }

    class UpdateProfileViewHolder extends RecyclerView.ViewHolder {
        TextView courseLink, courseTitle, lessons, duration, courseImageUrl;

        public UpdateProfileViewHolder(@NonNull View view) {
            super(view);
            courseLink = view.findViewById(R.id.courseLink);
            courseTitle = view.findViewById(R.id.courseTitle);
            lessons = view.findViewById(R.id.lessons);
            duration = view.findViewById(R.id.duration);
            courseImageUrl = view.findViewById(R.id.courseImageUrl);
        }

        void bind(CourseItem courseItem) {
            courseLink.setText(courseItem.getCourseLink());
            courseImageUrl.setText(courseItem.getCourseImageUrl());
            courseTitle.setText(courseItem.getCourseTitle());
            lessons.setText(courseItem.getLessons());
            duration.setText(courseItem.getDuration());
        }
    }
}