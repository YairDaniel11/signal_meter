package com.meir.signalmeter;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;

/** קובע ערכת נושא בהתאם למצב המערכת. נקרא ב-onCreate, לפני setContentView. */
public final class ThemeUtil {

    private ThemeUtil() {}

    public static void applyTheme(Activity activity) {
        activity.setTheme(isDarkMode(activity) ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    private static boolean isDarkMode(Activity activity) {
        // Configuration.UI_MODE_NIGHT_MASK זמין מ-API 8, אבל למכשיר שאין בו
        // בכלל מושג Dark Mode (לפני API 29) הדגל פשוט לא יוגדר - ברירת המחדל
        // ההיסטורית של האפליקציה הזו היא כהה.
        int nightModeFlags = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return nightModeFlags != Configuration.UI_MODE_NIGHT_NO;
        }
        return true; // ברירת מחדל למכשירים ישנים יותר
    }
}
