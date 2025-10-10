package com.app.nisisiafrica;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.app.nisisiafrica.data.Model.Question;
import com.app.nisisiafrica.data.Model.QuestionType;
import com.app.nisisiafrica.data.Model.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class QuestionnaireActivity extends AppCompatActivity {

    private static final String PREFS = "QuestionnairePrefs";
    private static final String KEY_PROGRESS = "progress_index";
    private static final String KEY_SUBMITTED = "submitted_flag";
    private TextView tvCategory;
    private LinearLayout questionsContainer;
    private Button btnPrev, btnNext;

    private SharedPreferences prefs;
    private List<Section> sections;
    private int currentIndex = 0;
    private boolean submitted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_questionaire);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        tvCategory = findViewById(R.id.tvCategory);
        questionsContainer = findViewById(R.id.questionsContainer);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        submitted = prefs.getBoolean(KEY_SUBMITTED, false);

        sections = buildSections();

        // Resume to earliest incomplete section, else to saved progress
        int saved = prefs.getInt(KEY_PROGRESS, 0);
        currentIndex = findEarliestIncompleteIndex();
        if (currentIndex == -1) currentIndex = Math.min(saved, sections.size() - 1);


        loadSection(currentIndex);

        btnPrev.setOnClickListener(v -> {
            if (currentIndex == 0) {
                finish();
            }
            if (currentIndex > 0) {
                currentIndex--;
                prefs.edit().putInt(KEY_PROGRESS, currentIndex).apply();
                loadSection(currentIndex);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (submitted && currentIndex == sections.size() - 1){
                Toast.makeText(this, "This form is already submitted", Toast.LENGTH_LONG).show();
                return;
            }
            if (!isSectionComplete(sections.get(currentIndex))) {
                Toast.makeText(this, "Please answer all questions.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentIndex < sections.size() - 1) {
                currentIndex++;
                prefs.edit().putInt(KEY_PROGRESS, currentIndex).apply();
                loadSection(currentIndex);
            } else {
                // Submit
                prefs.edit().putBoolean(KEY_SUBMITTED, true).apply();
                submitted = true;
                disableAllInputs();
                btnPrev.setEnabled(false);
                btnNext.setEnabled(false);
                btnNext.setText("Submitted");
                Toast.makeText(this, "Submitted. This form is now locked.", Toast.LENGTH_LONG).show();
                // todo NOTE:  persist to Room here. For now, answers are in SharedPreferences.
            }
        });
    }

    private List<Section> buildSections() {
        List<Section> list = new ArrayList<>();

        // 🎓 Education
        list.add(new Section(
                "education",
                "🎓 Education",
                Arrays.asList(
                        new Question("edu_q1", "Do you enjoy solving math puzzles?", QuestionType.RADIO,
                                Arrays.asList("Love it", "Sometimes", "Not much")),
                        new Question("edu_q2", "Do you like reading storybooks?", QuestionType.RADIO,
                                Arrays.asList("Yes", "No")),
                        new Question("edu_q3", "Which do you prefer?", QuestionType.CHECKBOX,
                                Arrays.asList("Science", "Art")), // allow both
                        new Question("edu_q4", "Do you enjoy learning new words?", QuestionType.RADIO,
                                Arrays.asList("Yes", "Sometimes", "No")),
                        new Question("edu_q5", "Write one thing you love learning right now", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        //  Mentorship & Character
        list.add(new Section(
                "mentorship",
                "🌱 Mentorship & Character",
                Arrays.asList(
                        new Question("men_q1", "Do friends come to you for advice?", QuestionType.RADIO,
                                Arrays.asList("Yes", "No")),
                        new Question("men_q2", "Do you enjoy leading group work?", QuestionType.RADIO,
                                Arrays.asList("Yes", "Sometimes", "No")),
                        new Question("men_q3", "Do you like helping others learn?", QuestionType.RADIO,
                                Arrays.asList("Yes", "No")),
                        new Question("men_q4", "Pick what fits you", QuestionType.CHECKBOX,
                                Arrays.asList("I listen more", "I talk more", "I like cheering others")),
                        new Question("men_q5", "Do you feel confident speaking to many people?", QuestionType.RADIO,
                                Arrays.asList("Yes", "Not really"))
                )
        ));

        //  Talent & Skills
        list.add(new Section(
                "talent",
                "🎭 Talent & Skills",
                Arrays.asList(
                        new Question("tal_q1", "Choose your interests", QuestionType.CHECKBOX,
                                Arrays.asList("Drawing/Painting", "Singing/Dancing", "Acting/Storytelling", "Coding/Puzzles", "Sports", "Fixing/Building")),
                        new Question("tal_q2", "Do you enjoy playing instruments?", QuestionType.RADIO,
                                Arrays.asList("Yes", "No")),
                        new Question("tal_q3", "Do you prefer outdoor or indoor games?", QuestionType.RADIO,
                                Arrays.asList("Outdoor", "Indoor", "Both")),
                        new Question("tal_q4", "Do you like making up new games?", QuestionType.RADIO,
                                Arrays.asList("Yes", "Sometimes", "No")),
                        new Question("tal_q5", "Write your favorite hobby", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        return list;
    }

    private void loadSection(int index) {
        Section section = sections.get(index);
        tvCategory.setText(section.getTitle());
        questionsContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);

        for (Question q : section.getQuestions()) {
            View item = inflater.inflate(R.layout.question_item, questionsContainer, false);
            TextView tvQ = item.findViewById(R.id.tvQuestion);
            RadioGroup rg = item.findViewById(R.id.rgOptions);
            LinearLayout checkboxContainer = item.findViewById(R.id.checkboxContainer);
            EditText et = item.findViewById(R.id.etAnswer);

            tvQ.setText(q.getText());

            String key = answerKey(section.getId(), q.getId());

            switch (q.getType()) {
                case RADIO:
                    rg.setVisibility(View.VISIBLE);
                    // Build radio options
                    rg.removeAllViews();
                    for (int i = 0; i < q.getOptions().size(); i++) {
                        RadioButton rb = new RadioButton(this);
                        rb.setText(q.getOptions().get(i));
                        rg.addView(rb);
                    }
                    // Restore
                    String savedRadio = prefs.getString(key, null);
                    if (savedRadio != null) {
                        for (int i = 0; i < rg.getChildCount(); i++) {
                            RadioButton rb = (RadioButton) rg.getChildAt(i);
                            if (rb.getText().toString().equals(savedRadio)) {
                                rb.setChecked(true);
                                break;
                            }
                        }
                    }
                    // Save on change
                    rg.setOnCheckedChangeListener((group, checkedId) -> {
                        RadioButton sel = group.findViewById(checkedId);
                        if (sel != null) {
                            prefs.edit().putString(key, sel.getText().toString()).apply();
                        }
                    });
                    break;

                case CHECKBOX:
                    checkboxContainer.setVisibility(View.VISIBLE);
                    checkboxContainer.removeAllViews();
                    // restore set
                    String savedCsv = prefs.getString(key, "");
                    HashSet<String> savedSet = new HashSet<>();
                    if (!savedCsv.isEmpty()) {
                        savedSet.addAll(Arrays.asList(savedCsv.split("\\|")));
                    }
                    for (String opt : q.getOptions()) {
                        CheckBox cb = new CheckBox(this);
                        cb.setText(opt);
                        cb.setChecked(savedSet.contains(opt));
                        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            // collect all checked
                            List<String> chosen = new ArrayList<>();
                            for (int i = 0; i < checkboxContainer.getChildCount(); i++) {
                                CheckBox c = (CheckBox) checkboxContainer.getChildAt(i);
                                if (c.isChecked()) chosen.add(c.getText().toString());
                            }
                            prefs.edit().putString(key, String.join("|", chosen)).apply();
                        });
                        checkboxContainer.addView(cb);
                    }
                    break;

                case TEXT:
                    et.setVisibility(View.VISIBLE);
                    et.setText(prefs.getString(key, ""));
                    et.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                        @Override public void afterTextChanged(Editable s) {
                            prefs.edit().putString(key, s.toString().trim()).apply();
                        }
                    });
                    break;
            }

            questionsContainer.addView(item);
        }

        // Buttons state
        btnPrev.setText((currentIndex == 0) ? "Close" : "Prev");
        if (currentIndex == sections.size() - 1) {
            if (submitted) {
                btnNext.setText("Submitted");
            } else {
                btnNext.setText("Submit");
            }
        } else {
            btnNext.setText("Next");
        }


        if (submitted) {
            disableAllInputs();
        }
    }

    private boolean isSectionComplete(Section section) {
        for (Question q : section.getQuestions()) {
            String key = answerKey(section.getId(), q.getId());
            switch (q.getType()) {
                case RADIO:
                case TEXT:
                    String v = prefs.getString(key, null);
                    if (v == null || v.trim().isEmpty()) return false;
                    break;
                case CHECKBOX:
                    String csv = prefs.getString(key, "");
                    if (csv.trim().isEmpty()) return false;
                    break;
            }
        }
        return true;
    }

    private int findEarliestIncompleteIndex() {
        for (int i = 0; i < sections.size(); i++) {
            if (!isSectionCompleteSnapshot(sections.get(i))) return i;
        }
        return -1;
    }

    // snapshot check (no UI)
    private boolean isSectionCompleteSnapshot(Section section) {
        for (Question q : section.getQuestions()) {
            String key = answerKey(section.getId(), q.getId());
            switch (q.getType()) {
                case RADIO:
                case TEXT:
                    String v = prefs.getString(key, null);
                    if (v == null || v.trim().isEmpty()) return false;
                    break;
                case CHECKBOX:
                    String csv = prefs.getString(key, "");
                    if (csv.trim().isEmpty()) return false;
                    break;
            }
        }
        return true;
    }

    private void disableAllInputs() {
        setEnabledRecursive(questionsContainer, false);
        questionsContainer.setAlpha(0.6f);
        tvCategory.append("  •  (Locked)");
    }

    private void setEnabledRecursive(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) v;
            for (int i = 0; i < ll.getChildCount(); i++) {
                setEnabledRecursive(ll.getChildAt(i), enabled);
            }
        }
    }

    private String answerKey(String sectionId, String questionId) {
        return "ans_" + sectionId + "_" + questionId;
    }
}