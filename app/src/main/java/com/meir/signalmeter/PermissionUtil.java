package com.meir.signalmeter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * בדיקת הרשאה עובדת על כל Context (כולל Service/Receiver בעתיד אם יתווספו).
 * בקשת הרשאה דורשת Activity פעיל בפועל - לכן שמורה בנפרד.
 */
public final class PermissionUtil {

    private PermissionUtil() {}

    public static final int REQUEST_CODE_PHONE_STATE = 100;

    public static boolean hasReadPhoneState(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // לפני API 23 כל ההרשאות המוצהרות ב-manifest מאושרות בהתקנה
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestReadPhoneState(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    REQUEST_CODE_PHONE_STATE);
        }
    }
}
