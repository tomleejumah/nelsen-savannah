package com.app.nisisiafrica.Utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Account and active-school UI role must be verified independently. */
public class VerifiedRolePolicyTest {
    @Test public void unknownOrOfflineRoleDefaultsToMentee() {
        assertEquals("Mentee", VerifiedRolePolicy.resolve(null, null));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("", ""));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("Mentor", ""));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("SchoolAdmin", null));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("Mentor", "applied"));
    }

    @Test public void schoolAdminAtSchoolAIsMenteeAtSchoolB() {
        assertEquals("SchoolAdmin", VerifiedRolePolicy.resolve("SchoolAdmin", "SchoolAdmin"));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("SchoolAdmin", "Mentee"));
        assertEquals("Mentor", VerifiedRolePolicy.resolve("SchoolAdmin", "Mentor"));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("SchoolAdmin", ""));
    }

    @Test public void mentorIsSchoolSpecific() {
        assertEquals("Mentor", VerifiedRolePolicy.resolve("Mentor", "Mentor"));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("Mentor", "Mentee"));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("Mentor", "suspended"));
    }

    @Test public void verifiedPlatformSuperAdminIsGlobal() {
        assertEquals("SuperAdmin", VerifiedRolePolicy.resolve("SuperAdmin", "Mentee"));
        assertEquals("SuperAdmin", VerifiedRolePolicy.resolve("Admin", "Mentor"));
    }

    @Test public void unknownOrMismatchedRoleCannotGainStaffPrivileges() {
        assertEquals("Mentee", VerifiedRolePolicy.resolve("SUPERADMIN", "SCHOOLADMIN"));
        assertEquals("Mentee", VerifiedRolePolicy.resolve("SchoolAdmin", "schooladmin"));
    }
}
