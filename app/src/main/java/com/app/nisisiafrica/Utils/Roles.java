package com.app.nisisiafrica.Utils;

import com.app.nisisiafrica.Constants;

/**
 * Single source of truth for role checks.
 *
 * MentUI chrome (tokens/layouts) is shared across Mentee, Mentor, and Admin.
 * Visibility differs by capability:
 * <ul>
 *   <li>{@link #isMentee()} — browse/book mentors, reserve seats</li>
 *   <li>{@link #canCreate()} — Mentor + Admin: create stories/events; talk via Chats
 *       (no Book Mentor / mentor browse lists)</li>
 *   <li>{@link #canManageApp()} — Admin only (banners, app-wide content)</li>
 * </ul>
 * Prefer these helpers over string literals so Admin is never treated as Mentee.
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

    /** Mentee-only: show mentor list / Book mentor / Find a mentor.
     * TEMP: always true so mentors see the list too — revert later. */
    public static boolean browsesMentors() {
        return true;
    }

    /** TEMP: always true — revert to !admin && !mentor later. */
    public static boolean browsesMentors(String role) {
        return true;
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
