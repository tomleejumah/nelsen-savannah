package com.app.nisisiafrica;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.nisisiafrica.Adapters.MediaGridAdapter;
import com.app.nisisiafrica.Interfaces.FirebaseCallback;
import com.app.nisisiafrica.Utils.EdgeBlurImageView;
import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.CourseItem;
import com.app.nisisiafrica.data.Model.MentorItem;
import com.app.nisisiafrica.data.Model.UserData;
import com.app.nisisiafrica.data.Model.UserMedia;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.shockwave.pdfium.PdfiumCore;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
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
    private View progressView;
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
    private RecyclerView rvMediaGrid;
    private MediaGridAdapter mediaAdapter;
    private List<UserMedia> mediaList = new ArrayList<>();
    private boolean isLoadingMedia = false;
    private String lastMediaKey = null;
    private static final int PAGE_SIZE = 18;

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
        initMediaGrid();
    }

    private void initMediaGrid() {
        rvMediaGrid = findViewById(R.id.rvMediaGrid);

        // Setup grid with 3 columns
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        rvMediaGrid.setLayoutManager(gridLayoutManager);

        // Open full preview
        mediaAdapter = new MediaGridAdapter(this, this::showMediaPreview);
        rvMediaGrid.setAdapter(mediaAdapter);

        // Load initial media
        loadUserMedia(false);

        // Setup pagination - load more when scrolling
        rvMediaGrid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!isLoadingMedia && dy > 0) { // Scrolling down
                    int visibleItemCount = gridLayoutManager.getChildCount();
                    int totalItemCount = gridLayoutManager.getItemCount();
                    int firstVisibleItem = gridLayoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItem) >= totalItemCount - 6) {
                        // Load more when 6 items from bottom
                        loadUserMedia(true);
                    }
                }
            }
        });
    }

    private void loadUserMedia(boolean loadMore) {
        if (isLoadingMedia) return;
        isLoadingMedia = true;

        String userId = isFromMentor ? id : Util.getState(Constants.CURRENT_USER_ID, "");

        DatabaseReference mediaRef = FirebaseDatabase.getInstance()
                .getReference("User_Media")
                .child(userId);

        Query query;
        if (loadMore && lastMediaKey != null) {
            // Load next page
            query = mediaRef.orderByKey()
                    .endBefore(lastMediaKey)
                    .limitToLast(PAGE_SIZE);
        } else {
            // Load first page (most recent)
            query = mediaRef.orderByKey()
                    .limitToLast(PAGE_SIZE);
        }

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<UserMedia> newMedia = new ArrayList<>();

                for (DataSnapshot mediaSnapshot : snapshot.getChildren()) {
                    UserMedia media = mediaSnapshot.getValue(UserMedia.class);
                    if (media != null) {
                        Log.d(TAG, "onDataChange: true");
                        newMedia.add(media);
                    }
                }
                if (newMedia.isEmpty()) {
                    Log.d(TAG, "No media found");

                    findViewById(R.id.emptyMediaState).setVisibility(View.VISIBLE);
                    findViewById(R.id.rvMediaGrid).setVisibility(View.GONE);

                } else {
                    Log.d(TAG, "Media exists");

                    findViewById(R.id.emptyMediaState).setVisibility(View.GONE);
                    findViewById(R.id.rvMediaGrid).setVisibility(View.VISIBLE);
                }

                if (!newMedia.isEmpty()) {
                    // Reverse to show newest first (orderByKey gives oldest first)
                    Collections.reverse(newMedia);

                    if (loadMore) {
                        mediaAdapter.addMedia(newMedia);
                        mediaList.addAll(newMedia);
                    } else {
                        mediaAdapter.setMediaList(newMedia);
                        mediaList = newMedia;
                    }

                    // Update lastKey for pagination
                    lastMediaKey = newMedia.get(newMedia.size() - 1).getPostID();
                }

                isLoadingMedia = false;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                isLoadingMedia = false;
                Toast.makeText(ProfileActivity.this,
                        "Failed to load media: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Show full preview when media item clicked
    private void showMediaPreview(UserMedia media, int position) {
        BottomSheetDialog previewDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_media_preview, null);

        ImageView imagePreview = view.findViewById(R.id.previewImage);
        VideoView videoPreview = view.findViewById(R.id.previewVideo);
        LinearLayout pdfPreview = view.findViewById(R.id.previewPdf);
        TextView pdfName = view.findViewById(R.id.previewPdfName);
        TextView description = view.findViewById(R.id.previewDescription);
        ImageButton closeBtn = view.findViewById(R.id.closePreview);
        ImageButton playBtn = view.findViewById(R.id.playPreview);

        // Hide all first
        imagePreview.setVisibility(View.GONE);
        videoPreview.setVisibility(View.GONE);
        pdfPreview.setVisibility(View.GONE);
        playBtn.setVisibility(View.GONE);

        String fileType = media.getFileType().toLowerCase();

        if (fileType.contains("pdf")) {
            pdfPreview.setVisibility(View.VISIBLE);
            pdfName.setText(media.getFileName());
        } else if (fileType.contains("mp4") || fileType.contains("video")) {
            videoPreview.setVisibility(View.VISIBLE);
            playBtn.setVisibility(View.VISIBLE);
            videoPreview.setVideoURI(Uri.parse(media.getMediaUrl()));

            playBtn.setOnClickListener(v -> {
                if (videoPreview.isPlaying()) {
                    videoPreview.pause();
                    playBtn.setImageResource(R.drawable.ic_play);
                } else {
                    videoPreview.start();
                    playBtn.setImageResource(R.drawable.ic_pause);
                }
            });
        } else {
            imagePreview.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(media.getMediaUrl())
                    .into(imagePreview);
        }

        description.setText(media.getDescription());
        closeBtn.setOnClickListener(v -> previewDialog.dismiss());

        previewDialog.setContentView(view);
        previewDialog.show();
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
                    showToolsSheet();
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

        BlurTarget target = uploadOptionsView.findViewById(R.id.target);
        BlurView blurViewName = uploadOptionsView.findViewById(R.id.blurViewName);

        blurViewName.setupWith(target).setBlurRadius(10f);
        blurViewName.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        blurViewName.setClipToOutline(true);

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
                pdfView.setVisibility(View.VISIBLE);
                pdfPlaceholder.setVisibility(View.VISIBLE);
                pdfFileName.setText(getFileName(uri));
                pdfFileSize.setText(getFileSize(uri));
                typeBadge.setText("PDF DOCUMENT");

                pdfView.fromUri(uri)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .load();

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

        // --- UI Setup ---
        ViewGroup rootContainer = findViewById(android.R.id.content);
        progressView = LayoutInflater.from(this).inflate(R.layout.progress_layout, rootContainer, false);
        rootContainer.addView(progressView);

        progressBar = progressView.findViewById(R.id.progressbar);
        progressTextView = progressView.findViewById(R.id.operateDescTv);
        percentTextView = progressView.findViewById(R.id.operateProgressTv);

        // --- File Setup ---
        String ext = getExtension(currentFileType);
        String originalName = getFileNameFromUri(currentPreviewUri);
        String fileName = originalName + "_" + System.currentTimeMillis() + ext;
        String userId = Util.getState(Constants.CURRENT_USER_ID, "");

        StorageReference mediaRef = FirebaseStorage.getInstance().getReference()
                .child("USER_MEDIA").child(userId).child(fileName);

        // --- Start Upload ---
        UploadTask uploadTask = mediaRef.putFile(currentPreviewUri);

        uploadTask.addOnProgressListener(snapshot -> {
            int percent = (int) ((100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount());
            progressBar.setProgress(percent);
            percentTextView.setText(String.valueOf(percent)); // Fixed integer crash
            updateStatusText(percent);
        }).addOnSuccessListener(taskSnapshot -> {
            mediaRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String mediaUrl = uri.toString();

                // Handle PDF Thumbnail before saving to DB
                if (ext.equals(".pdf")) {
                    uploadPdfThumbnail(mediaUrl, description, ext, fileName);
                } else {
                    saveToDatabase(mediaUrl, null, description, ext, fileName);
                }
            });
        }).addOnFailureListener(e -> {
            dismissProgressOverlay();
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void uploadPdfThumbnail(String pdfUrl, String desc, String ext, String name) {
        Bitmap thumb = getPdfThumbnail(currentPreviewUri);
        if (thumb == null) {
            saveToDatabase(pdfUrl, null, desc, ext, name);
            return;
        }

        StorageReference thumbRef = FirebaseStorage.getInstance().getReference()
                .child("THUMBNAILS").child(Util.getState(Constants.CURRENT_USER_ID, ""))
                .child(name.replace(".pdf", ".jpg"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        thumb.compress(Bitmap.CompressFormat.JPEG, 70, baos);

        thumbRef.putBytes(baos.toByteArray()).addOnSuccessListener(task -> {
            thumbRef.getDownloadUrl().addOnSuccessListener(uri -> {
                saveToDatabase(pdfUrl, uri.toString(), desc, ext, name);
            });
        }).addOnFailureListener(e -> saveToDatabase(pdfUrl, null, desc, ext, name));
    }

    private void saveToDatabase(String url, String thumbUrl, String desc, String ext, String name) {
        String userId = Util.getState(Constants.CURRENT_USER_ID, "");
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("User_Media").child(userId);
        String postID = ref.push().getKey();

        HashMap<String, Object> map = new HashMap<>();
        map.put("publisherID", userId);
        map.put("postID", postID);
        map.put("mediaUrl", url);
        map.put("thumbnailUrl", thumbUrl);
        map.put("description", desc);
        map.put("fileType", ext);
        map.put("fileName", name);
        map.put("timestamp", System.currentTimeMillis());

        ref.child(postID).setValue(map).addOnCompleteListener(task -> {
            dismissProgressOverlay();
            if (task.isSuccessful()) {
                Toast.makeText(this, "Uploaded!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private Bitmap getPdfThumbnail(Uri uri) {
        PdfiumCore pdfiumCore = new PdfiumCore(this);
        try {
            ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r");
            com.shockwave.pdfium.PdfDocument pdfDocument = pdfiumCore.newDocument(fd);
            pdfiumCore.openPage(pdfDocument, 0);

            int width = pdfiumCore.getPageWidthPoint(pdfDocument, 0);
            int height = pdfiumCore.getPageHeightPoint(pdfDocument, 0);

            // Use RGB_565 to force a solid background (no transparency)
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            // Use 'true' in the last parameter to render annotations and correct background
            pdfiumCore.renderPageBitmap(pdfDocument, bitmap, 0, 0, 0, width, height, true);

            pdfiumCore.closeDocument(pdfDocument);
            fd.close();
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
    private String getFileNameFromUri(Uri uri) {
        String name = "file";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            name = cursor.getString(index);
            if (name.contains(".")) name = name.substring(0, name.lastIndexOf("."));
            cursor.close();
        }
        return name;
    }

    private String getExtension(String mime) {
        if (mime == null) return ".file";
        if (mime.equals("application/pdf")) return ".pdf";
        if (mime.startsWith("image/")) return ".jpg";
        if (mime.startsWith("video/")) return ".mp4";
        return ".file";
    }

    private void updateStatusText(int percent) {
        if (percent < 30) progressTextView.setText("Uploading...");
        else if (percent < 70) progressTextView.setText("Processing...");
        else if (percent < 100) progressTextView.setText("Almost done...");
        else progressTextView.setText("Complete!");
    }

    private void dismissProgressOverlay() {
        if (progressView != null) {
            ViewGroup parent = (ViewGroup) progressView.getParent();
            if (parent != null) parent.removeView(progressView);
            progressView = null;
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
