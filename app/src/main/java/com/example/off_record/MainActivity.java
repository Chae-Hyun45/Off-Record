package com.example.off_record;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNav;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkNotificationPermission();

        db = FirebaseFirestore.getInstance();
        bottomNav = findViewById(R.id.bottomNav);

        // 2. 바텀 네비게이션 리스너 먼저 설정
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            android.util.Log.d("MainActivity", "BottomNav clicked: " + id);

            if (id == R.id.placeholder) {
                return false;
            }

            Fragment selected = null;
            if (id == R.id.calendar) {
                selected = new CalendarFragment();
            } else if (id == R.id.stats) {
                selected = new StatsFragment();
            } else if (id == R.id.extra) {
                selected = new ExtraFragment();
            } else if (id == R.id.settings) {
                selected = new SettingsFragment();
            }

            if (selected != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, selected)
                        .commit();
                return true;
            }

            return false;
        });

        // 1. 앱 실행 시 첫 화면을 명시적으로 트랜잭션하여 캘린더를 바로 띄움
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, new CalendarFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.calendar);
        }

        // 앱이 켜질 때, 사용자가 알림을 켜둔 상태라면 매일 알림을 다시 예약

        // FAB 연결
        View fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> showEmotionDialog());
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101
                );
            }
        }
    }

    private void setupDailyAlarm() {
        SharedPreferences pref = getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);

        if (!pref.getBoolean("alarm_on", true)) return;

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int hour = pref.getInt("alarm_hour", 21);
        int minute = pref.getInt("alarm_minute", 0);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (alarmManager != null) {
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
            );
        }
    }

    // 3. 감정 선택 팝업창 띄우는 함수
    private void showEmotionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_emotion_select);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.15f); // Soften the background dim
        }

        int[] emojiIds = {R.id.emo1, R.id.emo2, R.id.emo3, R.id.emo4, R.id.emo5};

        for (int id : emojiIds) {
            View btn = dialog.findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    // Playful scale effect before dismiss
                    v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).withEndAction(() -> {
                        dialog.dismiss();

                        // 4. 선택한 감정의 ID 정보를 Bundle에 담아서 InputFragment로 전달
                        InputFragment fragment = new InputFragment();
                        Bundle bundle = new Bundle();

                        String emotionKey = getResources().getResourceEntryName(id);
                        bundle.putString("selected_emotion", emotionKey);
                        fragment.setArguments(bundle);

                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.frameLayout, fragment)
                                .commit();
                    }).start();
                });
            }
        }

        dialog.show();
    }
}
