package com.app.nisisiafrica.data.Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Admin-updatable programme card (Firebase `programmes/{slug}`). */
public class ProgrammeItem {
    public String slug = "";
    public String title = "";
    public String blurb = "";
    public String audience = "";
    /** "maroon" | "blue" tag tint */
    public String tone = "maroon";
    public List<String> checklist = new ArrayList<>();
    public int order = 0;

    public ProgrammeItem() {}

    public ProgrammeItem(String slug, String title, String blurb, String audience,
                         String tone, List<String> checklist, int order) {
        this.slug = slug;
        this.title = title;
        this.blurb = blurb;
        this.audience = audience;
        this.tone = tone;
        if (checklist != null) this.checklist = checklist;
        this.order = order;
    }

    /** Seed matching web PROGRAMS when RTDB is empty. */
    public static List<ProgrammeItem> defaults() {
        return Arrays.asList(
                new ProgrammeItem("sela-programme", "Sela programme",
                        "Guided mentorship pathway for young people building clarity, confidence and next steps.",
                        "Mentees", "maroon",
                        Arrays.asList("Named mentor match", "90-day aims", "Accountability check-ins"), 0),
                new ProgrammeItem("trailblazers", "Trailblazers",
                        "For young leaders ready to stretch — peer cohorts, mentor access and real-world exposure.",
                        "Emerging leaders", "blue",
                        Arrays.asList("Peer cohorts", "Mentor panels", "Real-world exposure"), 1),
                new ProgrammeItem("scripture-safari", "Scripture Safari",
                        "Faith-rooted guidance that walks with mentees through life, purpose and community.",
                        "Faith & life", "maroon",
                        Arrays.asList("Faith & purpose", "Community walks", "Mentor accompaniment"), 2),
                new ProgrammeItem("go-for-it-codelab", "Go for it Codelab",
                        "Hands-on coding lab — projects, mentor feedback and skills you can show in a portfolio.",
                        "Aspiring builders", "blue",
                        Arrays.asList("Build projects", "Mentor feedback", "Portfolio-ready work"), 3)
        );
    }
}
