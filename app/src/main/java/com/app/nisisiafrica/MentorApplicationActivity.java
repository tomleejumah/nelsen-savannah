package com.app.nisisiafrica;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.app.nisisiafrica.Utils.Util;
import com.app.nisisiafrica.ViewModel.UserViewModel;
import com.app.nisisiafrica.data.Model.Question;
import com.app.nisisiafrica.data.Model.QuestionType;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MentorApplicationActivity extends AppCompatActivity {
    private static final String PREFS = "MentorApplicationPrefs";
    private static final String KEY_SUBMITTED = "submitted";
    private static final int PICK_DOCUMENTS = 100;
    private static final int PICK_VIDEO = 101;
    private static final int RECORD_VIDEO = 102;
    private static final int PERMISSION_CAMERA = 200;

    private TextView tvCategory;
    private LinearLayout questionsContainer;
    private Button btnApply;
    private ProgressBar progressBar;
    private TextView tvProgress;

    private SharedPreferences prefs;
    private FirebaseStorage storage;
    private DatabaseReference database;
    private List<Question> questions;
    private List<Uri> documentUris = new ArrayList<>();
    private Uri videoUri;
    private String userName, userEmail;
    private LinearLayout currentDocumentsContainer;
    private FrameLayout currentVideoContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mentor_application);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        UserViewModel viewModel = new ViewModelProvider(this).get(UserViewModel.class);

        tvCategory = findViewById(R.id.tvCategory);
        questionsContainer = findViewById(R.id.questionsContainer);
        btnApply = findViewById(R.id.btnApply);
        progressBar = findViewById(R.id.progressBar);
        tvProgress = findViewById(R.id.tvProgress);

        tvCategory.setText("🌟 Mentor Application");

        storage = FirebaseStorage.getInstance();
        database = FirebaseDatabase.getInstance().getReference();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Get user from Room
        viewModel.fetchingCurrentUserDataFromDB(Util.getState(Constants.CURRENT_USER_ID, "")).observe(this, fetchedUserData -> {
            userName = fetchedUserData.getFirstName() + " " + fetchedUserData.getLastName();
            userEmail = fetchedUserData.getEmail();
        });

        questions = buildQuestions();
        loadQuestions();

        btnApply.setEnabled(false);
        btnApply.setOnClickListener(v -> submitApplication());

        if (prefs.getBoolean(KEY_SUBMITTED, false)) {
            lockForm();
        }
    }

    private List<Question> buildQuestions() {
        List<Question> list = new ArrayList<>();

        list.add(new Question("expertise", "What areas can you mentor in?", QuestionType.CHECKBOX,
                Arrays.asList("Education", "Technology", "Arts", "Sports", "Life Skills", "Career")));

        list.add(new Question("experience", "Years of experience", QuestionType.RADIO,
                Arrays.asList("0-2 years", "3-5 years", "6-10 years", "10+ years")));

        list.add(new Question("availability", "Weekly time commitment", QuestionType.RADIO,
                Arrays.asList("1-2 hours", "3-5 hours", "6-10 hours", "10+ hours")));

        list.add(new Question("approach", "Preferred mentoring style", QuestionType.CHECKBOX,
                Arrays.asList("One-on-one", "Group sessions", "Online", "In-person")));

        list.add(new Question("motivation", "Why do you want to mentor?", QuestionType.TEXT, new ArrayList<>()));

        list.add(new Question("achievements", "Notable achievements/qualifications", QuestionType.TEXT, new ArrayList<>()));

        list.add(new Question("documents", "Upload Documents (CV, Certificates)", QuestionType.DOCUMENT_UPLOAD, new ArrayList<>()));

        list.add(new Question("video", "Introduction Video (Max 2 min)", QuestionType.VIDEO_UPLOAD, new ArrayList<>()));

        return list;
    }

    private void loadQuestions() {
        questionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Question q : questions) {
            View item = inflater.inflate(R.layout.question_item, questionsContainer, false);
            TextView tvQ = item.findViewById(R.id.tvQuestion);
            RadioGroup rg = item.findViewById(R.id.rgOptions);
            LinearLayout checkboxContainer = item.findViewById(R.id.checkboxContainer);
            EditText et = item.findViewById(R.id.etAnswer);
            LinearLayout docSection = item.findViewById(R.id.documentUploadSection);
            LinearLayout videoSection = item.findViewById(R.id.videoUploadSection);

            tvQ.setText(q.getText());
            String key = "ans_" + q.getId();

            switch (q.getType()) {
                case TEXT:
                    et.setVisibility(View.VISIBLE);
                    et.setMinLines(3);
                    et.setText(prefs.getString(key, ""));
                    et.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            prefs.edit().putString(key, s.toString().trim()).apply();
                            checkFormComplete();
                        }
                    });
                    break;

                case RADIO:
                    rg.setVisibility(View.VISIBLE);
                    for (String opt : q.getOptions()) {
                        RadioButton rb = new RadioButton(this);
                        rb.setText(opt);
                        rg.addView(rb);
                    }
                    String savedRadio = prefs.getString(key, null);
                    if (savedRadio != null) {
                        for (int i = 0; i < rg.getChildCount(); i++) {
                            RadioButton rb = (RadioButton) rg.getChildAt(i);
                            if (rb.getText().toString().equals(savedRadio)) rb.setChecked(true);
                        }
                    }
                    rg.setOnCheckedChangeListener((group, checkedId) -> {
                        RadioButton sel = group.findViewById(checkedId);
                        if (sel != null) {
                            prefs.edit().putString(key, sel.getText().toString()).apply();
                            checkFormComplete();
                        }
                    });
                    break;

                case CHECKBOX:
                    checkboxContainer.setVisibility(View.VISIBLE);
                    String savedCsv = prefs.getString(key, "");
                    HashSet<String> savedSet = new HashSet<>(Arrays.asList(savedCsv.split("\\|")));

                    for (String opt : q.getOptions()) {
                        CheckBox cb = new CheckBox(this);
                        cb.setText(opt);
                        cb.setChecked(savedSet.contains(opt) && !savedCsv.isEmpty());
                        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            List<String> chosen = new ArrayList<>();
                            for (int i = 0; i < checkboxContainer.getChildCount(); i++) {
                                CheckBox c = (CheckBox) checkboxContainer.getChildAt(i);
                                if (c.isChecked()) chosen.add(c.getText().toString());
                            }
                            prefs.edit().putString(key, String.join("|", chosen)).apply();
                            checkFormComplete();
                        });
                        checkboxContainer.addView(cb);
                    }
                    break;

                case DOCUMENT_UPLOAD:
                    docSection.setVisibility(View.VISIBLE);
                    Button btnAddDocs = item.findViewById(R.id.btnAddDocuments);
                    currentDocumentsContainer = item.findViewById(R.id.documentsContainer);

                    btnAddDocs.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("application/pdf");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        startActivityForResult(intent, PICK_DOCUMENTS);
                    });
                    break;

                case VIDEO_UPLOAD:
                    videoSection.setVisibility(View.VISIBLE);
                    Button btnSelect = item.findViewById(R.id.btnSelectVideo);
                    Button btnRecord = item.findViewById(R.id.btnRecordVideo);
                    currentVideoContainer = item.findViewById(R.id.videoContainer);

                    btnSelect.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("video/*");
                        startActivityForResult(intent, PICK_VIDEO);
                    });

                    btnRecord.setOnClickListener(v -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
                        } else {
                            recordVideo();
                        }
                    });
                    break;
            }

            questionsContainer.addView(item);
        }
    }

    private void recordVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 120);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);

        File videoFile = new File(getExternalFilesDir(null), "mentor_video_" + System.currentTimeMillis() + ".mp4");
        Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", videoFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

        startActivityForResult(intent, RECORD_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == PICK_DOCUMENTS && data != null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    addDocumentPreview(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                addDocumentPreview(data.getData());
            }
        } else if (requestCode == PICK_VIDEO && data != null) {
            Uri uri = data.getData();
            if (isVideoValid(uri)) {
                videoUri = uri;
                addVideoPreview(uri);
            } else {
                Toast.makeText(this, "Video must be under 2 minutes", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == RECORD_VIDEO && data != null) {
            Uri uri = data.getData();
            if (isVideoValid(uri)) {
                videoUri = uri;
                addVideoPreview(uri);
            }
        }
    }

    private void addDocumentPreview(Uri uri) {
        documentUris.add(uri);
        View docView = getLayoutInflater().inflate(R.layout.document_item, currentDocumentsContainer, false);

        TextView tvFileName = docView.findViewById(R.id.tvFileName);
        ImageButton btnRemove = docView.findViewById(R.id.btnRemoveDoc);

        tvFileName.setText(getFileName(uri));
        btnRemove.setOnClickListener(v -> {
            currentDocumentsContainer.removeView(docView);
            documentUris.remove(uri);
            checkFormComplete();
        });

        currentDocumentsContainer.addView(docView);
        checkFormComplete();
    }

    private void addVideoPreview(Uri uri) {
        currentVideoContainer.removeAllViews();
        View videoView = getLayoutInflater().inflate(R.layout.video_item, currentVideoContainer, false);

        ImageView ivThumbnail = videoView.findViewById(R.id.ivVideoThumbnail);
        TextView tvDuration = videoView.findViewById(R.id.tvVideoDuration);
        ImageButton btnRemove = videoView.findViewById(R.id.btnRemoveVideo);

        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, uri);

            Bitmap thumbnail = retriever.getFrameAtTime(0);
            ivThumbnail.setImageBitmap(thumbnail);

            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = Long.parseLong(duration);
            tvDuration.setText(formatDuration(durationMs));

            retriever.release();
        } catch (Exception e) {
            ivThumbnail.setImageResource(R.drawable.ic_video_place_holder);
        }

        btnRemove.setOnClickListener(v -> {
            currentVideoContainer.removeAllViews();
            videoUri = null;
            checkFormComplete();
        });

        currentVideoContainer.addView(videoView);
        checkFormComplete();
    }

    private boolean isVideoValid(Uri uri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, uri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = Long.parseLong(duration);
            retriever.release();
            return durationMs <= 120000;
        } catch (Exception e) {
            return false;
        }
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void checkFormComplete() {
        boolean complete = true;

        for (Question q : questions) {
            String key = "ans_" + q.getId();

            if (q.getType() == QuestionType.DOCUMENT_UPLOAD) {
                if (documentUris.isEmpty()) complete = false;
            } else if (q.getType() == QuestionType.VIDEO_UPLOAD) {
                if (videoUri == null) complete = false;
            } else {
                String val = prefs.getString(key, "");
                if (val.trim().isEmpty()) complete = false;
            }
        }

        btnApply.setEnabled(complete);
    }

    private void submitApplication() {
        progressBar.setVisibility(View.VISIBLE);
        tvProgress.setVisibility(View.VISIBLE);
        btnApply.setEnabled(false);

        uploadDocuments();
    }

    private void uploadDocuments() {
        List<String> documentUrls = new ArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger(0);

        if (documentUris.isEmpty()) {
            uploadVideo(new ArrayList<>());
            return;
        }

        for (Uri uri : documentUris) {
            String filename = "doc_" + System.currentTimeMillis() + "_" + uploadCount.get() + ".pdf";
            StorageReference ref = storage.getReference().child("mentor_applications/" + userEmail + "/documents/" + filename);

            ref.putFile(uri)
                    .addOnProgressListener(snapshot -> {
                        int progress = (int) ((100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount());
                        tvProgress.setText("Uploading docs: " + (uploadCount.get() + 1) + "/" + documentUris.size() + " (" + progress + "%)");
                    })
                    .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                        documentUrls.add(downloadUri.toString());
                        if (uploadCount.incrementAndGet() == documentUris.size()) {
                            uploadVideo(documentUrls);
                        }
                    }))
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show();
                        resetUploadState();
                    });
        }
    }

    private void uploadVideo(List<String> documentUrls) {
        if (videoUri == null) {
            saveToDatabase(documentUrls, null);
            return;
        }

        String filename = "video_" + System.currentTimeMillis() + ".mp4";
        StorageReference ref = storage.getReference().child("mentor_applications/" + userEmail + "/videos/" + filename);

        ref.putFile(videoUri)
                .addOnProgressListener(snapshot -> {
                    int progress = (int) ((100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount());
                    tvProgress.setText("Uploading video: " + progress + "%");
                })
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(downloadUri ->
                        saveToDatabase(documentUrls, downloadUri.toString())))
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Video upload failed", Toast.LENGTH_SHORT).show();
                    resetUploadState();
                });
    }

    private void saveToDatabase(List<String> documentUrls, String videoUrl) {
        Map<String, Object> application = new HashMap<>();
        application.put("name", userName);
        application.put("email", userEmail);
        application.put("timestamp", System.currentTimeMillis());
        application.put("documentUrls", documentUrls);
        application.put("videoUrl", videoUrl);

        for (Question q : questions) {
            if (q.getType() != QuestionType.DOCUMENT_UPLOAD && q.getType() != QuestionType.VIDEO_UPLOAD) {
                application.put(q.getId(), prefs.getString("ans_" + q.getId(), ""));
            }
        }

        database.child("mentor_applications").child(userEmail.replace(".", "_")).setValue(application)
                .addOnSuccessListener(aVoid -> {
                    prefs.edit().putBoolean(KEY_SUBMITTED, true).apply();
                    progressBar.setVisibility(View.GONE);
                    tvProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "Application submitted!", Toast.LENGTH_LONG).show();
                    lockForm();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to submit", Toast.LENGTH_SHORT).show();
                    resetUploadState();
                });
    }

    private void resetUploadState() {
        progressBar.setVisibility(View.GONE);
        tvProgress.setVisibility(View.GONE);
        btnApply.setEnabled(true);
    }

    private void lockForm() {
        setEnabledRecursive(questionsContainer, false);
        questionsContainer.setAlpha(0.6f);
        btnApply.setEnabled(false);
        btnApply.setText("Application Submitted");
    }

    private void setEnabledRecursive(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                setEnabledRecursive(((ViewGroup) v).getChildAt(i), enabled);
            }
        }
    }

    private String getFileName(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            String name = cursor.getString(idx);
            cursor.close();
            return name;
        }
        return "document.pdf";
    }
}