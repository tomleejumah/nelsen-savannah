package com.app.nisisiafrica;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;

import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.Utils.EdgeBlurImageView;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import eightbitlab.com.blurview.BlurTarget;
import eightbitlab.com.blurview.BlurView;
import kotlin.Unit;

public class ProfileActivity extends AppCompatActivity implements FirebaseCallback {
    private static final String TAG = "ProfileActivity";
    boolean isFromMentor;
    BlurView blurViewName, blurViewDesc, blurViewDescHead, blurViewRc;
    private ConstraintLayout gradientOverlay;
    private CircleImageView imgDp;
    private EdgeBlurImageView dpImage;
    private CustomTarget<Bitmap> paletteTarget;
    private int defaultColor;
    private String id, role;
    private UserViewModel sharedUserViewModel;
    private UserData userData;
    private TextView tv_username, tvDescription,tvProfileName,tvAbout,tvRole;
    private Uri videoUri, photoUri;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_profile);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        if (intent != null) {
            isFromMentor = intent.getBooleanExtra(Constants.IS_MENTOR, false);
            id = isFromMentor ? intent.getStringExtra(Constants.MENTOR_ID) : intent.getStringExtra(Constants.CURRENT_USER_ID);
        }

        TextView title = findViewById(R.id.txtDescTittle);
        TextView myCourses = findViewById(R.id.myCourses);
        title.setText(isFromMentor ? "Mentor Profile" : "Profile");
        myCourses.setText(isFromMentor ? "My Materials" : "My Courses");
        float radius = 20f;
        BlurTarget target = findViewById(R.id.target);
        blurViewName = findViewById(R.id.blurViewName);
        blurViewDescHead = findViewById(R.id.blurViewDescHead);
        blurViewDesc = findViewById(R.id.blurViewDesc);
        blurViewRc = findViewById(R.id.bottomRc);
        imgDp = findViewById(R.id.imgDp);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        tv_username = findViewById(R.id.tv_username);
        tvDescription = findViewById(R.id.tv_Description);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvAbout = findViewById(R.id.tvAbout);
        tvRole = findViewById(R.id.tvRole);

        if (isFromMentor) {
            FirebaseRemoteDataSource.INSTANCE.getMentorData(id, mentors -> {
                        if (mentors != null) {

                            Glide.with(ProfileActivity.this)
                                    .load(mentors.getMentorImageUrl())
                                    .apply(RequestOptions.circleCropTransform())
                                    .into(imgDp);
                            tvProfileName.setText(mentors.getMentorName());
                            tvRole.setText("Mentor");
                            tvAbout.setText(mentors.getMentorDescription());
//            tvDescription.setText(mentors.getMentorDescription());
//            tv_username.setText(mentors.getMentorName());
//            loadAndStyle(mentors.getMentorImageUrl());
                        } else {
                            // Handle null case
                            blurViewDesc.setVisibility(View.GONE);
                            tvDescription.setText("");
                            tv_username.setText("");
                        }
                return Unit.INSTANCE;
            } ,e->{
                Log.d(TAG, "onCreate: Failed to fetch mentor" + e.getMessage());
                return Unit.INSTANCE;    }
            );
        } else {
            sharedUserViewModel = new ViewModelProvider(this).get(UserViewModel.class);
            sharedUserViewModel.fetchingCurrentUserDataFromDB(id).observe(this, data -> {
                if (data != null) {
                    userData = data;
//                    loadAndStyle(userData.getPhotoUrl());
                    Glide.with(ProfileActivity.this)
                            .load(userData.getPhotoUrl())
                            .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.ic_person)
                            .into(imgDp);
                    blurViewDesc.setVisibility(
                            TextUtils.isEmpty(data.getBio())
                                    ? View.GONE
                                    : View.VISIBLE
                    );
                    tvDescription.setText(userData.getBio());
//                    tv_username.setText(userData.getFirstName() + " " + userData.getLastName());
                    tvProfileName.setText(userData.getFirstName() + " " + userData.getLastName());
                    tvRole.setText(userData.getUserRole());
                    tvAbout.setText(userData.getBio());
                    if (Objects.equals(userData.getUserRole(), "Mentee")) {

                        tv_username.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                    }
                }
            });

        }

        gradientOverlay = findViewById(R.id.root);
        dpImage = findViewById(R.id.dpImage);
        dpImage.setBlurRadius(30f);

        defaultColor = ContextCompat.getColor(this, android.R.color.darker_gray);

        findViewById(R.id.btn_Menu).setOnClickListener(v -> {
            showToolsSheet();
        });

//        setupBlur(target, radius, blurViewName, blurViewDescHead, blurViewDesc, blurViewRc);

        findViewById(R.id.iv_action).setOnClickListener(v -> {
            Intent intent1 = new Intent(ProfileActivity.this, EditProfileActivity.class);
            boolean isMentor = userData != null && Objects.equals(userData.getUserRole(), "Mentor");
            intent1.putExtra(Constants.IS_MENTOR, isMentor);
            intent1.putExtra(Constants.CURRENT_USER_ID, userData != null ? userData.getId() : id);
            startActivity(intent1);
        });

        ExtendedFloatingActionButton button = findViewById(R.id.btnNext);
        button.setVisibility(!isFromMentor ? View.GONE : View.VISIBLE);
        findViewById(R.id.btn_Menu).setVisibility(isFromMentor ? View.GONE : View.VISIBLE);
        findViewById(R.id.iv_action).setVisibility(isFromMentor ? View.GONE : View.VISIBLE);
        button.setText(!isFromMentor ? "" : "Book Now");
        button.setOnClickListener(v -> {
            Intent intent1 = new Intent(ProfileActivity.this, BookMentor.class);
            intent1.putExtra(Constants.MENTOR_ID, id);
            intent1.putExtra(Constants.MENTOR_NAME, tvProfileName.getText().toString());
            startActivity(intent1);
        });
    }

    private void showToolsSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog((this));
        View view = getLayoutInflater().inflate(R.layout.upload_options, null);

        CardView uploadDocuments = view.findViewById(R.id.uploadDocuments);
        CardView uploadMedia = view.findViewById(R.id.uploadMedia);
        CardView captureMedia = view.findViewById(R.id.captureMedia);

        if (isFromMentor) {
            uploadDocuments.setVisibility(View.GONE);
        } else uploadDocuments.setVisibility(View.VISIBLE);

        uploadMedia.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            startActivityForResult(intent, 1002);
        });

        uploadDocuments.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/pdf");
            startActivityForResult(intent, 1001);
        });

        captureMedia.setOnClickListener(v -> {
            String[] options = {"Image", "Video"};
            new AlertDialog.Builder(this)
                    .setItems(options, (d, which) -> {
                        if (which == 0) captureImage();
                        else captureVideo();
                    }).show();

        });

        sheet.setContentView(view);
        sheet.show();
    }

    private Uri createMediaUri(String type) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "cap_" + System.currentTimeMillis());
        values.put(MediaStore.MediaColumns.MIME_TYPE,
                type.equals("image") ? "image/jpeg" : "video/mp4");

        return getContentResolver().insert(
                type.equals("image") ?
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI :
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
        );
    }

    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        photoUri = createMediaUri("image");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        startActivityForResult(intent, 1003);
    }

    private void captureVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        videoUri = createMediaUri("video");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
        startActivityForResult(intent, 1003);
    }

    private void setupBlur(BlurTarget target, float radius, BlurView... blurViews) {
        for (BlurView blurView : blurViews) {
            blurView.setupWith(target).setBlurRadius(radius);
            blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            blurView.setClipToOutline(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) return;

        Uri uri = null;

        if (requestCode == 1001 || requestCode == 1002) {
            if (data != null) uri = data.getData();
        } else if (requestCode == 1003) {
            uri = (photoUri != null) ? photoUri : videoUri;
        }

        if (uri == null) return;

        if (requestCode == 1001) uploadPdfToFirebase(uri);
        else uploadMediaToFirebase(uri);
    }

    private void uploadPdfToFirebase(Uri uri) {
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("docs/" + System.currentTimeMillis() + ".pdf");

       /* ref.putFile(uri)
                .addOnSuccessListener(task -> {})
                .addOnFailureListener(e -> {}); */
    }

    private void uploadMediaToFirebase(Uri uri) {
        String type = getContentResolver().getType(uri);

        String ext;
        if (type != null && type.startsWith("image")) ext = ".jpg";
        else ext = ".mp4";

        /*
        StorageReference ref = FirebaseStorage.getInstance()
                .getReference("media/" + System.currentTimeMillis() + ext);

        ref.putFile(uri);
         */
    }


    @Override
    public void onUserDataReceived(@org.jetbrains.annotations.Nullable UserData userData) {

    }

    @Override
    public void onMentorDataFetched(@org.jetbrains.annotations.Nullable MentorItem mentors) {
        if (mentors != null) {

            Glide.with(ProfileActivity.this)
                    .load(mentors.getMentorImageUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .into(imgDp);
            tvProfileName.setText(mentors.getMentorName());
            tvRole.setText("Mentor");
            tvAbout.setText(mentors.getMentorDescription());
//            tvDescription.setText(mentors.getMentorDescription());
//            tv_username.setText(mentors.getMentorName());
//            loadAndStyle(mentors.getMentorImageUrl());
        } else {
            // Handle null case
            blurViewDesc.setVisibility(View.GONE);
            tvDescription.setText("");
            tv_username.setText("");
        }
    }

    @Override
    public void onMentorsIDFetched(@org.jetbrains.annotations.Nullable List<@org.jetbrains.annotations.Nullable String> mentorIds) {
    }

    @Override
    public void onCoursesFetched(@NotNull List<@NotNull CourseItem> courses) {
    }

    @Override
    public void onMentorsFetched(@NotNull List<@NotNull MentorItem> mentors) {
    }

    @Override
    public void onError(@org.jetbrains.annotations.Nullable Exception e) {
    }

    private void loadAndStyle(String url) {
        // Load as Bitmap for color extraction
        paletteTarget = new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {

                Palette.from(resource).generate(palette -> {
                    int dominant = palette != null ? palette.getDominantColor(defaultColor) : defaultColor;
                    int vibrant = palette != null ? palette.getVibrantColor(dominant) : dominant;
                    int muted = palette != null ? palette.getMutedColor(dominant) : dominant;
//                    applyColorsAnimated(dominant, vibrant, muted);
                });

                Glide.with(ProfileActivity.this)
                        .load(url)
                        .apply(RequestOptions.circleCropTransform())
//                        .placeholder(R.drawable.donation)
                        .into(dpImage);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        };

        Glide.with(this)
                .asBitmap()
                .load(url)
                .apply(RequestOptions.centerCropTransform())
                .into(paletteTarget);
    }

    private void applyColorsAnimated(int dominant, int vibrant, int muted) {

        int c1 = setAlpha(muted, 0.8f);
        int c2 = setAlpha(dominant, 0.7f);
        int c3 = setAlpha(vibrant, 0.6f);
        // For a simple 2-color gradient, use dominant to vibrant; for 3-color, use LinearGradient with positions
        GradientDrawable grad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{c1, c2, c3}
        );
        grad.setColors(new int[]{c1, c2, c3});
        grad.setGradientType(GradientDrawable.LINEAR_GRADIENT);
//        grad.setOrientation(GradientDrawable.Orientation.TL_BR);

        // Crossfade the overlay to the new gradient
        final Drawable before = gradientOverlay.getBackground();
        gradientOverlay.setBackground(grad);
        gradientOverlay.setAlpha(0f);
        gradientOverlay.animate().alpha(1f).setDuration(350).start();

        // Animate status bar to a darkened version of dominant (Spotify-style)
//        int newStatus = darken(dominant, 0.2f);
//        Window w = getWindow();
//        int start = w.getStatusBarColor();
//        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), start, c1);
//        va.setDuration(350);
//        va.addUpdateListener(animation -> w.setStatusBarColor((int) animation.getAnimatedValue()));
//        va.start();

        // Set readable text color based on dominant todo
        int textColor = isDark(dominant) ? Color.WHITE : Color.BLACK;
    }

    // Your helper methods remain the same
    private int setAlpha(int color, float alphaFactor) {
        int a = Math.round(Color.alpha(color) * alphaFactor);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int darken(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, hsv[2] - factor);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private boolean isDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (paletteTarget != null) {
//            Glide.with(this).clear(paletteTarget);
        }
    }
}
