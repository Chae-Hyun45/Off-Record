package com.example.off_record;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Calendar;

public class AlarmScheduler {

    public static final int DAILY_ALARM_REQUEST_CODE = 1001;

    private AlarmScheduler() {}

    public static PendingIntent createPendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        return PendingIntent.getBroadcast(
                context,
                DAILY_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static void cancelDailyAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(createPendingIntent(context));
    }

    public static void scheduleFromPreferences(Context context) {
        SharedPreferences pref = context.getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        if (!pref.getBoolean("alarm_on", true)) return;

        int hour = pref.getInt("alarm_hour", 21);
        int minute = pref.getInt("alarm_minute", 0);
        scheduleDailyAlarm(context, hour, minute, false);
    }

    public static boolean scheduleDailyAlarm(Context context, int hour, int minute, boolean showToastWhenPermissionMissing) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return false;

        PendingIntent pendingIntent = createPendingIntent(context);
        alarmManager.cancel(pendingIntent);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        long triggerAtMillis = calendar.getTimeInMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            if (showToastWhenPermissionMissing) {
                Toast.makeText(context, "정확한 알림을 위해 알람 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
                Intent settingsIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                settingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(settingsIntent);
            }
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
            );
        }
        return true;
    }

    public static void scheduleNextDayFromPreferences(Context context) {
        SharedPreferences pref = context.getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        if (!pref.getBoolean("alarm_on", true)) return;

        int hour = pref.getInt("alarm_hour", 21);
        int minute = pref.getInt("alarm_minute", 0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        PendingIntent pendingIntent = createPendingIntent(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }
}
