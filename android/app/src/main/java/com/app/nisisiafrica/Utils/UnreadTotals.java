package com.app.nisisiafrica.Utils;

import androidx.annotation.Nullable;

import java.util.Map;

/** Pure helpers for the app-wide chat unread badge (no Firebase types, unit-testable). */
public final class UnreadTotals {
    public static final int CAP = 99;

    private UnreadTotals() {}

    /** Reads one user's count from a Firestore {@code unreadCount} map (values arrive as Long). */
    public static int countFor(@Nullable Map<String, ?> unreadCount, @Nullable String uid) {
        if (unreadCount == null || uid == null) return 0;
        Object v = unreadCount.get(uid);
        if (!(v instanceof Number)) return 0;
        long n = ((Number) v).longValue();
        if (n <= 0) return 0;
        return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
    }

    /** True for rooms the chat list never shows; the badge must ignore them too. */
    public static boolean isExcludedRoom(@Nullable String roomId, @Nullable String type) {
        return "announcements".equals(roomId) || "system".equals(type);
    }

    /** "" when zero (caller hides the badge), "99+" above the cap. */
    public static String label(int total) {
        if (total <= 0) return "";
        return total > CAP ? CAP + "+" : String.valueOf(total);
    }
}
