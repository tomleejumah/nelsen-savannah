package com.app.nisisiafrica.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * Persists and applies app language (English / French / Kiswahili).
 */
public final class LocaleHelper {

    public static final String PREF_LANG = "app_language";
    public static final String LANG_EN = "en";
    public static final String LANG_FR = "fr";
    public static final String LANG_SW = "sw";
    private static final String PREFS = "MyPrefs";

    private LocaleHelper() {}

    private static SharedPreferences prefs(Context ctx) {
        // Application.attachBaseContext runs before getApplicationContext() is ready.
        Context store = ctx.getApplicationContext();
        if (store == null) store = ctx;
        return store.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String current(Context ctx) {
        return prefs(ctx).getString(PREF_LANG, LANG_EN);
    }

    public static String displayLabel(Context ctx) {
        switch (current(ctx)) {
            case LANG_FR:
                return "Français";
            case LANG_SW:
                return "Kiswahili";
            default:
                return "English";
        }
    }

    public static void applyPersisted(Context ctx) {
        apply(ctx, current(ctx), false);
    }

    public static void setLanguage(Context ctx, String langTag) {
        apply(ctx, langTag, true);
    }

    private static void apply(Context ctx, String langTag, boolean recreate) {
        if (langTag == null || langTag.isEmpty()) langTag = LANG_EN;
        prefs(ctx).edit().putString(PREF_LANG, langTag).apply();
        // Keep Util prefs in sync when available
        try {
            Util.saveState(PREF_LANG, langTag);
        } catch (IllegalStateException ignored) {
            // Util not initialised yet (Application.attachBaseContext)
        }
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(langTag);
        AppCompatDelegate.setApplicationLocales(locales);
        if (recreate && ctx instanceof android.app.Activity) {
            ((android.app.Activity) ctx).recreate();
        }
    }

    /** Wrap context so layouts inflate with the persisted locale before AppCompat applies. */
    public static Context wrap(Context context) {
        String tag = current(context);
        Locale locale = Locale.forLanguageTag(tag);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.setLocale(locale);
        }
        return context.createConfigurationContext(config);
    }
}
