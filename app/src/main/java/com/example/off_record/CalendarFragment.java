package com.example.off_record;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Calendar;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private TextView selectedDate, detailText, tvRecordStatus, tvScoreChip, tvStressChip, tvQuestionMark;
    private ImageView selectedEmoji;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        CalendarView calendarView = view.findViewById(R.id.calendarView);
        selectedDate = view.findViewById(R.id.selectedDate);
        detailText = view.findViewById(R.id.detailText);
        selectedEmoji = view.findViewById(R.id.selectedEmoji);
        tvQuestionMark = view.findViewById(R.id.tvQuestionMark);
        tvRecordStatus = view.findViewById(R.id.tvRecordStatus);
        tvScoreChip = view.findViewById(R.id.tvScoreChip);
        tvStressChip = view.findViewById(R.id.tvStressChip);

        Calendar today = Calendar.getInstance();

        // 앱을 처음 열었을 때 캘린더의 선택 날짜가 항상 오늘 날짜로 맞춰지도록 설정
        calendarView.setDate(today.getTimeInMillis(), false, true);

        updateDateDisplay(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH)
        );

        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView view, int year, int month, int dayOfMonth) {
                updateDateDisplay(year, month, dayOfMonth);
            }
        });

        return view;
    }

    private void updateDateDisplay(int year, int month, int day) {
        String dateKey = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
        String dayText = String.format(Locale.getDefault(), "%02d일", day);

        selectedDate.setText(dayText);
        loadRecordForDate(dateKey);
    }

    private void loadRecordForDate(String dateKey) {
        if (getActivity() == null) return;

        SharedPreferences pref = getActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        String allRecords = pref.getString("all_records", "");

        if (allRecords.isEmpty()) {
            showEmptyState("이날의 기록이 없습니다.");
            return;
        }

        String[] recordsArray = allRecords.split("##");
        for (String record : recordsArray) {
            if (record == null || record.trim().isEmpty()) continue;

            if (record.startsWith(dateKey)) {
                String[] detail = record.split("\\|");
                if (detail.length >= 5) {
                    String emotion = detail[1];
                    String score = detail[2];
                    String diary = detail[3];
                    String stress = detail.length > 6 ? detail[6].trim() : "미선택";
                    if (stress.isEmpty() || stress.equals("미선택")) stress = "-";

                    showRecordEmoji(emotion);
                    tvRecordStatus.setText("감정 기록");
                    tvScoreChip.setText("점수 | " + score + "점");
                    tvStressChip.setText("스트레스 | " + stress);
                    detailText.setText(diary.isEmpty() ? "작성된 일기 내용이 없습니다." : diary);
                    return;
                }
            }
        }

        showEmptyState("이날의 기록이 없습니다.");
    }

    private void showRecordEmoji(String emotion) {
        selectedEmoji.setVisibility(View.VISIBLE);
        tvQuestionMark.setVisibility(View.GONE);
        selectedEmoji.setImageResource(getEmojiImage(emotion));
    }

    private void showEmptyState(String message) {
        selectedEmoji.setVisibility(View.GONE);
        tvQuestionMark.setVisibility(View.VISIBLE);
        tvRecordStatus.setText("기록 없음");
        tvScoreChip.setText("점수 | -");
        tvStressChip.setText("스트레스 | -");
        detailText.setText(message);
    }

    private int getEmojiImage(String emotionCode) {
        switch (emotionCode) {
            case "emo1": return R.drawable.one;
            case "emo2": return R.drawable.two;
            case "emo3": return R.drawable.three;
            case "emo4": return R.drawable.four;
            case "emo5": return R.drawable.five;
            default: return R.drawable.three;
        }
    }
}
