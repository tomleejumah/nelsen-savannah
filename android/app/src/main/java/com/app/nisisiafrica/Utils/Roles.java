package com.app.nisisiafrica.Utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Role checks + LMS shells (aligned with API /lms/me).
 *
 * Hierarchy: SuperAdmin (legacy Admin) → SchoolAdmin → Mentor → Mentee.
 *
 * Android mapping (temporary until school/admin shells land in-app):
 * <pre>
 *   SuperAdmin  → mentor UX  (Teach / create / course CMS) + canManageApp
 *   SchoolAdmin → mentor UX  (same) + canManageSchoolUsers
 *   Mentor      → mentor UX
 *   Mentee      → student UX
 * </pre>
 * Web already has /school and /admin shells; app collapses staff → mentor first.
 */
public final class Roles {

    public static final String ADMIN = "Admin";
    public static final String SUPER_ADMIN = "SuperAdmin";
    public static final String SCHOOL_ADMIN = "SchoolAdmin";
    public static final String MENTOR = "Mentor";
    public static final String MENTEE = "Mentee";

    public static final String SHELL_STUDENT = "student";
    public static final String SHELL_MENTOR = "mentor";
    /** Reserved for a future in-app school console (web /school today). */
    public static final String SHELL_SCHOOL = "school";
    /** Reserved for a future in-app platform console (web /admin today). */
    public static final String SHELL_ADMIN = "admin";

    private Roles() {}

    private static final String SCHOOL_ROLE_KEY = "LMS_ACTIVE_SCHOOL_ROLE_";
    private static final String PLATFORM_ROLE_KEY = "LMS_VERIFIED_PLATFORM_ROLE_";

    /**
     * Treat only /lms/me verified roles for this Firebase UID as staff.
     * Never trust the device-wide legacy USER_ROLE preference. School roles
     * are cleared on resume until the active school is revalidated.
     */
    public static String current() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return MENTEE;
        String uid = user.getUid();
        return VerifiedRolePolicy.resolve(
                Util.getState(PLATFORM_ROLE_KEY + uid, ""),
                Util.getState(SCHOOL_ROLE_KEY + uid, ""));
    }

    /** Apply a complete API /lms/me result only after verifying its UID. */
    public static void storeVerifiedRoles(String uid, String globalRole, String activeSchoolRole) {
        if (uid == null || uid.isBlank()) return;
        String platform = isSuperAdmin(globalRole) ? SUPER_ADMIN : MENTEE;
        Util.saveState(PLATFORM_ROLE_KEY + uid, platform);
        storeActiveSchoolRole(uid, activeSchoolRole);
    }

    /** School role cache is temporary UI state, never a cross-school grant. */
    public static void storeActiveSchoolRole(String uid, String role) {
        if (uid == null || uid.isBlank()) return;
        String canonical = SCHOOL_ADMIN.equals(role) ? SCHOOL_ADMIN
                : MENTOR.equals(role) ? MENTOR : MENTEE;
        Util.saveState(SCHOOL_ROLE_KEY + uid, canonical);
    }

    /** Fail closed while refreshing after login/account/school switching. */
    public static void invalidateVerifiedRoles(String uid) {
        if (uid == null || uid.isBlank()) return;
        Util.saveState(PLATFORM_ROLE_KEY + uid, MENTEE);
        Util.saveState(SCHOOL_ROLE_KEY + uid, MENTEE);
    }

    public static boolean isSuperAdmin() {
        return isSuperAdmin(current());
    }

    public static boolean isSuperAdmin(String role) {
        return ADMIN.equals(role) || SUPER_ADMIN.equals(role);
    }

    public static boolean isSchoolAdmin() {
        return isSchoolAdmin(current());
    }

    public static boolean isSchoolAdmin(String role) {
        return SCHOOL_ADMIN.equals(role);
    }

    public static boolean isAdmin() {
        return isSuperAdmin();
    }

    /**
     * True mentor title only (not school/super admin).
     * Prefer {@link #actsAsMentor()} for feature gates.
     */
    public static boolean isMentorTitle() {
        return MENTOR.equals(current());
    }

    public static boolean isMentorTitle(String role) {
        return MENTOR.equals(role);
    }

    /**
     * Mentor-equivalent app UX: Mentor, SchoolAdmin, and SuperAdmin.
     * Use this for course CMS, create events, teach chrome, etc.
     */
    public static boolean actsAsMentor() {
        return actsAsMentor(current());
    }

    public static boolean actsAsMentor(String role) {
        return isMentorTitle(role) || isSchoolAdmin(role) || isSuperAdmin(role);
    }

    /** @deprecated prefer {@link #actsAsMentor()} — kept so call sites keep compiling. */
    public static boolean isMentor() {
        return actsAsMentor();
    }

    /** @deprecated prefer {@link #actsAsMentor(String)}. */
    public static boolean isMentor(String role) {
        return actsAsMentor(role);
    }

    public static boolean isMentee() {
        return !actsAsMentor();
    }

    public static boolean isMentee(String role) {
        return !actsAsMentor(role);
    }

    /**
     * In-app product shell. Staff collapse to mentor until dedicated UIs exist.
     * (Web still uses school/admin shells.)
     */
    public static String lmsShell() {
        return lmsShell(current());
    }

    public static String lmsShell(String role) {
        if (actsAsMentor(role)) return SHELL_MENTOR;
        return SHELL_STUDENT;
    }

    /** Human label — still shows real role so SchoolAdmin ≠ plain Mentor in copy. */
    public static String lmsShellLabel() {
        if (isSuperAdmin()) return "Super admin";
        if (isSchoolAdmin()) return "School admin";
        if (isMentorTitle()) return "Teach";
        return "Learning";
    }

    /** Mentee-only: show mentor list / Book mentor.
     * TEMP: always true so mentors see the list too — revert later. */
    public static boolean browsesMentors() {
        return true;
    }

    public static boolean browsesMentors(String role) {
        return true;
    }

    public static boolean canCreate() {
        return actsAsMentor();
    }

    /**
     * Create / edit / delete communities, logos, group events.
     * Mentees engage only (join, post, like) — not manage the space.
     */
    public static boolean canManageCommunities() {
        return actsAsMentor();
    }

    public static boolean canManageCommunities(String role) {
        return actsAsMentor(role);
    }

    public static boolean canManageApp() {
        return isSuperAdmin();
    }

    public static boolean canManageSchoolUsers() {
        return isSuperAdmin() || isSchoolAdmin();
    }

    /** Corporate marketplace stories (brand ads). Personal stories are for mentees/mentors. */
    public static boolean postsCorporateStories() {
        return isSuperAdmin() || isSchoolAdmin();
    }

    public static boolean isAdmin(String role) {
        return isSuperAdmin(role);
    }

    public static boolean canCreate(String role) {
        return actsAsMentor(role);
    }
}
