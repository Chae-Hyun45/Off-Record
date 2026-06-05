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
        TextView tvLoginPrompt = view.findViewById(R.id.tvLoginPrompt);

        View itemNotificationTime = view.findViewById(R.id.itemNotificationTime);
        View cardTotalDays = view.findViewById(R.id.cardTotalDays);
        View cardStatsReport = view.findViewById(R.id.cardStatsReport);

        updateStats();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (btnLogin != null) btnLogin.setVisibility(View.GONE);
            if (tvLogout != null) tvLogout.setVisibility(View.VISIBLE);
            if (tvLoginPrompt != null) tvLoginPrompt.setVisibility(View.GONE); // 로그인 시 프롬프트 숨김

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
        }
        else {
            if (btnLogin != null) btnLogin.setVisibility(View.VISIBLE);
            if (tvLogout != null) tvLogout.setVisibility(View.GONE);
            if (tvLoginPrompt != null) tvLoginPrompt.setVisibility(View.VISIBLE); // 비로그인 시 프롬프트 표시
            if (tvAccountName != null) tvAccountName.setText("게스트 ⚠️");
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
}
