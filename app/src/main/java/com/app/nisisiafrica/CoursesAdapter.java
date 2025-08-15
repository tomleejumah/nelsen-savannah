package com.app.nisisiafrica;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.app.nisisiafrica.Model.CourseItem;
import com.bumptech.glide.Glide;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CoursesAdapter extends RecyclerView.Adapter<CoursesAdapter.ViewHolder> {
    private final boolean isExpanded;
    private final List<CourseItem> courseItems;
    private final Context mContext;

    public CoursesAdapter(boolean isExpanded, List<CourseItem> courseItems, Context mContext) {
        this.isExpanded = isExpanded;
        this.courseItems = courseItems;
        this.mContext = mContext;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(parent.getContext()).inflate(
                isExpanded ? com.app.nisisiafrica.R.layout.card_course_item : R.layout.card_course_item_flex,parent,false);

        return new CoursesAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(courseItems.get(position));

        holder.itemView.setOnClickListener(v -> {
            //todo Add webview->
            String url = courseItems.get(position).getCourseLink();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            mContext.startActivity(browserIntent);

        });
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    @Override
    public int getItemCount() {
        return (isExpanded) ? courseItems.size() : Math.min(courseItems.size(),5);
//        return mJobItems.size();
    }
    public class ViewHolder extends RecyclerView.ViewHolder{
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