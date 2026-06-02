package com.example.off_record;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private SharedPreferences pref;
    private TextView tvTotalDays;
    private TextView tvAlarmTime;
    private SwitchCompat switchAlarm;
    private FirebaseAuth mAuth;
    private TextView tvAccountName;
    private TextView tvLifeDataStatus;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showMaterialTimePicker();
                } else {
                    if (switchAlarm != null) switchAlarm.setChecked(false);
                    Toast.makeText(getContext(), "알림 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mAuth = FirebaseAuth.getInstance();
        pref = requireActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);

        tvTotalDays = view.findViewById(R.id.tvTotalDays);
        tvAlarmTime = view.findViewById(R.id.tvAlarmTime);
        switchAlarm = view.findViewById(R.id.switchAlarm);
        Button btnLogin = view.findViewById(R.id.btnLogin);
        TextView tvLogout = view.findViewById(R.id.tvLogout);
        tvAccountName = view.findViewById(R.id.tvAccountName);
        tvLifeDataStatus = view.findViewById(R.id.tvLifeDataStatus);

        View itemNotificationTime = view.findViewById(R.id.itemNotificationTime);
        View itemLifeData = view.findViewById(R.id.itemLifeData);
        View itemVoicePolicy = view.findViewById(R.id.itemVoicePolicy);
        View cardTotalDays = view.findViewById(R.id.cardTotalDays);
        View cardStatsReport = view.findViewById(R.id.cardStatsReport);

        updateStats();

        // 로그인 상태에 따른 UI 분기 처리 및 닉네임 변환
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (btnLogin != null) btnLogin.setVisibility(View.GONE);
            if (tvLogout != null) tvLogout.setVisibility(View.VISIBLE);

            String email = currentUser.getEmail();
            if (tvAccountName != null && email != null) {
                if (email.contains("test1")) {
                    tvAccountName.setText("사용자 1 👑");
                } else if (email.contains("test2")) {
                    tvAccountName.setText("사용자 2 🌟");
                } else {
                    tvAccountName.setText(email);
                }
            }
        } else {
            if (btnLogin != null) btnLogin.setVisibility(View.VISIBLE);
            if (tvLogout != null) tvLogout.setVisibility(View.GONE);

            if (tvAccountName != null) tvAccountName.setText("게스트 ⚠️");
        }

        // 처음 설정 화면에 진입했을 때, 이미 권한이 켜져 있다면 "연동됨"으로 보여주기
        if (checkUsageStatsPermission()) {
            if (tvLifeDataStatus != null) tvLifeDataStatus.setText("연동됨");
        } else {
            if (tvLifeDataStatus != null) tvLifeDataStatus.setText("미연동");
        }

        boolean isAlarmOn = pref.getBoolean("alarm_on", true);
        if (switchAlarm != null) {
            switchAlarm.setChecked(isAlarmOn);
        }

        int savedHour = pref.getInt("alarm_hour", 21);
        int savedMinute = pref.getInt("alarm_minute", 0);
        updateTimeText(savedHour, savedMinute);

        if (switchAlarm != null) {
            switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    checkPermissionAndShowPicker();
                } else {
                    pref.edit().putBoolean("alarm_on", false).apply();
                    setDailyAlarm(false);
                    Toast.makeText(getContext(), "알림이 꺼졌습니다.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (itemNotificationTime != null) {
            itemNotificationTime.setOnClickListener(v -> checkPermissionAndShowPicker());
        }

        if (cardTotalDays != null) {
            cardTotalDays.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).bottomNav.setSelectedItemId(R.id.extra);
                }
            });
        }

        if (cardStatsReport != null) {
            cardStatsReport.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).bottomNav.setSelectedItemId(R.id.stats);
                }
            });
        }

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, new LoginFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }

        if (tvLogout != null) {
            tvLogout.setOnClickListener(v -> {
                mAuth.signOut();
                Toast.makeText(getContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();

                getParentFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, new SettingsFragment())
                        .commit();
            });
        }

        // 생활 데이터 연동 버튼 클릭 시 액션
        if (itemLifeData != null) {
            itemLifeData.setOnClickListener(v -> {
                if (checkUsageStatsPermission()) {
                    if (tvLifeDataStatus != null) tvLifeDataStatus.setText("연동됨");

                    java.util.Map<String, Object> topApps = getTop3AppUsage();
                    if (!topApps.isEmpty()) {

                        StringBuilder msgBuilder = new StringBuilder();

                        // 💡 [UI 추가] 대화 상자 상단에 오늘 총 휴대폰 사용 시간 출력
                        long totalHours = topApps.containsKey("total_hours") ? (long) topApps.get("total_hours") : 0L;
                        long totalMinutes = topApps.containsKey("total_minutes") ? (long) topApps.get("total_minutes") : 0L;

                        msgBuilder.append("📱 오늘 총 휴대폰 사용 시간:\n");
                        if (totalHours > 0) {
                            msgBuilder.append(totalHours).append("시간 ");
                        }
                        msgBuilder.append(totalMinutes).append("분\n\n");
                        msgBuilder.append("-----------------------------\n\n");
                        msgBuilder.append("[앱별 사용량 순위]\n");

                        if (topApps.containsKey("app1_name")) {
                            msgBuilder.append("• 1위 : ").append(topApps.get("app1_name")).append(" (").append(topApps.get("app1_time")).append("분)\n");
                        }
                        if (topApps.containsKey("app2_name")) {
                            msgBuilder.append("• 2위 : ").append(topApps.get("app2_name")).append(" (").append(topApps.get("app2_time")).append("분)\n");
                        }
                        if (topApps.containsKey("app3_name")) {
                            msgBuilder.append("• 3위 : ").append(topApps.get("app3_name")).append(" (").append(topApps.get("app3_time")).append("분)");
                        }

                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("📊 오늘 스마트폰 사용 통계 리포트")
                                .setMessage(msgBuilder.toString().trim())
                                .setPositiveButton("확인", null)
                                .create()
                                .show();

                    } else {
                        Toast.makeText(getContext(), "오늘 한국 시간 00:00 이후 수집된 스마트폰 사용 데이터가 아직 없습니다!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    requestUsageStatsPermission();
                }
            });
        }

        if (itemVoicePolicy != null) itemVoicePolicy.setOnClickListener(v -> showVoicePolicyDialog());

        return view;
    }

    private void checkPermissionAndShowPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                showMaterialTimePicker();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            showMaterialTimePicker();
        }
    }

    private void showMaterialTimePicker() {
        int currentHour = pref.getInt("alarm_hour", 21);
        int currentMinute = pref.getInt("alarm_minute", 0);

        try {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(currentHour)
                    .setMinute(currentMinute)
                    .setTitleText("기록 알림 시간을 선택하세요")
                    .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                    .build();

            picker.addOnPositiveButtonClickListener(v -> {
                int hour = picker.getHour();
                int minute = picker.getMinute();

                pref.edit()
                        .putBoolean("alarm_on", true)
                        .putInt("alarm_hour", hour)
                        .putInt("alarm_minute", minute)
                        .apply();

                updateTimeText(hour, minute);
                setDailyAlarm(true);

                if (switchAlarm != null) switchAlarm.setChecked(true);
            });

            picker.addOnNegativeButtonClickListener(v -> {
                if (!pref.contains("alarm_hour") && switchAlarm != null) {
                    switchAlarm.setChecked(false);
                }
            });

            if (isAdded()) {
                picker.show(getChildFragmentManager(), "MATERIAL_TIME_PICKER");
            }
        } catch (Exception e) {
            showOldTimePicker();
        }
    }

    private void showOldTimePicker() {
        int hour = pref.getInt("alarm_hour", 21);
        int minute = pref.getInt("alarm_minute", 0);

        android.app.TimePickerDialog timePicker = new android.app.TimePickerDialog(getContext(),
                (view, hourOfDay, min) -> {
                    pref.edit()
                            .putBoolean("alarm_on", true)
                            .putInt("alarm_hour", hourOfDay)
                            .putInt("alarm_minute", min)
                            .apply();
                    updateTimeText(hourOfDay, min);
                    setDailyAlarm(true);
                    if (switchAlarm != null) switchAlarm.setChecked(true);
                }, hour, minute, false);
        timePicker.show();
    }

    private void setDailyAlarm(boolean enable) {
        if (getContext() == null) return;

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(requireContext(), AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager == null) return;

        if (!enable) {
            alarmManager.cancel(pendingIntent);
            return;
        }

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

        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    private void updateStats() {
        String allRecords = pref.getString("all_records", "");
        if (allRecords.isEmpty()) {
            if (tvTotalDays != null) tvTotalDays.setText("0일");
        } else {
            String[] recordsArray = allRecords.split("##");
            int count = 0;
            for (String r : recordsArray) {
                if (r != null && !r.trim().isEmpty()) count++;
            }
            if (tvTotalDays != null) tvTotalDays.setText(count + "일");
        }
    }

    private void updateTimeText(int hour, int minute) {
        if (tvAlarmTime == null) return;
        String amPm = (hour < 12) ? "오전" : "오후";
        int displayHour = (hour == 0 || hour == 12) ? 12 : hour % 12;
        String timeStr = String.format(Locale.KOREAN, "%s %02d:%02d", amPm, displayHour, minute);
        tvAlarmTime.setText(timeStr);
    }

    private void showVoicePolicyDialog() {
        if (getContext() == null) return;
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("음성 데이터 보안 안내");
        builder.setMessage("사용자의 음성 기록은 AI 분석 직후 텍스트로만 보관되며, 원본 음성 파일은 보안을 위해 기기에서 즉시 자동 삭제됩니다.");
        builder.setPositiveButton("확인", null);
        builder.show();
    }

    // ================= 💡 생활 데이터 수집을 위한 고도화 메서드들 =================

    private boolean checkUsageStatsPermission() {
        android.app.AppOpsManager appOps = (android.app.AppOpsManager) requireContext().getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), requireContext().getPackageName());
        return mode == android.app.AppOpsManager.MODE_ALLOWED;
    }

    private void requestUsageStatsPermission() {
        Toast.makeText(getContext(), "AI 분석 기능을 위해 스마트폰 사용 정보 접근 권한 승인이 필요합니다.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }

    private java.util.Map<String, Object> getTop3AppUsage() {
        java.util.Map<String, Object> topAppsResult = new java.util.HashMap<>();
        android.app.usage.UsageStatsManager usageStatsManager = (android.app.usage.UsageStatsManager) requireContext().getSystemService(Context.USAGE_STATS_SERVICE);
        android.content.pm.PackageManager packageManager = requireContext().getPackageManager();

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        android.app.usage.UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);

        java.util.Map<String, Long> appUsageMap = new java.util.HashMap<>();
        java.util.Map<String, Long> openTimeMap = new java.util.HashMap<>();

        android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            String pkg = event.getPackageName();

            if (pkg.contains("launcher") || pkg.equals(requireContext().getPackageName()) || pkg.contains("systemui")) {
                continue;
            }

            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();

            if (eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                openTimeMap.put(pkg, eventTime);
            }
            else if (eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                if (openTimeMap.containsKey(pkg)) {
                    long openTime = openTimeMap.remove(pkg);
                    long duration = eventTime - openTime;
                    if (duration > 0) {
                        appUsageMap.put(pkg, appUsageMap.getOrDefault(pkg, 0L) + duration);
                    }
                }
            }
        }

        for (String pkg : openTimeMap.keySet()) {
            long openTime = openTimeMap.get(pkg);
            long duration = endTime - openTime;
            if (duration > 0) {
                appUsageMap.put(pkg, appUsageMap.getOrDefault(pkg, 0L) + duration);
            }
        }

        // 💡 [로직 추가] 수집된 모든 실사용 앱들의 밀리초 합산하여 총 사용 시간 계산
        long totalMillis = 0;
        for (long duration : appUsageMap.values()) {
            totalMillis += duration;
        }
        long totalTotalMinutes = totalMillis / (1000 * 60);
        long totalHours = totalTotalMinutes / 60;
        long remainingMinutes = totalTotalMinutes % 60;

        // 결과 지도(Map)에 총 사용 시간 메타데이터 바인딩
        topAppsResult.put("total_hours", totalHours);
        topAppsResult.put("total_minutes", remainingMinutes);

        // 사용 시간순 내림차순 정렬
        java.util.List<java.util.Map.Entry<String, Long>> sortedList = new java.util.ArrayList<>(appUsageMap.entrySet());
        sortedList.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        int rank = 1;
        for (java.util.Map.Entry<String, Long> entry : sortedList) {
            if (rank > 3) break;

            String packageName = entry.getKey();
            long minutes = entry.getValue() / (1000 * 60);

            if (minutes == 0) continue;

            String appLabel = packageName;
            try {
                android.content.pm.ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                appLabel = packageManager.getApplicationLabel(appInfo).toString();
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                // 패키지명 유지
            }

            topAppsResult.put("app" + rank + "_name", appLabel);
            topAppsResult.put("app" + rank + "_time", minutes);
            rank++;
        }

        return topAppsResult;
    }
}