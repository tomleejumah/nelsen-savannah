package com.app.nisisiafrica;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.data.Model.LmsModels;
import com.app.nisisiafrica.data.remote.ApiClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** L2/L3 mentor board — queue mark + students + assign. */
public class MentorBoardActivity extends AppCompatActivity {

    private LinearLayout root;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        TextView title = label("Teach board", 22, true);
        root.addView(title);
        status = label("Loading…", 14, false);
        root.addView(status);
        loadAll();
    }

    private void loadAll() {
        withBearer(bearer -> {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            String mentorId = u != null ? u.getUid() : "";
            ApiClient.getLmsService().submissionQueue(bearer).enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<LmsModels.QueueEnvelope> call,
                                       Response<LmsModels.QueueEnvelope> response) {
                    root.removeAllViews();
                    root.addView(label("Teach board", 22, true));
                    TextView back = label("← Back", 14, false);
                    back.setOnClickListener(v -> finish());
                    root.addView(back);

                    LmsModels.QueueEnvelope body = response.body();
                    root.addView(label("Marking queue", 18, true));
                    if (!response.isSuccessful() || body == null || !body.ok
                            || body.data == null || body.data.queue == null
                            || body.data.queue.isEmpty()) {
                        root.addView(label("Queue empty", 14, false));
                    } else {
                        for (LmsModels.QueueItemDto item : body.data.queue) {
                            TextView row = label(
                                    (item.lessonTitle != null ? item.lessonTitle : item.lessonId)
                                            + " · " + (item.menteeName != null ? item.menteeName : item.menteeId),
                                    14, true);
                            row.setPadding(0, dp(12), 0, dp(4));
                            row.setOnClickListener(v -> promptMark(item));
                            root.addView(row);
                            if (!TextUtils.isEmpty(item.text)) {
                                root.addView(label(item.text, 13, false));
                            }
                        }
                    }

                    ApiClient.getLmsService().menteeProgress(bearer, mentorId)
                            .enqueue(new Callback<>() {
                                @Override
                                public void onResponse(Call<LmsModels.MenteesEnvelope> call,
                                                       Response<LmsModels.MenteesEnvelope> response) {
                                    root.addView(label("Students", 18, true));
                                    LmsModels.MenteesEnvelope mb = response.body();
                                    if (response.isSuccessful() && mb != null && mb.ok
                                            && mb.data != null && mb.data.mentees != null) {
                                        for (LmsModels.MenteeProgressDto m : mb.data.mentees) {
                                            root.addView(label(
                                                    (m.displayName != null ? m.displayName : m.uid)
                                                            + " · " + m.trackId + " · "
                                                            + Math.round(m.trackPercent) + "%",
                                                    13, false));
                                        }
                                    } else {
                                        root.addView(label("No students", 14, false));
                                    }
                                    addAssignButton(bearer);
                                }

                                @Override
                                public void onFailure(Call<LmsModels.MenteesEnvelope> call, Throwable t) {
                                    root.addView(label("Students load failed", 14, false));
                                    addAssignButton(bearer);
                                }
                            });
                }

                @Override
                public void onFailure(Call<LmsModels.QueueEnvelope> call, Throwable t) {
                    if (status != null) status.setText("Network error");
                }
            });
        });
    }

    private void addAssignButton(String bearer) {
        TextView assign = label("＋ Assign work", 16, true);
        assign.setPadding(0, dp(20), 0, dp(8));
        assign.setOnClickListener(v -> promptAssign(bearer));
        root.addView(assign);
    }

    private void promptMark(LmsModels.QueueItemDto item) {
        final EditText score = new EditText(this);
        score.setInputType(InputType.TYPE_CLASS_NUMBER);
        score.setText("85");
        final EditText feedback = new EditText(this);
        feedback.setHint("Feedback");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(score);
        box.addView(feedback);
        new AlertDialog.Builder(this)
                .setTitle("Mark submission")
                .setView(box)
                .setPositiveButton("Pass", (d, w) ->
                        mark(item.id, parseScore(score.getText().toString(), 85), true,
                                feedback.getText().toString()))
                .setNeutralButton("Fail", (d, w) ->
                        mark(item.id, parseScore(score.getText().toString(), 60), false,
                                feedback.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptAssign(String bearer) {
        final EditText title = new EditText(this);
        title.setHint("Title");
        final EditText prompt = new EditText(this);
        prompt.setHint("Prompt");
        final EditText trackId = new EditText(this);
        trackId.setHint("trackId (optional)");
        final EditText uid = new EditText(this);
        uid.setHint("assignee uid (optional)");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(title);
        box.addView(prompt);
        box.addView(trackId);
        box.addView(uid);
        new AlertDialog.Builder(this)
                .setTitle("Assign work")
                .setView(box)
                .setPositiveButton("Assign", (d, w) -> {
                    String t = title.getText() != null ? title.getText().toString().trim() : "";
                    if (t.isEmpty()) {
                        Toast.makeText(this, "Title required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String tr = trackId.getText() != null ? trackId.getText().toString().trim() : "";
                    String asg = uid.getText() != null ? uid.getText().toString().trim() : "";
                    if (tr.isEmpty() && asg.isEmpty()) {
                        Toast.makeText(this, "Need trackId or uid", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ApiClient.getLmsService().createAssignment(bearer,
                                    new LmsModels.AssignmentBody(
                                            t,
                                            prompt.getText() != null ? prompt.getText().toString() : "",
                                            tr.isEmpty() ? null : tr,
                                            asg.isEmpty() ? null : asg))
                            .enqueue(new Callback<>() {
                                @Override
                                public void onResponse(Call<LmsModels.AssignmentEnvelope> call,
                                                       Response<LmsModels.AssignmentEnvelope> response) {
                                    Toast.makeText(MentorBoardActivity.this,
                                            response.isSuccessful() ? "Assigned" : "Failed",
                                            Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(Call<LmsModels.AssignmentEnvelope> call, Throwable t) {
                                    Toast.makeText(MentorBoardActivity.this,
                                            "Network error", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void mark(String id, int score, boolean passed, String feedback) {
        withBearer(bearer -> ApiClient.getLmsService()
                .markSubmission(bearer, id, new LmsModels.MarkBody(score, passed, feedback))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(Call<LmsModels.MarkEnvelope> call,
                                           Response<LmsModels.MarkEnvelope> response) {
                        Toast.makeText(MentorBoardActivity.this,
                                response.isSuccessful() ? "Marked" : "Mark failed",
                                Toast.LENGTH_SHORT).show();
                        loadAll();
                    }

                    @Override
                    public void onFailure(Call<LmsModels.MarkEnvelope> call, Throwable t) {
                        Toast.makeText(MentorBoardActivity.this,
                                "Network error", Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private static int parseScore(String raw, int fallback) {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(raw.trim())));
        } catch (Exception e) {
            return fallback;
        }
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setPadding(0, dp(6), 0, dp(2));
        tv.setGravity(Gravity.START);
        return tv;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void withBearer(BearerCallback cb) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Sign in required", Toast.LENGTH_SHORT).show();
            return;
        }
        user.getIdToken(false).addOnSuccessListener(r ->
                cb.onToken("Bearer " + r.getToken()));
    }

    private interface BearerCallback {
        void onToken(String bearer);
    }
}
