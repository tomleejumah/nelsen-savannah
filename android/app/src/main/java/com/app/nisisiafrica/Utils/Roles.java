package com.app.nisisiafrica.Utils;

import com.app.nisisiafrica.Constants;

/**
 * Role checks + LMS shells (aligned with API /lms/me).
 *
 * Hierarchy: SuperAdmin (legacy Admin) → SchoolAdmin → Mentor → Mentee.
 */
public final class Roles {

    public static final String ADMIN = "Admin";
    public static final String SUPER_ADMIN = "SuperAdmin";
    public static final String SCHOOL_ADMIN = "SchoolAdmin";
    public static final String MENTOR = "Mentor";
    public static final String MENTEE = "Mentee";

    public static final String SHELL_STUDENT = "student";
    public static final String SHELL_MENTOR = "mentor";
    public static final String SHELL_SCHOOL = "school";
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

    public static boolean isMentor() {
        return MENTOR.equals(current());
    }

    public static boolean isMentee() {
        return !isSuperAdmin() && !isSchoolAdmin() && !isMentor();
    }

    /** LMS product shell for this role. */
    public static String lmsShell() {
        return lmsShell(current());
    }

    public static String lmsShell(String role) {
        if (isSuperAdmin(role)) return SHELL_ADMIN;
        if (isSchoolAdmin(role)) return SHELL_SCHOOL;
        if (MENTOR.equals(role)) return SHELL_MENTOR;
        return SHELL_STUDENT;
    }

    public static String lmsShellLabel() {
        switch (lmsShell()) {
            case SHELL_ADMIN:
                return "Super admin";
            case SHELL_SCHOOL:
                return "School admin";
            case SHELL_MENTOR:
                return "Teach";
            default:
                return "Learning";
        }
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
        return isSuperAdmin() || isSchoolAdmin() || isMentor();
    }

    public static boolean canManageApp() {
        return isSuperAdmin();
    }

    public static boolean canManageSchoolUsers() {
        return isSuperAdmin() || isSchoolAdmin();
    }

    public static boolean isAdmin(String role) {
        return isSuperAdmin(role);
    }

    public static boolean isMentor(String role) {
        return MENTOR.equals(role);
    }

    public static boolean canCreate(String role) {
        return isSuperAdmin(role) || isSchoolAdmin(role) || isMentor(role);
    }
}
