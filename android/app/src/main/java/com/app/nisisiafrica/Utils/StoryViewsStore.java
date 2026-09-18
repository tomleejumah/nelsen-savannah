package com.app.nisisiafrica.Utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Local “seen” marks for home story rings (WhatsApp/IG style). */
public final class StoryViewsStore {

    private static final String PREFS = "story_views";

    private StoryViewsStore() {}

    private static String key() {
        String uid = FirebaseAuth.getInstance().getUid();
        return "seen_" + (uid != null ? uid : "anon");
    }

    @NonNull
    public static Set<String> seenIds(Context context) {
        if (context == null) return Collections.emptySet();
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> raw = prefs.getStringSet(key(), null);
        return raw == null ? Collections.emptySet() : new HashSet<>(raw);
    }

    public static void markSeen(Context context, String storyId) {
        if (context == null || storyId == null || storyId.isEmpty()) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> next = new HashSet<>(prefs.getStringSet(key(), Collections.emptySet()));
        if (next.add(storyId)) {
            prefs.edit().putStringSet(key(), next).apply();
        }
    }

    public static boolean isSeen(Context context, String storyId) {
        if (storyId == null) return false;
        return seenIds(context).contains(storyId);
    }
}
