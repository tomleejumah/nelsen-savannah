package com.app.nisisiafrica.Utils;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.app.nisisiafrica.R;

/**
 * Resolves Material theme attributes to concrete colours so code-driven tints
 * follow the active light/dark theme instead of hardcoding hex values.
 */
public final class ThemeColors {

    private ThemeColors() {}

    /** Resolves a theme attribute (e.g. {@code colorSecondary}), falling back to {@code fallbackRes}. */
    @ColorInt
    public static int of(Context context, @AttrRes int attr, int fallbackRes) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            return value.resourceId != 0
                    ? ContextCompat.getColor(context, value.resourceId)
                    : value.data;
        }
        return ContextCompat.getColor(context, fallbackRes);
    }

    /** Brand accent (maroon by day, lifted maroon at night). */
    @ColorInt
    public static int accent(Context context) {
        return of(context, com.google.android.material.R.attr.colorSecondary, R.color.action_accent);
    }

    /** Brand primary (navy by day, maroon at night). */
    @ColorInt
    public static int primary(Context context) {
        return of(context, com.google.android.material.R.attr.colorPrimary, R.color.action_primary);
    }

    /** Muted foreground, for de-emphasised icons and secondary text. */
    @ColorInt
    public static int muted(Context context) {
        return of(context, com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.text_muted);
    }
}
