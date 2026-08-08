package com.app.nisisiafrica.Utils;

import com.app.nisisiafrica.Constants;

/**
 * Single source of truth for role checks.
 *
 * Roles were previously compared against string literals scattered across the
 * codebase, which is how Admin ended up being treated as a Mentee in some
 * screens: a check for {@code "Mentor".equals(role)} silently excludes admins.
 * Prefer {@link #canCreate()} over testing for a specific role, so a new role
 * only has to be taught about here.
 */
public final class Roles {

    public static final String ADMIN = "Admin";
    public static final String MENTOR = "Mentor";
    public static final String MENTEE = "Mentee";

    private Roles() {}

    /** The signed-in user's role, defaulting to Mentee when unknown. */
    public static String current() {
        return Util.getState(Constants.USER_ROLE, MENTEE);
    }

    public static boolean isAdmin() {
        return ADMIN.equals(current());
    }

    public static boolean isMentor() {
        return MENTOR.equals(current());
    }

    public static boolean isMentee() {
        return !isAdmin() && !isMentor();
    }

    /** Whether the user may author content: stories, events, communities, announcements. */
    public static boolean canCreate() {
        return isAdmin() || isMentor();
    }

    /** Whether the user may manage app-wide content such as home banners. */
    public static boolean canManageApp() {
        return isAdmin();
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equals(role);
    }

    public static boolean isMentor(String role) {
        return MENTOR.equals(role);
    }

    /** Role-string overload, for when the role comes from a fetched profile rather than prefs. */
    public static boolean canCreate(String role) {
        return isAdmin(role) || isMentor(role);
    }
}
