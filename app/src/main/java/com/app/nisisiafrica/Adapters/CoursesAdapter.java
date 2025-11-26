package com.app.nisisiafrica.Adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

import com.app.nisisiafrica.Constants;
import com.app.nisisiafrica.EditProfileActivity;
import com.app.nisisiafrica.Interfaces.NotificationApiService;
import com.app.nisisiafrica.MainActivity;
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
        if (holder instanceof CompactViewHolder ) {
            ((CompactViewHolder) holder).bind(item);
            isLiked(item.getCourseId(),((CompactViewHolder) holder).likeBtn);
            ((CompactViewHolder) holder).likeBtn.setOnClickListener(v -> {
                if (v.getTag().equals("Like")){
                    FirebaseDatabase.getInstance().getReference().child("Likes").
                            child((item.getCourseId())).child(Util.
                                    getState(Constants.CURRENT_USER_ID, "")).setValue(true);

//                    addNotification(item.getCourseId(),Util.getState(Constants.CURRENT_USER_ID,
//                            ""),"Liked your Post", item.getTutorId());
                    addNotification(item.getCourseId(), item.getTutorId(), "Liked your Course: ");

                    notifyItemChanged(position);

//                    saveLikedPost(item.getCourseId(), posts.getUserName(), posts.getDescription(),
//                            posts.getPrice(), posts.getImageUrl(), posts.getPublisherID());
                }else {
                    notifyItemChanged(position);
                    FirebaseDatabase.getInstance().getReference().child("Likes").
                            child((item.getCourseId())).child(Util.getState(Constants.CURRENT_USER_ID, "")).removeValue();
                    removeLiked(item.getCourseId(),Util.getState(Constants.CURRENT_USER_ID, ""));
                }
            });

            ((CompactViewHolder) holder).courseBody.setOnClickListener(v -> {
                String url = item.getCourseLink();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                mContext.startActivity(browserIntent);
            });
//            ((CompactViewHolder) holder.courseBody.setOnClickListener(v -> {
//
//            });
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
        LinearLayout courseBody;

        public CompactViewHolder(@NonNull View itemView) {
            super(itemView);
            courseBody = itemView.findViewById(R.id.courseBody);
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

//    private void addNotification(String postID, String senderId,String text,String coursePublisher) {
//
//        HashMap<String,Object> map=new HashMap<>();
//
//        map.put("senderId",senderId);
//        map.put("text",text);
//        map.put("courseID",postID);
//
//        if (!coursePublisher.equals(Util.getState(Constants.CURRENT_USER_ID, ""))){
//            FirebaseDatabase.getInstance().getReference().child("Notifications").
//                    child(coursePublisher).push().setValue(map);
//        }
//    }

    /*private void addNotification(String postID, String coursePublisher, String text) {
        String currentUserId = Util.getState(Constants.CURRENT_USER_ID, "");

        if (coursePublisher.equals(currentUserId)) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("coursePublisher", coursePublisher);
        data.put("postID", postID);
        data.put("text", text);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("sendLikeNotification")
                .call(data)
                .addOnSuccessListener(result -> {
                    Log.d("Notification", "Sent successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("Notification", "Failed to send", e);
                });
    }
     */

    private static final String TAG = "CoursesAdapter";
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