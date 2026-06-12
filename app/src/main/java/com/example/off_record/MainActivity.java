package com.example.off_record;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;


public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNav;
    FirebaseFirestore db;
    private long backPressedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkNotificationPermission();

        db = FirebaseFirestore.getInstance();
        bottomNav = findViewById(R.id.bottomNav);

        // 앱이 켜질 때, 사용자가 알림을 켜둔 상태라면 매일 알림을 다시 예약
        setupDailyAlarm();

        // 매일 자정 사용 데이터 자동 동기화 예약
        DailyUsageSyncWorker.enqueueDailyWork(this);

        // 1. 앱 실행 시 첫 화면은 캘린더로 설정
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, new CalendarFragment())
                    .commit();
            bottomNav.setSelectedItemId(R.id.calendar);
        }

        // 2. 바텀 네비게이션 클릭
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            updateFabPosition(id);

            Fragment selected = null;
            if (id == R.id.calendar) {
                selected = new CalendarFragment();
            } else if (id == R.id.stats) {
                selected = new StatsFragment();
            } else if (id == R.id.extra) {
                selected = new ExtraFragment();
            } else if (id == R.id.settings) {
                selected = new SettingsFragment();
            } else if (id == R.id.record) {
                showEmotionDialog();
                return true;
            }

            if (selected != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, selected)
                        .commit();
                return true;
            }

            return false;
        });

        // 3. 중앙 FAB(이모지 버튼) 클릭 시 감정 선택 다이얼로그 표시
        ImageButton fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd != null) {
            updateFabPosition(bottomNav.getSelectedItemId());
            fabAdd.setOnClickListener(v -> {
                if (bottomNav.getSelectedItemId() == R.id.record) {
                    showEmotionDialog();
                } else {
                    bottomNav.setSelectedItemId(R.id.record);
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        // 프래그먼트 백스택이 있으면 (예: 캘린더 상세화면) 이전 화면으로 이동
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
        } else {
            // 루트 화면(캘린더, 통계 등)인 경우 두 번 눌러 종료
            if (System.currentTimeMillis() - backPressedTime < 2000) {
                super.onBackPressed();
                finish();
            } else {
                backPressedTime = System.currentTimeMillis();
                Toast.makeText(this, "뒤로가기를 한 번 더 누르시면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateFabPosition(int itemId) {
        ImageButton fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd == null) return;

        float density = getResources().getDisplayMetrics().density;
        if (itemId == R.id.record) {
            // 기록 탭이 선택되었을 때 (현재 만족하시는 위치)
            fabAdd.setTranslationY(5 * density);
        } else {
            // 다른 탭이 선택되었을 때 (이모지를 조금 더 아래로 내림)
            fabAdd.setTranslationY(15 * density);
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
        AlarmScheduler.scheduleFromPreferences(this);
    }

    // 3. 감정 선택 팝업창 띄우는 함수
    private void showEmotionDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_emotion_select);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        int[] emojiIds = {R.id.emo1, R.id.emo2, R.id.emo3, R.id.emo4, R.id.emo5};
        String[] emotionValues = {"매우_안좋아요", "안좋아요", "보통이에요", "좋아요", "매우_좋아요"};

        for (int i = 0; i < emojiIds.length; i++) {
            int id = emojiIds[i];
            String emotionValue = emotionValues[i];

            View btn = dialog.findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    dialog.dismiss();

                    InputFragment fragment = new InputFragment();
                    Bundle bundle = new Bundle();

                    bundle.putString("selected_emotion", emotionValue);
                    fragment.setArguments(bundle);

                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.frameLayout, fragment)
                            .commit();
                });
            }
        }

        dialog.show();
    }
}
