package com.app.nisisiafrica.Adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.SchoolsListActivity;
import com.app.nisisiafrica.AllCoursesActivity;
import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.EditProfileActivity;
import com.app.nisisiafrica.Interfaces.NotificationApiService;
import com.app.nisisiafrica.MainActivity;
import com.app.nisisiafrica.ProfileActivity;
import com.app.nisisiafrica.TrackLearnActivity;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.R;
import com.app.nisisiafrica.ViewAllActivity;
import com.app.nisisiafrica.data.Model.LikeNotificationRequest;
import com.app.nisisiafrica.data.Model.NotificationResponse;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CoursesAdapter extends PagingDataAdapter<CourseItem, RecyclerView.ViewHolder> {
    private static final int TYPE_COMPACT = 0;
    private static final int TYPE_EXPANDED = 1;
    private static final int TYPE_UPDATE_PROFILE = 2;
    private static final int TYPE_ADD_MORE = 3;
    private final Context mContext;

    private static final DiffUtil.ItemCallback<CourseItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<CourseItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull CourseItem oldItem, @NonNull CourseItem newItem) {
            String oldId = oldItem.getCourseId();
            String newId = newItem.getCourseId();
            if (oldId == null || newId == null) return false;
            return oldId.equals(newId);
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
            int shown = Math.min(super.getItemCount(), 5);
            if (shown == 1 && position == 1) return TYPE_ADD_MORE;
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

        if (viewType == TYPE_ADD_MORE) {
            return new AddMoreViewHolder(inflater.inflate(R.layout.item_course_add_more, parent, false));
        } else if (viewType == TYPE_COMPACT) {
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
        if (holder instanceof AddMoreViewHolder) {
            holder.itemView.setOnClickListener(v ->
                    mContext.startActivity(new Intent(mContext, SchoolsListActivity.class)));
            return;
        }

        CourseItem item = getItem(position);
        if (item == null) return;
        if (holder instanceof CompactViewHolder ) {
            CompactViewHolder vh = (CompactViewHolder) holder;
            vh.bind(item, position);
            isLiked(item.getCourseId(), vh.likeBtn);
            vh.likeBtn.setOnClickListener(v -> {
                if (v.getTag().equals("Like")){
                    FirebaseDatabase.getInstance().getReference().child("Likes").
                            child((item.getCourseId())).child(Util.
                                    getState(Constants.CURRENT_USER_ID, "")).setValue(true);

                    addNotification(item.getCourseId(), item.getTutorId(), "Liked your Course: ");

                    notifyItemChanged(position);
                }else {
                    notifyItemChanged(position);
                    FirebaseDatabase.getInstance().getReference().child("Likes").
                            child((item.getCourseId())).child(Util.getState(Constants.CURRENT_USER_ID, "")).removeValue();
                    removeLiked(item.getCourseId(),Util.getState(Constants.CURRENT_USER_ID, ""));
                }
            });

            // Whole card (incl. cover) opens the course; only tutor avatar/name open Profile.
            holder.itemView.setOnClickListener(v -> {
                Intent learn = new Intent(mContext, TrackLearnActivity.class);
                learn.putExtra(TrackLearnActivity.EXTRA_TRACK_ID, item.getCourseId());
                learn.putExtra(TrackLearnActivity.EXTRA_TITLE, item.getCourseTitle());
                String tutorLabel = item.getTutorName();
                boolean showTutor = hasRealTutor(tutorLabel);
                learn.putExtra(TrackLearnActivity.EXTRA_DESC,
                        showTutor ? "with " + tutorLabel : "");
                learn.putExtra(TrackLearnActivity.EXTRA_FALLBACK_URL, item.getCourseLink());
                if (showTutor && item.getTutorId() != null && !item.getTutorId().isEmpty()) {
                    learn.putExtra(TrackLearnActivity.EXTRA_TUTOR_ID, item.getTutorId());
                }
                if (showTutor) {
                    learn.putExtra(TrackLearnActivity.EXTRA_TUTOR_NAME, tutorLabel);
                }
                if (showTutor && item.getTutorAvatarUrl() != null) {
                    learn.putExtra(TrackLearnActivity.EXTRA_TUTOR_AVATAR, item.getTutorAvatarUrl());
                }
                mContext.startActivity(learn);
            });
            View.OnClickListener openTutor = v -> {
                String tid = item.getTutorId();
                if (tid == null || tid.isEmpty() || !hasRealTutor(item.getTutorName())) {
                    Toast.makeText(mContext, "Tutor profile unavailable", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent profile = new Intent(mContext, ProfileActivity.class);
                profile.putExtra(Constants.IS_MENTOR, true);
                profile.putExtra(Constants.MENTOR_ID, tid);
                if (item.getTutorName() != null) {
                    profile.putExtra(Constants.MENTOR_NAME, item.getTutorName());
                }
                mContext.startActivity(profile);
            };
            vh.tv_tutor_name.setOnClickListener(openTutor);
            vh.iv_tutor_avatar.setOnClickListener(openTutor);
            if (vh.tutorClickRow != null) {
                vh.tutorClickRow.setOnClickListener(openTutor);
            }
        } else if (holder instanceof UpdateProfileViewHolder) {
            ((UpdateProfileViewHolder) holder).bind(item);
            holder.itemView.setOnClickListener(v -> {
                Toast.makeText(mContext, "working on update course feature", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public int getItemCount() {
        int n = super.getItemCount();
        if (n == 0) return 0;
        if (mContext instanceof MainActivity) {
            int shown = Math.min(n, 5);
            // Exactly one active course → append "enroll more" polaroid.
            return shown == 1 ? 2 : shown;
        }
        return n;
    }

    class AddMoreViewHolder extends RecyclerView.ViewHolder {
        AddMoreViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    class CompactViewHolder extends RecyclerView.ViewHolder {
        TextView tv_lessons, tv_duration, tv_course_title, tv_tutor_name;
        CircleImageView iv_tutor_avatar, likeBtn;
        ImageView iv_course_image;
        LinearLayout courseBody;
        View tutorClickRow;

        public CompactViewHolder(@NonNull View itemView) {
            super(itemView);
            courseBody = itemView.findViewById(R.id.courseBody);
            tutorClickRow = itemView.findViewById(R.id.tutorClickRow);
            tv_duration = itemView.findViewById(R.id.tv_duration);
            tv_lessons = itemView.findViewById(R.id.tv_lessons);
            tv_course_title = itemView.findViewById(R.id.tv_course_title);
            tv_tutor_name = itemView.findViewById(R.id.tv_tutor_name);
            iv_course_image = itemView.findViewById(R.id.iv_course_image);
            iv_tutor_avatar = itemView.findViewById(R.id.iv_tutor_avatar);
            likeBtn = itemView.findViewById(R.id.likeBtn);
        }

        void bind(CourseItem courseItem, int position) {
            // Alternating polaroid tilt on home rail.
            if (mContext instanceof MainActivity) {
                itemView.setRotation(position % 2 == 0 ? -2.2f : 1.6f);
            } else {
                itemView.setRotation(0f);
            }

            tv_course_title.setText(courseItem.getCourseTitle());
            String lessons = courseItem.getLessons() != null ? courseItem.getLessons().trim() : "";
            if (tv_duration != null) {
                tv_duration.setText(courseItem.getDuration() != null ? courseItem.getDuration() : "");
            }
            if (tv_lessons != null) {
                tv_lessons.setText(lessons);
            }

            String tutor = courseItem.getTutorName();
            boolean showTutor = hasRealTutor(tutor);
            String meta;
            if (showTutor && !lessons.isEmpty()) {
                meta = tutor.toUpperCase() + " · " + lessons.toUpperCase();
            } else if (showTutor) {
                meta = tutor.toUpperCase();
            } else if (!lessons.isEmpty()) {
                meta = lessons.toUpperCase();
            } else {
                meta = "";
            }
            if (tutorClickRow != null) {
                tutorClickRow.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
            }
            if (tv_tutor_name != null) {
                tv_tutor_name.setText(meta);
                tv_tutor_name.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
            }

            Glide.with(mContext).load(courseItem.getCourseImageUrl()).into(iv_course_image);
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

    private static final String TAG = "CoursesAdapter";

    /** Blank / org placeholder — hide tutor UI instead of defaulting. */
    private static boolean hasRealTutor(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.isEmpty()) return false;
        return !n.equalsIgnoreCase("Nelsen Savannah")
                && !n.equalsIgnoreCase("Nelsen Savannah Innovation Hub");
    }

    private void addNotification(String courseId, String tutorId, String text) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            return;
        }

        currentUser.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String firebaseToken = task.getResult().getToken();

                // Create request
                LikeNotificationRequest request = new LikeNotificationRequest(tutorId,courseId,text);

                // Call API
                NotificationApiService service = ApiClient.getNotificationService();
                Call<NotificationResponse> call = service.sendLikeNotification(
                        "Bearer " + firebaseToken,
                        request
                );

                call.enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<NotificationResponse> call,
                                           Response<NotificationResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            NotificationResponse result = response.body();
                            if (result.getSuccess()) {
                                Log.d(TAG, "Notification sent successfully");
                            } else {
                                Log.w(TAG, "Notification failed: " + result.getMessage());
                            }
                        } else {
                            Log.e(TAG, "API Error: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<NotificationResponse> call, Throwable t) {
                        Log.e(TAG, "Network error: " + t.getMessage());
                    }
                });
            } else {
                Log.e(TAG, "Failed to get Firebase token");
            }
        });
    }
    private void removeLiked(String postId,String PublisherID){
        DatabaseReference cartRef = FirebaseDatabase.getInstance().getReference().child("LIKED")
                .child(Util.getState(Constants.CURRENT_USER_ID, ""));

        Query query = cartRef.orderByChild("courseID").equalTo(postId);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot itemSnapshot : dataSnapshot.getChildren()) {
                    itemSnapshot.getRef().removeValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("Firebase", "onCancelled", databaseError.toException());
            }
        });
    }

    private void isLiked(String courseId, ImageView imageView){
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference()
                .child("Likes").child(courseId);

        imageView.setImageResource(R.drawable.ic_liked);

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.child(Util.getState(Constants.CURRENT_USER_ID, "")).exists()){
//                    imageView.setImageResource(R.drawable.ic_liked);
                    imageView.setTag("Liked");
                } else {
                    imageView.setImageResource(R.drawable.ic_like);
                    imageView.setTag("Like");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                imageView.setImageResource(R.drawable.ic_like);
                imageView.setTag("Like");
            }
        });
    }
}