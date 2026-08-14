package com.app.nisisiafrica.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Age → LMS programSlug allowlist after Find My Path questionnaire.
 * Prefs written by {@link com.app.nisisiafrica.QuestionnaireActivity}.
 */
public final class PathAgeFilter {
    public static final String PREFS = "QuestionnairePrefs";
    public static final String KEY_AGE = "user_age";
    public static final String KEY_SUBMITTED = "submitted_flag";
    public static final String KEY_HAS_JOB = "user_has_job";

    private PathAgeFilter() {}

    public static boolean isActive(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SUBMITTED, false) && prefs.getInt(KEY_AGE, -1) > 0;
    }

    /** Program slugs that match the mentee's age band. Empty = no filter. */
    public static Set<String> allowedProgramSlugs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SUBMITTED, false)) {
            return Collections.emptySet();
        }
        int age = prefs.getInt(KEY_AGE, -1);
        if (age < 0) return Collections.emptySet();

        List<String> slugs;
        if (age >= 7 && age <= 15) {
            // Kids / early teens — faith + foundation paths
            slugs = Arrays.asList("scripture-safari", "sela-programme");
        } else if (age >= 16 && age <= 21) {
            slugs = Arrays.asList("sela-programme", "trailblazers", "go-for-it-codelab", "ui-demo");
        } else if (prefs.getBoolean(KEY_HAS_JOB, false)) {
            slugs = Arrays.asList("trailblazers", "go-for-it-codelab", "mentor-academy", "ui-demo");
        } else {
            slugs = Arrays.asList("sela-programme", "trailblazers", "go-for-it-codelab", "ui-demo");
        }
        return new HashSet<>(slugs);
    }

    public static boolean matches(Context context, String programSlug) {
        Set<String> allowed = allowedProgramSlugs(context);
        if (allowed.isEmpty()) return true;
        if (programSlug == null || programSlug.isEmpty()) return true; // keep Firebase/legacy rows
        return allowed.contains(programSlug);
    }
}
