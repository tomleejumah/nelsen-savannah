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


//todo save status in firebase
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
            if (submitted && currentIndex == sections.size() - 1) {
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

        // First, get age to determine flow
        int age = prefs.getInt("user_age", -1);
        boolean hasJob = prefs.getBoolean("user_has_job", false);

        if (age == -1) {
            list.add(createAgeSection());
            return list;
        }

        // Branch based on age
        if (age >= 7 && age <= 15) {
            // Kids/Early Teens - Discovery & Foundation
            list.addAll(buildKidsQuestionnaire());
        } else if (age >= 16 && age <= 21) {
            // Late Teens/Young Adults - Identity & Direction
            list.addAll(buildYouthQuestionnaire());
        } else {
            // Adults (22+) - Branch by employment status
            if (!hasJobInfoCollected()) {
                list.add(createEmploymentStatusSection());
                return list;
            }

            if (hasJob) {
                list.addAll(buildEmployedAdultQuestionnaire());
            } else {
                list.addAll(buildUnemployedAdultQuestionnaire());
            }
        }

        return list;
    }

    private Section createAgeSection() {
        return new Section(
                "age_selection",
                "Welcome! Let's Get Started",
                Arrays.asList(
                        new Question("age", "How old are you?", QuestionType.RADIO,
                                Arrays.asList(
                                        "7-10 years",
                                        "11-15 years",
                                        "16-18 years",
                                        "19-21 years",
                                        "22-25 years",
                                        "26-30 years",
                                        "31-40 years",
                                        "41+ years"
                                ))
                )
        );
    }

    private Section createEmploymentStatusSection() {
        return new Section(
                "employment_status",
                "Your Current Situation",
                Arrays.asList(
                        new Question("has_job", "What's your current employment status?", QuestionType.RADIO,
                                Arrays.asList(
                                        "Employed full-time",
                                        "Employed part-time",
                                        "Self-employed/Freelance",
                                        "Student",
                                        "Unemployed & seeking work",
                                        "Unemployed & not seeking work",
                                        "Other"
                                ))
                )
        );
    }

    private List<Section> buildKidsQuestionnaire() {
        List<Section> sections = new ArrayList<>();

        // Education & Learning
        sections.add(new Section(
                "kids_education",
                "Education & Learning",
                Arrays.asList(
                        new Question("edu_q1", "How do you feel about school?", QuestionType.RADIO,
                                Arrays.asList("I love it!", "It's okay", "I don't like it much", "I really don't like it")),
                        new Question("edu_q2", "What subjects do you enjoy most?", QuestionType.CHECKBOX,
                                Arrays.asList("Math", "Science", "Languages", "Arts", "Sports/PE", "Music", "History", "Technology/Computers")),
                        new Question("edu_q3", "Do you enjoy solving puzzles or problems?", QuestionType.RADIO,
                                Arrays.asList("Love it!", "Sometimes", "Not really", "No")),
                        new Question("edu_q4", "Do you like reading books or stories?", QuestionType.RADIO,
                                Arrays.asList("Yes, a lot", "Sometimes", "Not much", "No")),
                        new Question("edu_q5", "What's something new you'd like to learn?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        // Social & Character
        sections.add(new Section(
                "kids_social",
                "About You & Friends",
                Arrays.asList(
                        new Question("soc_q1", "Do friends come to you for help or advice?", QuestionType.RADIO,
                                Arrays.asList("Yes, often", "Sometimes", "Rarely", "No")),
                        new Question("soc_q2", "How do you feel about working in groups?", QuestionType.RADIO,
                                Arrays.asList("I love leading", "I like helping", "I prefer following", "I prefer working alone")),
                        new Question("soc_q3", "What describes you best?", QuestionType.CHECKBOX,
                                Arrays.asList("I'm a good listener", "I like to talk and share", "I cheer others up", "I solve problems", "I'm creative")),
                        new Question("soc_q4", "Do you feel confident speaking in front of many people?", QuestionType.RADIO,
                                Arrays.asList("Yes, I enjoy it", "Yes, but I get nervous", "No, I don't like it", "I've never tried")),
                        new Question("soc_q5", "What makes you a good friend?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        // Talents & Interests
        sections.add(new Section(
                "kids_talents",
                "Talents & Hobbies",
                Arrays.asList(
                        new Question("tal_q1", "What activities do you enjoy?", QuestionType.CHECKBOX,
                                Arrays.asList("Drawing/Painting", "Singing/Dancing", "Acting/Drama", "Sports", "Coding/Gaming", "Building things", "Playing instruments", "Writing stories")),
                        new Question("tal_q2", "Do you prefer indoor or outdoor activities?", QuestionType.RADIO,
                                Arrays.asList("Outdoor", "Indoor", "Both equally")),
                        new Question("tal_q3", "Have you won any awards or competitions?", QuestionType.RADIO,
                                Arrays.asList("Yes, many", "Yes, a few", "Not yet", "I don't compete")),
                        new Question("tal_q4", "What's your favorite hobby or activity?", QuestionType.TEXT, new ArrayList<>()),
                        new Question("tal_q5", "If you could learn any skill, what would it be?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        return sections;
    }

    private List<Section> buildYouthQuestionnaire() {
        List<Section> sections = new ArrayList<>();

        // Education & Career Exploration
        sections.add(new Section(
                "youth_education",
                "Education & Future Goals",
                Arrays.asList(
                        new Question("edu_q1", "What's your current education level?", QuestionType.RADIO,
                                Arrays.asList("High school", "College/University", "Vocational training", "Graduated", "Not in school")),
                        new Question("edu_q2", "What field interests you most?", QuestionType.CHECKBOX,
                                Arrays.asList("Technology/IT", "Business/Finance", "Healthcare", "Arts/Design", "Education", "Engineering", "Social Sciences", "Trades/Skills", "Not sure yet")),
                        new Question("edu_q3", "Do you have a clear career direction?", QuestionType.RADIO,
                                Arrays.asList("Yes, very clear", "Have some ideas", "Exploring options", "No idea yet")),
                        new Question("edu_q4", "What motivates you most?", QuestionType.CHECKBOX,
                                Arrays.asList("Making money", "Helping people", "Being creative", "Solving problems", "Being independent", "Making a difference")),
                        new Question("edu_q5", "What's your biggest challenge right now?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        // Skills & Development
        sections.add(new Section(
                "youth_skills",
                "Skills & Strengths",
                Arrays.asList(
                        new Question("skl_q1", "What are you naturally good at?", QuestionType.CHECKBOX,
                                Arrays.asList("Communication", "Leadership", "Problem-solving", "Creativity", "Technical skills", "Organization", "Teamwork", "Teaching others")),
                        new Question("skl_q2", "Do you have any work experience?", QuestionType.RADIO,
                                Arrays.asList("Yes, full-time", "Yes, part-time", "Yes, internship/volunteer", "No, not yet")),
                        new Question("skl_q3", "What skills do you want to develop?", QuestionType.CHECKBOX,
                                Arrays.asList("Technical/Computer skills", "Communication", "Leadership", "Financial literacy", "Language skills", "Creative skills", "Business skills")),
                        new Question("skl_q4", "Are you involved in any clubs, sports, or organizations?", QuestionType.RADIO,
                                Arrays.asList("Yes, very active", "Yes, somewhat", "Not currently", "No")),
                        new Question("skl_q5", "What achievement are you most proud of?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        // Personal Development
        sections.add(new Section(
                "youth_personal",
                "Personal Growth",
                Arrays.asList(
                        new Question("per_q1", "How would you describe your confidence level?", QuestionType.RADIO,
                                Arrays.asList("Very confident", "Somewhat confident", "Building confidence", "Struggling with confidence")),
                        new Question("per_q2", "What do you need most right now?", QuestionType.CHECKBOX,
                                Arrays.asList("Career guidance", "Skill development", "Mentorship", "Networking opportunities", "Financial advice", "Emotional support")),
                        new Question("per_q3", "Are you comfortable with public speaking/presentations?", QuestionType.RADIO,
                                Arrays.asList("Yes, I enjoy it", "Yes, but nervous", "Working on it", "Very uncomfortable")),
                        new Question("per_q4", "How do you handle challenges?", QuestionType.RADIO,
                                Arrays.asList("Face them head-on", "Think them through", "Ask for help", "Sometimes avoid them")),
                        new Question("per_q5", "What's your biggest dream or goal?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        return sections;
    }

    // ============= EMPLOYED ADULTS QUESTIONNAIRE (22+) =============
    private List<Section> buildEmployedAdultQuestionnaire() {
        List<Section> sections = new ArrayList<>();

        // Career Status
        sections.add(new Section(
                "adult_career",
                "Your Career Journey",
                Arrays.asList(
                        new Question("car_q1", "What industry do you work in?", QuestionType.TEXT, new ArrayList<>()),
                        new Question("car_q2", "How long have you been in your current role?", QuestionType.RADIO,
                                Arrays.asList("Less than 1 year", "1-3 years", "3-5 years", "5-10 years", "10+ years")),
                        new Question("car_q3", "How satisfied are you with your current job?", QuestionType.RADIO,
                                Arrays.asList("Very satisfied", "Satisfied", "Neutral", "Dissatisfied", "Very dissatisfied")),
                        new Question("car_q4", "What's your primary career goal?", QuestionType.RADIO,
                                Arrays.asList("Advance in current field", "Switch careers", "Start own business", "Better work-life balance", "Higher income", "More meaningful work")),
                        new Question("car_q5", "What's your biggest career challenge?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        // Professional Development
        sections.add(new Section(
                "adult_development",
                "Growth & Development",
                Arrays.asList(
                        new Question("dev_q1", "What areas do you want to improve?", QuestionType.CHECKBOX,
                                Arrays.asList("Leadership skills", "Technical expertise", "Communication", "Strategic thinking", "Time management", "Networking", "Industry knowledge")),
                        new Question("dev_q2", "Are you in a leadership position?", QuestionType.RADIO,
                                Arrays.asList("Yes, senior leadership", "Yes, mid-level management", "Yes, team lead", "No, individual contributor")),
                        new Question("dev_q3", "What kind of mentorship are you seeking?", QuestionType.CHECKBOX,
                                Arrays.asList("Career advancement", "Skill development", "Industry insights", "Work-life balance", "Entrepreneurship", "Personal branding", "None currently")),
                        new Question("dev_q4", "How often do you upskill or learn new things?", QuestionType.RADIO,
                                Arrays.asList("Constantly", "Regularly", "Occasionally", "Rarely")),
                        new Question("dev_q5", "What professional achievement are you most proud of?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        // Goals & Aspirations
        sections.add(new Section(
                "adult_goals",
                "Future Aspirations",
                Arrays.asList(
                        new Question("gol_q1", "Where do you see yourself in 3-5 years?", QuestionType.CHECKBOX,
                                Arrays.asList("Senior position in current company", "Different company, same field", "Different career entirely", "Running my own business", "Freelancing/Consulting", "More education/certifications")),
                        new Question("gol_q2", "What's holding you back from your goals?", QuestionType.CHECKBOX,
                                Arrays.asList("Lack of skills", "Limited opportunities", "Financial constraints", "Lack of confidence", "Work-life balance", "Unclear direction", "Nothing major")),
                        new Question("gol_q3", "Are you interested in mentoring others?", QuestionType.RADIO,
                                Arrays.asList("Yes, very interested", "Yes, somewhat", "Maybe in the future", "No")),
                        new Question("gol_q4", "What impact do you want to make?", QuestionType.CHECKBOX,
                                Arrays.asList("Build wealth", "Help my community", "Create jobs", "Solve important problems", "Inspire others", "Leave a legacy")),
                        new Question("gol_q5", "What support would help you achieve your goals?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        return sections;
    }

    // ============= UNEMPLOYED ADULTS QUESTIONNAIRE (22+) =============
    private List<Section> buildUnemployedAdultQuestionnaire() {
        List<Section> sections = new ArrayList<>();

        // Current Situation
        sections.add(new Section(
                "unemployed_situation",
                "Your Current Situation",
                Arrays.asList(
                        new Question("sit_q1", "What's your employment situation?", QuestionType.RADIO,
                                Arrays.asList("Actively job searching", "Taking a break", "Studying/Retraining", "Starting a business", "Dealing with personal matters", "Other")),
                        new Question("sit_q2", "What's your highest education level?", QuestionType.RADIO,
                                Arrays.asList("High school", "Diploma/Certificate", "Bachelor's degree", "Master's degree", "PhD", "Other")),
                        new Question("sit_q3", "Do you have previous work experience?", QuestionType.RADIO,
                                Arrays.asList("Yes, extensive (5+ years)", "Yes, moderate (2-5 years)", "Yes, limited (< 2 years)", "No, first-time job seeker")),
                        new Question("sit_q4", "What field were you in / want to enter?", QuestionType.TEXT, new ArrayList<>()),
                        new Question("sit_q5", "How long have you been job searching?", QuestionType.RADIO,
                                Arrays.asList("Just started", "1-3 months", "3-6 months", "6-12 months", "Over a year", "Not currently searching")),
                        new Question("sit_q6", "What's your biggest challenge right now?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        // Skills & Readiness
        sections.add(new Section(
                "unemployed_skills",
                "Skills & Preparation",
                Arrays.asList(
                        new Question("skl_q1", "What skills do you have?", QuestionType.CHECKBOX,
                                Arrays.asList("Technical/IT skills", "Communication", "Leadership", "Sales/Marketing", "Finance/Accounting", "Creative/Design", "Teaching/Training", "Trades/Manual skills")),
                        new Question("skl_q2", "What skills do you need to develop?", QuestionType.CHECKBOX,
                                Arrays.asList("Job search skills", "Interview techniques", "Resume/CV writing", "Technical skills", "Soft skills", "Industry knowledge", "Networking")),
                        new Question("skl_q3", "Are you willing to retrain or learn new skills?", QuestionType.RADIO,
                                Arrays.asList("Yes, absolutely", "Yes, if needed", "Maybe", "No, want to use current skills")),
                        new Question("skl_q4", "What type of work are you looking for?", QuestionType.CHECKBOX,
                                Arrays.asList("Full-time employment", "Part-time work", "Freelance/Contract", "Remote work", "Internship", "Apprenticeship", "Self-employment")),
                        new Question("skl_q5", "What would make you job-ready?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        // Support Needs
        sections.add(new Section(
                "unemployed_support",
                "What You Need",
                Arrays.asList(
                        new Question("sup_q1", "What support would help you most?", QuestionType.CHECKBOX,
                                Arrays.asList("Job referrals", "Skills training", "Career counseling", "Resume help", "Interview prep", "Networking opportunities", "Confidence building", "Financial planning")),
                        new Question("sup_q2", "Are you open to changing careers?", QuestionType.RADIO,
                                Arrays.asList("Yes, actively considering", "Yes, if necessary", "Maybe", "No, committed to current field")),
                        new Question("sup_q3", "Do you have financial constraints affecting your job search?", QuestionType.RADIO,
                                Arrays.asList("Yes, significant", "Yes, some", "Not really", "No")),
                        new Question("sup_q4", "Would you like a mentor to guide you?", QuestionType.RADIO,
                                Arrays.asList("Yes, definitely", "Yes, would be helpful", "Maybe", "No, I'm good")),
                        new Question("sup_q5", "What's your ideal next step?", QuestionType.TEXT, new ArrayList<>())
                )
        ));

        return sections;
    }

    private boolean hasJobInfoCollected() {
        return prefs.contains("ans_employment_status_has_job");
    }

    // Update the age parsing logic in loadSection
    private int parseAge(String ageRange) {
        if (ageRange.contains("7-10")) return 10;
        if (ageRange.contains("11-15")) return 15;
        if (ageRange.contains("16-18")) return 18;
        if (ageRange.contains("19-21")) return 21;
        if (ageRange.contains("22-25")) return 25;
        if (ageRange.contains("26-30")) return 30;
        if (ageRange.contains("31-40")) return 35;
        return 45; // 41+
    }

    // Update the employment status parsing
    private boolean parseEmploymentStatus(String status) {
        return status.contains("Employed") || status.contains("Self-employed");
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
                    rg.removeAllViews();
                    for (String opt : q.getOptions()) {
                        RadioButton rb = new RadioButton(this);
                        rb.setText(opt);
                        rg.addView(rb);
                    }

                    // Restore saved value
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

                    // Special handling for age section
                    if (section.getId().equals("age_selection")) {
                        rg.setOnCheckedChangeListener((group, checkedId) -> {
                            RadioButton sel = group.findViewById(checkedId);
                            if (sel != null) {
                                int age = parseAge(sel.getText().toString());
                                prefs.edit()
                                        .putString(key, sel.getText().toString())
                                        .putInt("user_age", age)
                                        .apply();

                                // Rebuild sections based on age
                                sections = buildSections();
                                currentIndex = 0;
                                loadSection(currentIndex);
                            }
                        });
                    }
                    // Special handling for employment section
                    else if (section.getId().equals("employment_status")) {
                        rg.setOnCheckedChangeListener((group, checkedId) -> {
                            RadioButton sel = group.findViewById(checkedId);
                            if (sel != null) {
                                boolean hasJob = parseEmploymentStatus(sel.getText().toString());
                                prefs.edit()
                                        .putString(key, sel.getText().toString())
                                        .putBoolean("user_has_job", hasJob)
                                        .apply();

                                // Rebuild sections based on employment
                                sections = buildSections();
                                currentIndex = 0;
                                loadSection(currentIndex);
                            }
                        });
                    }
                    // Normal radio button handling
                    else {
                        rg.setOnCheckedChangeListener((group, checkedId) -> {
                            RadioButton sel = group.findViewById(checkedId);
                            if (sel != null) {
                                prefs.edit().putString(key, sel.getText().toString()).apply();
                                checkFormComplete();
                            }
                        });
                    }
                    break;

                case CHECKBOX:
                    checkboxContainer.setVisibility(View.VISIBLE);
                    checkboxContainer.removeAllViews();

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

    private void checkFormComplete() {
        boolean complete = true;

        for (Question q : sections.get(currentIndex).getQuestions()) {
            String key = answerKey(sections.get(currentIndex).getId(), q.getId());

            String val = prefs.getString(key, "");
            if (val.trim().isEmpty()) complete = false;

        }

        btnNext.setEnabled(complete);
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