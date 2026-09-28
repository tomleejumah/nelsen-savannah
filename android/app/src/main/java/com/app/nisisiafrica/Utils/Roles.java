package com.app.nisisiafrica.Utils;

import com.app.nisisiafrica.Constants;

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

    public static String current() {
        return Util.getState(Constants.USER_ROLE, MENTEE);
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
