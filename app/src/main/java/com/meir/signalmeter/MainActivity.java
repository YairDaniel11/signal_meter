package com.meir.signalmeter;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * מסך יחיד: מציג עוצמת אות (dBm), ASU, רמה (0-4), סוג רשת ומפעיל,
 * ואוגר מינימום/ממוצע/מקסימום מאז פתיחת המסך - כדי שאפשר יהיה להשוות
 * שני מכשירים זהים באותו מקום בדיוק.
 *
 * הערה מכוונת: משתמשים ב-PhoneStateListener (Deprecated מ-API 31,
 * TelephonyCallback הוא התחליף הרשמי) כי PhoneStateListener עדיין
 * פועל בכל הגרסאות מ-19 ומעלה ונמנע מנתיב קוד כפול עבור אפליקציה
 * קטנה כזו.
 */
@SuppressWarnings("deprecation")
public class MainActivity extends Activity {

    private TextView dbmValueText;
    private TextView asuLevelText;
    private TextView networkText;
    private TextView statsText;
    private TextView samplesText;
    private TextView permissionMessageText;
    private Button grantPermissionButton;
    private Button resetButton;

    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    private int minDbm = Integer.MAX_VALUE;
    private int maxDbm = Integer.MIN_VALUE;
    private long sumDbm = 0;
    private int sampleCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbmValueText = (TextView) findViewById(R.id.dbm_value_text);
        asuLevelText = (TextView) findViewById(R.id.asu_level_text);
        networkText = (TextView) findViewById(R.id.network_text);
        statsText = (TextView) findViewById(R.id.stats_text);
        samplesText = (TextView) findViewById(R.id.samples_text);
        permissionMessageText = (TextView) findViewById(R.id.permission_message_text);
        grantPermissionButton = (Button) findViewById(R.id.grant_permission_button);
        resetButton = (Button) findViewById(R.id.reset_button);

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetStats();
            }
        });

        grantPermissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PermissionUtil.requestReadPhoneState(MainActivity.this);
            }
        });

        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        if (PermissionUtil.hasReadPhoneState(this)) {
            showPermissionUi(false);
            startListening();
        } else {
            showPermissionUi(true);
            PermissionUtil.requestReadPhoneState(this);
        }

        resetButton.requestFocus();
    }

    private void showPermissionUi(boolean needsPermission) {
        permissionMessageText.setVisibility(needsPermission ? View.VISIBLE : View.GONE);
        grantPermissionButton.setVisibility(needsPermission ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionUtil.REQUEST_CODE_PHONE_STATE) {
            if (PermissionUtil.hasReadPhoneState(this)) {
                showPermissionUi(false);
                startListening();
            } else {
                permissionMessageText.setText(R.string.permission_denied_message);
                showPermissionUi(true);
            }
        }
    }

    private void startListening() {
        if (telephonyManager == null || phoneStateListener != null) return;

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                handleSignalStrength(signalStrength);
            }
        };
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        updateNetworkInfo();
    }

    private void stopListening() {
        if (telephonyManager != null && phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            phoneStateListener = null;
        }
    }

    private void handleSignalStrength(SignalStrength signalStrength) {
        Integer dbm = extractDbm(signalStrength);
        Integer level = extractLevel(signalStrength);
        int asu = signalStrength.getGsmSignalStrength();

        if (dbm != null) {
            dbmValueText.setText(dbm + " " + getString(R.string.dbm_unit));
            recordSample(dbm);
        } else {
            dbmValueText.setText(R.string.unknown_value);
        }

        String levelText = (level != null) ? String.valueOf(level) : getString(R.string.unknown_value);
        String asuText = (asu != 99) ? String.valueOf(asu) : getString(R.string.unknown_value);
        asuLevelText.setText(getString(R.string.asu_level_format, asuText, levelText));

        updateNetworkInfo();
    }

    /** dBm מדויק זמין רק מ-API 29; לפני כן ממירים מ-ASU (נוסחת GSM סטנדרטית). */
    private Integer extractDbm(SignalStrength signalStrength) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int dbm = signalStrength.getDbm();
            return (dbm != Integer.MAX_VALUE) ? dbm : null;
        }
        int asu = signalStrength.getGsmSignalStrength();
        if (asu == 99) return null;
        return -113 + (2 * asu);
    }

    /** רמה (0-4) זמינה רק מ-API 23; לפני כן נגזרת ידנית מ-ASU. */
    private Integer extractLevel(SignalStrength signalStrength) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return signalStrength.getLevel();
        }
        int asu = signalStrength.getGsmSignalStrength();
        if (asu == 99) return null;
        if (asu <= 2) return 0;
        if (asu <= 8) return 1;
        if (asu <= 15) return 2;
        if (asu <= 22) return 3;
        return 4;
    }

    private void recordSample(int dbm) {
        sampleCount++;
        sumDbm += dbm;
        if (dbm < minDbm) minDbm = dbm;
        if (dbm > maxDbm) maxDbm = dbm;

        String unit = getString(R.string.dbm_unit);
        long avg = sumDbm / sampleCount;
        statsText.setText(getString(R.string.stats_format,
                minDbm + " " + unit, avg + " " + unit, maxDbm + " " + unit));
        samplesText.setText(getString(R.string.samples_format, sampleCount));
    }

    private void resetStats() {
        minDbm = Integer.MAX_VALUE;
        maxDbm = Integer.MIN_VALUE;
        sumDbm = 0;
        sampleCount = 0;
        statsText.setText("");
        samplesText.setText(getString(R.string.samples_format, 0));
    }

    private void updateNetworkInfo() {
        if (telephonyManager == null || !PermissionUtil.hasReadPhoneState(this)) return;
        String networkType = networkTypeName(telephonyManager.getNetworkType());
        String operatorName = telephonyManager.getNetworkOperatorName();
        if (operatorName == null || operatorName.length() == 0) {
            operatorName = getString(R.string.unknown_value);
        }
        networkText.setText(getString(R.string.network_format, networkType, operatorName));
    }

    private String networkTypeName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_NR: // API 29 - קבוע מהודר בקומפילציה, בטוח על מכשיר ישן
                return "5G";
            default:
                return getString(R.string.unknown_value);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
    }
}
