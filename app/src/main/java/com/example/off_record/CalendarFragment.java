package com.example.off_record;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.Calendar;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private TextView selectedDate, detailText, tvRecordStatus, tvScoreChip, tvStressChip, tvQuestionMark;
    private ImageView selectedEmoji;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        db = FirebaseFirestore.getInstance();
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

        // 💡 [5단계 격리 반영] 현재 로그인한 유저의 고유 UID를 가져옵니다.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "guest_user";

        // 💡 [5단계 격리 반영] 공용 보관함이 아닌, users/{uid}/daily_records 경로에서 해당 날짜의 데이터를 가져옵니다.
        db.collection("users").document(uid).collection("daily_records").document(dateKey).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // DB에 데이터가 있는 경우
                        String emotion = documentSnapshot.getString("emotion");
                        Long scoreLong = documentSnapshot.getLong("score");
                        String score = (scoreLong != null) ? String.valueOf(scoreLong) : "-";
                        String diary = documentSnapshot.getString("diary");
                        String stress = documentSnapshot.getString("stress");
                        if (stress == null || stress.isEmpty() || stress.equals("미선택")) stress = "-";

                        showRecordEmoji(emotion);
                        tvRecordStatus.setText("감정 기록");
                        tvScoreChip.setText("점수 | " + score + "점");
                        tvStressChip.setText("스트레스 | " + stress);
                        detailText.setText(diary == null || diary.isEmpty() ? "작성된 일기 내용이 없습니다." : diary);
                    } else {
                        // DB에 데이터가 없으면 빈 상태를 표시합니다.
                        showEmptyState("이날의 기록이 없습니다.");
                    }
                })
                .addOnFailureListener(e -> {
                    showEmptyState("기록을 불러오지 못했습니다.");
                    android.util.Log.e("CalendarFragment", "Error fetching record", e);
                });
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