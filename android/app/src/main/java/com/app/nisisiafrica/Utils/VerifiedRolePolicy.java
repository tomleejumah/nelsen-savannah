package com.app.nisisiafrica.Utils;

/**
 * Pure, testable role resolution for the Android chrome.
 *
 * The legacy, device-wide USER_ROLE preference is not an authorization
 * source: another account can log in on the same phone, and a global Mentor
 * or SchoolAdmin might have Mentee privileges in the selected school.
 * Server-side guards remain authoritative for protected operations.
 */
public final class VerifiedRolePolicy {
    private VerifiedRolePolicy() {}

    public static String resolve(String verifiedGlobal, String verifiedActiveSchool) {
        if ("SuperAdmin".equals(verifiedGlobal) || "Admin".equals(verifiedGlobal)) {
            return "SuperAdmin";
        }
        if ("SchoolAdmin".equals(verifiedActiveSchool)) return "SchoolAdmin";
        if ("Mentor".equals(verifiedActiveSchool)) return "Mentor";
        // Unknown, pending, rejected, offline/unverified, or stale roles do
        // not gain staff UI privileges while an API refresh is in flight.
        return "Mentee";
    }
}
