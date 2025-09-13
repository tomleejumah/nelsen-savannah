package com.app.nisisiafrica.Adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.BookMentor;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.Model.MentorItem;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.R;
import com.bumptech.glide.Glide;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class MentorsAdapter extends RecyclerView.Adapter<MentorsAdapter.ViewHolder> {
    private  boolean isExpanded;
    private  List<MentorItem> mentorItems;
    private  Context mContext;

    public MentorsAdapter(boolean isExpanded,Context mContext) {
        this.isExpanded = isExpanded;
        this.mContext = mContext;
    }

    public MentorsAdapter(List<MentorItem> mentorItems) {
        this.mentorItems = mentorItems;
    }


    @NonNull
    @Override
    public MentorsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mentor, parent, false);

        return new MentorsAdapter.ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        holder.bind(mentorItems.get(position));
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(mContext, ProfileActivity.class);
            intent.putExtra(Constants.IS_MENTOR, true);
            intent.putExtra(Constants.MENTOR_ID, mentorItems.get(position).getMentorId());
            mContext.startActivity(intent);
        });
    }

    @Override
    public int getItemViewType(int position) {
        return super.getItemViewType(position);
    }

    @Override
    public int getItemCount() {
        return (isExpanded) ? mentorItems.size() : Math.min(mentorItems.size(), 3);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private CircleImageView ivTutorProfile;
        private TextView tvTutorName;
        private TextView tvTutorDescription;
        private TextView tvStudentsCount;
        private AppCompatButton btnBookNow;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            ivTutorProfile = itemView.findViewById(R.id.iv_tutor_profile);
            tvTutorName = itemView.findViewById(R.id.tv_tutor_name);
            tvTutorDescription = itemView.findViewById(R.id.tv_tutor_description);
            tvStudentsCount = itemView.findViewById(R.id.tv_students_count);
            btnBookNow = itemView.findViewById(R.id.btn_book_now);

        }

        void bind(MentorItem mentorItem) {
            Glide.with(mContext).load(mentorItem.getMentorImageUrl()).into(ivTutorProfile);
            tvTutorName.setText(mentorItem.getMentorName());
            tvTutorDescription.setText(mentorItem.getMentorDescription());
//                tvStudentsCount.setText(mentorItem.getStudentsCount());
            btnBookNow.setOnClickListener(v -> {
                Intent intent = new Intent(mContext, BookMentor.class);
                intent.putExtra("mentor", mentorItem.getMentorId());
                mContext.startActivity(intent);
            });

        }
    }
}
