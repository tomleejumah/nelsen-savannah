package com.app.nisisiafrica.Adapters;

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
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.Utils.OverlapImages;
import com.app.nisisiafrica.Utils.Roles;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.bumptech.glide.Glide;
import com.zen.overlapimagelistview.OverlapImageListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

/** Non-paging list used when mentors are filtered by questionnaire categories. */
public class MentorsArrayAdapter extends RecyclerView.Adapter<MentorsArrayAdapter.VH> {
    private final Context context;
    private final List<MentorItem> items = new ArrayList<>();

    public MentorsArrayAdapter(Context context) {
        this.context = context;
    }

    public void submit(List<MentorItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mentor, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class VH extends RecyclerView.ViewHolder {
        CircleImageView avatar;
        TextView name, desc, count, tag;
        AppCompatButton book;
        OverlapImageListView overlap;

        VH(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.iv_tutor_profile);
            name = itemView.findViewById(R.id.tv_tutor_name);
            desc = itemView.findViewById(R.id.tv_tutor_description);
            count = itemView.findViewById(R.id.tv_students_count);
            tag = itemView.findViewById(R.id.tvMentorTag);
            book = itemView.findViewById(R.id.btn_book_now);
            overlap = itemView.findViewById(R.id.overlapImage);
        }

        void bind(MentorItem m) {
            Glide.with(context).load(m.getMentorImageUrl()).into(avatar);
            name.setText(m.getMentorName());
            desc.setText(m.getMentorDescription() != null ? m.getMentorDescription() : "");
            OverlapImages.load(overlap, m.getStudentImages());
            long n = 0;
            try {
                if (m.getStudentsCount() != null)
                    n = Long.parseLong(m.getStudentsCount().replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {}
            if (n <= 0 && m.getStudentImages() != null) n = m.getStudentImages().size();
            Double rating = m.getAverageRating();
            boolean hasHistory = (rating != null && rating > 0) || n > 0;
            if (hasHistory && rating != null && rating > 0) {
                tag.setText(String.format(Locale.getDefault(), "★ %.1f · %d students", rating, n));
                count.setText(n > 0 ? n + " mentees" : "");
            } else if (hasHistory) {
                tag.setText(n + " students");
                count.setText(n + " mentees");
            } else {
                tag.setText("New mentor");
                count.setText("");
            }
            book.setVisibility(Roles.browsesMentors() ? View.VISIBLE : View.GONE);
            itemView.setOnClickListener(v -> {
                Intent i = new Intent(context, ProfileActivity.class);
                i.putExtra(Constants.IS_MENTOR, true);
                i.putExtra(Constants.MENTOR_ID, m.getMentorId());
                context.startActivity(i);
            });
            book.setOnClickListener(v -> {
                Intent i = new Intent(context, BookMentor.class);
                i.putExtra(Constants.MENTOR_ID, m.getMentorId());
                i.putExtra(Constants.MENTOR_NAME, m.getMentorName());
                context.startActivity(i);
            });
        }
    }
}
