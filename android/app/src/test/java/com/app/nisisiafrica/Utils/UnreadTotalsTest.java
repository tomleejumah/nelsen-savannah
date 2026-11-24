package com.app.nisisiafrica.Utils;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnreadTotalsTest {
    @Test public void countForHandlesMissingAndBadValues() {
        Map<String, Object> m = new HashMap<>();
        m.put("a", 3L);
        m.put("b", -2L);
        m.put("c", "x");
        assertEquals(3, UnreadTotals.countFor(m, "a"));
        assertEquals(0, UnreadTotals.countFor(m, "b"));
        assertEquals(0, UnreadTotals.countFor(m, "c"));
        assertEquals(0, UnreadTotals.countFor(m, "zzz"));
        assertEquals(0, UnreadTotals.countFor(null, "a"));
        assertEquals(0, UnreadTotals.countFor(m, null));
    }

    @Test public void labelCapsAndHides() {
        assertEquals("", UnreadTotals.label(0));
        assertEquals("7", UnreadTotals.label(7));
        assertEquals("99", UnreadTotals.label(99));
        assertEquals("99+", UnreadTotals.label(100));
    }

    @Test public void excludesAnnouncementsAndSystem() {
        assertTrue(UnreadTotals.isExcludedRoom("announcements", "group"));
        assertTrue(UnreadTotals.isExcludedRoom("r1", "system"));
        assertFalse(UnreadTotals.isExcludedRoom("r1", "direct"));
        assertFalse(UnreadTotals.isExcludedRoom("school_announcements_s1", "group"));
    }
}
