package com.app.nisisiafrica.Utils;

import java.util.Locale;

/** Shared mentor star-average formatting for list cards and profile. */
public final class MentorRatingFormat {
    private MentorRatingFormat() {}

    /**
     * One decimal normally; if the fractional part is above 0.9, round to nearest whole
     * (e.g. 4.91 → "5", 4.8 → "4.8").
     */
    public static String formatAverage(double avg) {
        if (!(avg > 0) || Double.isNaN(avg)) return "0";
        double frac = avg - Math.floor(avg);
        if (frac > 0.9) {
            return String.valueOf(Math.round(avg));
        }
        return String.format(Locale.getDefault(), "%.1f", avg);
    }

    public static String listTag(Double average, Long ratingsCount) {
        long n = ratingsCount != null ? ratingsCount : 0L;
        if (average != null && average > 0 && n > 0) {
            return String.format(Locale.getDefault(),
                    "★ %s · %d rating%s",
                    formatAverage(average), n, n == 1 ? "" : "s");
        }
        if (average != null && average > 0) {
            return "★ " + formatAverage(average);
        }
        if (n > 0) {
            return n + (n == 1 ? " rating" : " ratings");
        }
        return "New mentor";
    }
}
