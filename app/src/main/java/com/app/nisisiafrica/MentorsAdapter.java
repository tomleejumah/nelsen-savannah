package com.app.nisisiafrica;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Model.CourseItem;
import com.app.nisisiafrica.Model.MentorItem;
import com.bumptech.glide.Glide;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class MentorsAdapter extends RecyclerView.Adapter<MentorsAdapter.ViewHolder> {
        private final boolean isExpanded;
        private final List<MentorItem> mentorItems;
        private final Context mContext;

    public MentorsAdapter(boolean isExpanded, List<MentorItem> mentorItems, Context mContext) {
            this.isExpanded = isExpanded;
            this.mentorItems = mentorItems;
            this.mContext = mContext;
        }

        @NonNull
        @Override
        public MentorsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mentor ,parent,false);

            return new MentorsAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        }

        @Override
        public int getItemViewType(int position) {
            return super.getItemViewType(position);
        }

        @Override
        public int getItemCount() {
            return (isExpanded) ? mentorItems.size() : Math.min(mentorItems.size(),3);
        }
        public class ViewHolder extends RecyclerView.ViewHolder{
        //todo cleanup
            TextView tv_lessons, tv_duration, tv_course_title, tv_tutor_name;
            CircleImageView iv_tutor_avatar, likeBtn;
            ImageView iv_course_image;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tv_duration = itemView.findViewById(R.id.tv_duration);
                tv_lessons = itemView.findViewById(R.id.tv_lessons);
                tv_course_title = itemView.findViewById(R.id.tv_course_title);
                tv_tutor_name = itemView.findViewById(R.id.tv_tutor_name);
                iv_course_image = itemView.findViewById(R.id.iv_course_image);
                iv_tutor_avatar = itemView.findViewById(R.id.iv_tutor_avatar);
                likeBtn = itemView.findViewById(R.id.likeBtn);

            }

            void bind(CourseItem courseItem){
                tv_duration.setText(courseItem.getDuration());
                tv_lessons.setText(courseItem.getLessons());
                tv_course_title.setText(courseItem.getCourseTitle());
                tv_tutor_name.setText(courseItem.getTutorName());

                Glide.with(mContext).load(courseItem.getCourseImageUrl()).into(iv_course_image);
                Glide.with(mContext).load(courseItem.getTutorAvatarUrl()).into(iv_tutor_avatar);
            }
        }
}
