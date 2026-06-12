package com.example.off_record;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GuestRecordStore {
    private static final String PREF_NAME = "GuestTodayRecord";

    public static String getTodayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    public static void saveTodayRecord(Context context, Map<String, Object> record, String dateId) {
        if (context == null || record == null) return;

        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.putString("date", dateId);
        editor.putString("timestamp", stringValue(record.get("timestamp")));
        editor.putString("emotion", stringValue(record.get("emotion")));
        editor.putInt("score", intValue(record.get("score")));
        editor.putString("diary", stringValue(record.get("diary")));
        editor.putString("meals", stringValue(record.get("meals")));
        editor.putString("influence", stringValue(record.get("influence")));
        editor.putString("stress", stringValue(record.get("stress")));
        editor.putString("fatigue", stringValue(record.get("fatigue")));
        editor.putString("sleep", stringValue(record.get("sleep")));
        editor.putString("need", stringValue(record.get("need")));
        editor.putString("feedback", stringValue(record.get("feedback")));
        editor.apply();
    }

    public static boolean hasTodayRecord(Context context) {
        if (context == null) return false;
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return getTodayKey().equals(pref.getString("date", ""));
    }

    public static Map<String, Object> getTodayRecord(Context context) {
        if (!hasTodayRecord(context)) return null;

        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Map<String, Object> record = new HashMap<>();
        record.put("date", pref.getString("date", ""));
        record.put("timestamp", pref.getString("timestamp", ""));
        record.put("emotion", pref.getString("emotion", ""));
        record.put("score", pref.getInt("score", 0));
        record.put("diary", pref.getString("diary", ""));
        record.put("meals", pref.getString("meals", ""));
        record.put("influence", pref.getString("influence", "미선택"));
        record.put("stress", pref.getString("stress", "미선택"));
        record.put("fatigue", pref.getString("fatigue", "미선택"));
        record.put("sleep", pref.getString("sleep", "미선택"));
        record.put("need", pref.getString("need", "미선택"));
        record.put("feedback", pref.getString("feedback", "미선택"));
        record.put("resultText", pref.getString("resultText", ""));
        return record;
    }

    public static void clearIfNotToday(Context context) {
        if (context == null) return;
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedDate = pref.getString("date", "");
        if (!savedDate.isEmpty() && !getTodayKey().equals(savedDate)) {
            pref.edit().clear().apply();
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }
}
