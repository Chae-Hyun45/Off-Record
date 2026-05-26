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

import java.util.Calendar;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private SharedPreferences pref;
    private TextView tvTotalDays;
    private TextView tvAlarmTime;
    private SwitchCompat switchAlarm;

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

        pref = requireActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);

        tvTotalDays = view.findViewById(R.id.tvTotalDays);
        tvAlarmTime = view.findViewById(R.id.tvAlarmTime);
        switchAlarm = view.findViewById(R.id.switchAlarm);
        Button btnLogin = view.findViewById(R.id.btnLogin);
        TextView tvLogout = view.findViewById(R.id.tvLogout);

        View itemNotificationTime = view.findViewById(R.id.itemNotificationTime);
        View itemLifeData = view.findViewById(R.id.itemLifeData);
        View itemVoicePolicy = view.findViewById(R.id.itemVoicePolicy);
        View cardStatsReport = view.findViewById(R.id.cardStatsReport);

        updateStats();

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

        if (cardStatsReport != null) {
            cardStatsReport.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).bottomNav.setSelectedItemId(R.id.stats);
                }
            });
        }

        if (btnLogin != null) btnLogin.setOnClickListener(v -> Toast.makeText(getContext(), "로그인 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show());
        if (itemLifeData != null) itemLifeData.setOnClickListener(v -> Toast.makeText(getContext(), "생활 데이터 연동 설정으로 이동합니다.", Toast.LENGTH_SHORT).show());
        if (itemVoicePolicy != null) itemVoicePolicy.setOnClickListener(v -> showVoicePolicyDialog());
        if (tvLogout != null) tvLogout.setOnClickListener(v -> Toast.makeText(getContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show());

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
}
