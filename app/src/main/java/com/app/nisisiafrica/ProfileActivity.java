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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.palette.graphics.Palette;

import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.Utils.EdgeBlurImageView;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.remote.FirebaseRemoteDataSource;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.github.barteksc.pdfviewer.PDFView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
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
    private TextView tv_username, tvDescription, tvProfileName, tvAbout, tvRole;
    private Uri videoUri, photoUri;
    private View progressView;  // The inflated progress layout
    private ProgressBar progressBar;
    private TextView progressTextView;
    private TextView percentTextView;
    private View uploadOptionsView;  // The inflated bottom sheet view
    private LinearLayout fileSelectionView;
    private ScrollView previewView;
    private PDFView pdfView;
    private LinearLayout pdfPlaceholder;
    private TextView pdfFileName, pdfFileSize;
    private ImageView imageView;
    private FrameLayout videoContainer;
    private VideoView videoView;
    private ImageButton playButton;
    private TextView typeBadge;
    private TextInputEditText descriptionInput;
    private MaterialButton uploadButton;
    private Uri currentPreviewUri;
    private String currentFileType;


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
                    }, e -> {
                        Log.d(TAG, "onCreate: Failed to fetch mentor" + e.getMessage());
                        return Unit.INSTANCE;
                    }
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
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        uploadOptionsView = getLayoutInflater().inflate(R.layout.upload_options, null);

        // Initialize views
        fileSelectionView = uploadOptionsView.findViewById(R.id.fileSelectionView);
        previewView = uploadOptionsView.findViewById(R.id.previewView);
        pdfView = uploadOptionsView.findViewById(R.id.pdfView);
        pdfPlaceholder = uploadOptionsView.findViewById(R.id.pdfPlaceholder);
        pdfFileName = uploadOptionsView.findViewById(R.id.pdfFileName);
        pdfFileSize = uploadOptionsView.findViewById(R.id.pdfFileSize);
        imageView = uploadOptionsView.findViewById(R.id.imageView);
        videoContainer = uploadOptionsView.findViewById(R.id.videoContainer);
        videoView = uploadOptionsView.findViewById(R.id.videoView);
        playButton = uploadOptionsView.findViewById(R.id.playButton);
        typeBadge = uploadOptionsView.findViewById(R.id.typeBadge);
        descriptionInput = uploadOptionsView.findViewById(R.id.descriptionInput);
        uploadButton = uploadOptionsView.findViewById(R.id.uploadButton);

        TextView sheetTitle = uploadOptionsView.findViewById(R.id.sheetTitle);
        sheetTitle.setOnClickListener(v -> {
            // Show the 3 option cards again
            uploadOptionsView.findViewById(R.id.uploadDocuments).setVisibility(View.VISIBLE);
            uploadOptionsView.findViewById(R.id.uploadMedia).setVisibility(View.VISIBLE);
            uploadOptionsView.findViewById(R.id.captureMedia).setVisibility(View.VISIBLE);

            // Hide preview
            previewView.setVisibility(View.GONE);

            // Reset title
            sheetTitle.setText("Add Material");
        });

        CardView uploadDocuments = uploadOptionsView.findViewById(R.id.uploadDocuments);
        CardView uploadMedia = uploadOptionsView.findViewById(R.id.uploadMedia);
        CardView captureMedia = uploadOptionsView.findViewById(R.id.captureMedia);

        if (isFromMentor) {
            uploadDocuments.setVisibility(View.GONE);
        }

        // File selection click listeners
        uploadMedia.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            startActivityForResult(intent, 1002);
            sheet.dismiss();
        });

        uploadDocuments.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/pdf");
            startActivityForResult(intent, 1001);
            sheet.dismiss();
        });

        captureMedia.setOnClickListener(v -> {
            String[] options = {"Image", "Video"};
            new AlertDialog.Builder(this)
                    .setItems(options, (d, which) -> {
                        if (which == 0) captureImage();
                        else captureVideo();
                        sheet.dismiss();
                    }).show();
        });

        // Description input listener
        descriptionInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean isEnabled = !s.toString().trim().isEmpty();
                uploadButton.setEnabled(isEnabled);
                uploadButton.setAlpha(isEnabled ? 1f : 0.5f);
//                uploadButton.setEnabled(s.toString().trim().length() > 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Upload button listener
        uploadButton.setOnClickListener(v -> {
            if (descriptionInput.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Please add a description", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadCurrentFile();
            sheet.dismiss();
        });

        // Video play button
        playButton.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
                playButton.setVisibility(View.VISIBLE);
            } else {
                videoView.start();
                playButton.setVisibility(View.GONE);
            }
        });

        videoView.setOnCompletionListener(mp -> playButton.setVisibility(View.VISIBLE));

        sheet.setContentView(uploadOptionsView);
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


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) return;

        Uri uri = null;

        switch (requestCode) {
            case 1001: // PDF
                if (data != null) uri = data.getData();
                if (uri != null) {
                    showToolsSheet();
                    showPreviewInSheet(uri, "application/pdf");
                }
                break;

            case 1002: // Media (image/video)
                if (data != null) uri = data.getData();
                if (uri != null) {
                    String type = getContentResolver().getType(uri);
                    showToolsSheet();
                    showPreviewInSheet(uri, type);
                }
                break;

            case 1003: // Captured media
                if (videoUri != null) uri = videoUri;
                else if (photoUri != null) uri = photoUri;

                if (uri != null) {
                    String type = getContentResolver().getType(uri);
                    showToolsSheet(); // Re-open sheet
                    showPreviewInSheet(uri, type);
                }
                break;
        }
    }

    private void showPreviewInSheet(Uri uri, String mimeType) {
        currentPreviewUri = uri;
        currentFileType = mimeType;

        // Hide the 3 option cards
        uploadOptionsView.findViewById(R.id.uploadDocuments).setVisibility(View.GONE);
        uploadOptionsView.findViewById(R.id.uploadMedia).setVisibility(View.GONE);
        uploadOptionsView.findViewById(R.id.captureMedia).setVisibility(View.GONE);

        // Show preview ScrollView
        previewView.setVisibility(View.VISIBLE);

        // Update title
        TextView sheetTitle = uploadOptionsView.findViewById(R.id.sheetTitle);
        sheetTitle.setText("← Preview & Upload");

        // Hide all preview types first
        pdfView.setVisibility(View.GONE);
        pdfPlaceholder.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        videoContainer.setVisibility(View.GONE);

        // Show appropriate preview based on type
        if (mimeType != null) {
            if (mimeType.equals("application/pdf")) {
                pdfPlaceholder.setVisibility(View.VISIBLE);
                pdfFileName.setText(getFileName(uri));
                pdfFileSize.setText(getFileSize(uri));
                typeBadge.setText("PDF DOCUMENT");

            } else if (mimeType.startsWith("image/")) {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageURI(uri);
                typeBadge.setText("IMAGE FILE");

            } else if (mimeType.startsWith("video/")) {
                videoContainer.setVisibility(View.VISIBLE);
                videoView.setVideoURI(uri);
                playButton.setVisibility(View.VISIBLE);
                typeBadge.setText("VIDEO FILE");
            }
        }

        // Reset description
        descriptionInput.setText("");
        descriptionInput.requestFocus();

        // Scroll to top after layout
        previewView.postDelayed(() -> previewView.scrollTo(0, 0), 100);
    }

    private void uploadCurrentFile() {
        String description = descriptionInput.getText().toString().trim();
        if (description.isEmpty()) {
            Toast.makeText(this, "Please add a description", Toast.LENGTH_SHORT).show();
            return;
        }

        ViewGroup rootContainer = findViewById(android.R.id.content);
        progressView = LayoutInflater.from(this).inflate(R.layout.progress_layout, rootContainer, false);
        rootContainer.addView(progressView);
        MotionLayout motionLayout1 = (MotionLayout) progressView;
        progressBar = progressView.findViewById(R.id.progressbar);
        progressTextView = progressView.findViewById(R.id.operateDescTv);
        percentTextView = progressView.findViewById(R.id.operateProgressTv);

        // Determine folder and extension
        String ext;
        if (currentFileType != null) {
            if (currentFileType.equals("application/pdf")) {
                ext = ".pdf";
            } else if (currentFileType.startsWith("image/")) {
                ext = ".jpg";
            } else if (currentFileType.startsWith("video/")) {
                ext = ".mp4";
            } else {
                ext = ".file";
            }
        } else {
            ext = ".file";
        }

        StorageReference storageReference = FirebaseStorage.getInstance().getReference();
        String originalName = getFileName(currentPreviewUri);
        if (originalName.contains(".")) {
            originalName = originalName.substring(0, originalName.lastIndexOf("."));
        }
        String fileName = originalName + "_" + System.currentTimeMillis() + ext;
        StorageReference mediaRef = storageReference
                .child("USER_MEDIA")
                .child(Util.getState(Constants.CURRENT_USER_ID, ""))
                .child(fileName);

        if (currentPreviewUri == null) {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
            return;
        }

        UploadTask uploadTask = mediaRef.putFile(currentPreviewUri);
        uploadTask.addOnProgressListener(taskSnapshot -> {
            double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
            int percent = (int) progress;

            if (progressBar != null && percentTextView != null) {
                progressBar.setProgress(percent);
                percentTextView.setText(String.valueOf(percent));

                if (percent < 30) {
                    progressTextView.setText("Uploading...");
                } else if (percent < 70) {
                    progressTextView.setText("Processing...");
                } else if (percent < 100) {
                    progressTextView.setText("Almost done...");
                } else {
                    progressTextView.setText("Complete!");
                }
            }
        }).addOnSuccessListener(taskSnapshot -> {
            mediaRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String mediaUrl = uri.toString();

                // Save to database
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("User_Media");
                String postID = ref.push().getKey();

                HashMap<String, Object> map = new HashMap<>();
                map.put("PublisherID", Util.getState(Constants.CURRENT_USER_ID, ""));
                map.put("postID", postID);
                map.put("mediaUrl", mediaUrl);
                map.put("description", description);
                map.put("FileType", ext);
                map.put("FileName", fileName);

                ref.child(Util.getState(Constants.CURRENT_USER_ID, ""))
                        .child(postID)
                        .setValue(map)
                        .addOnSuccessListener(aVoid -> {
                            // Hide progress
                            dismissProgressOverlay();
                            Toast.makeText(this, "Upload successful!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            dismissProgressOverlay();
                            Toast.makeText(this, "Database save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            });
        }).addOnFailureListener(e -> {
            if (progressView != null) {
                motionLayout1.removeView(progressView);
                progressView = null;
            }
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void dismissProgressOverlay() {
        if (progressView != null) {
            ViewGroup parent = (ViewGroup) progressView.getParent();
            if (parent != null) {
                parent.removeView(progressView);
            }
            progressView = null; // Clear reference to prevent memory leaks
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private String getFileSize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    long size = cursor.getLong(sizeIndex);
                    return formatFileSize(size);
                }
            }
        }
        return "Unknown";
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
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

    private void setupBlur(BlurTarget target, float radius, BlurView... blurViews) {
        for (BlurView blurView : blurViews) {
            blurView.setupWith(target).setBlurRadius(radius);
            blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            blurView.setClipToOutline(true);
        }
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
