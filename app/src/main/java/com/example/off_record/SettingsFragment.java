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
    private FirebaseAuth mAuth; // 💡 파이어베이스 인증 객체 추가
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

        mAuth = FirebaseAuth.getInstance(); // 💡 초기화
        pref = requireActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);

        tvTotalDays = view.findViewById(R.id.tvTotalDays);
        tvAlarmTime = view.findViewById(R.id.tvAlarmTime);
        switchAlarm = view.findViewById(R.id.switchAlarm);
        Button btnLogin = view.findViewById(R.id.btnLogin);
        TextView tvLogout = view.findViewById(R.id.tvLogout);
        tvAccountName = view.findViewById(R.id.tvAccountName);

        View itemNotificationTime = view.findViewById(R.id.itemNotificationTime);
        View itemLifeData = view.findViewById(R.id.itemLifeData);
        View itemVoicePolicy = view.findViewById(R.id.itemVoicePolicy);
        View cardTotalDays = view.findViewById(R.id.cardTotalDays);
        View cardStatsReport = view.findViewById(R.id.cardStatsReport);

        updateStats();

        // 💡 [정원님 추가] 로그인 상태에 따른 UI 분기 처리
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // 🔓 로그인된 상태라면? 로그인 버튼 숨기고 로그아웃 글씨 보여주기
            if (btnLogin != null) btnLogin.setVisibility(View.GONE);
            if (tvLogout != null) tvLogout.setVisibility(View.VISIBLE);

            // 💡 [이게 들어가야 해요!] 로그인한 유저의 실제 이메일 주소로 글자 바꾸기
            if (tvAccountName != null) tvAccountName.setText(currentUser.getEmail());
        } else {
            // 🔒 로그아웃된 상태라면? 로그인 버튼 보여주고 로그아웃 글씨 숨기기
            if (btnLogin != null) btnLogin.setVisibility(View.VISIBLE);
            if (tvLogout != null) tvLogout.setVisibility(View.GONE);

            // 💡 [이것도 필수!] 로그아웃되면 다시 원래대로 "게스트 ⚠️"로 돌려놓기
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

        // 💡 로그인 버튼 누르면 로그인 화면으로 전환
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> {
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, new LoginFragment())
                        .addToBackStack(null)
                        .commit();
            });
        }

        // 💡 [정원님 추가] 로그아웃 버튼 누르면 진짜 파이어베이스 로그아웃 처리
        if (tvLogout != null) {
            tvLogout.setOnClickListener(v -> {
                mAuth.signOut(); // 🚀 서버 로그아웃
                Toast.makeText(getContext(), "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();

                // 설정 화면 새로고침 (바뀐 UI 반영)
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.frameLayout, new SettingsFragment())
                        .commit();
            });
        }

        if (itemLifeData != null) itemLifeData.setOnClickListener(v -> Toast.makeText(getContext(), "생활 데이터 연동 설정으로 이동합니다.", Toast.LENGTH_SHORT).show());
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
}