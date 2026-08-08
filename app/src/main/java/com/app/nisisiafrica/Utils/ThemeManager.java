package com.app.nisisiafrica.Utils;

import androidx.appcompat.app.AppCompatDelegate;

import com.app.nisisiafrica.Constants;

/**
 * Single entry point for the app's light/dark preference.
 *
 * The choice is persisted in the same SharedPreferences bucket as the rest of
 * the app state, so it survives process death, and is re-applied in
 * {@code App.onCreate()} before any Activity inflates.
 */
public final class ThemeManager {

    private ThemeManager() {}

    /** True when the user has explicitly opted into dark mode. */
    public static boolean isDarkMode() {
        return Util.getState(Constants.DARK_MODE, false);
    }

    /** Applies the persisted preference. Call once from {@code Application.onCreate()}. */
    public static void applyPersistedMode() {
        apply(isDarkMode());
    }

    /**
     * Persists the choice and applies it immediately. AppCompat recreates the
     * running activities itself, so the switch takes effect without a restart.
     */
    public static void setDarkMode(boolean enabled) {
        Util.saveState(Constants.DARK_MODE, enabled);
        apply(enabled);
    }

    private static void apply(boolean enabled) {
        AppCompatDelegate.setDefaultNightMode(
                enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
