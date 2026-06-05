package com.example.off_record;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import androidx.fragment.app.Fragment;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CalendarFragment extends Fragment {

    private TextView selectedDate, detailText, tvRecordStatus, tvScoreChip, tvStressChip, tvQuestionMark;
    private TextView tvMonthTitle;
    private ImageButton btnPrevMonth, btnNextMonth;
    private GridLayout weekGrid, calendarGrid;
    private ImageView selectedEmoji;
    private FirebaseFirestore db;

    private final Map<String, String> recordedEmotionMap = new HashMap<>();
    private int currentYear;
    private int currentMonth; // 0부터 시작: 0=1월
    private int selectedYear;
    private int selectedMonth;
    private int selectedDay;

    private static final int COLOR_EMPTY_TEXT = Color.parseColor("#B7BDB7");
    private static final int COLOR_TRANSPARENT = Color.TRANSPARENT;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        db = FirebaseFirestore.getInstance();

        tvMonthTitle = view.findViewById(R.id.tvMonthTitle);
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth);
        btnNextMonth = view.findViewById(R.id.btnNextMonth);
        weekGrid = view.findViewById(R.id.weekGrid);
        calendarGrid = view.findViewById(R.id.calendarGrid);

        selectedDate = view.findViewById(R.id.selectedDate);
        detailText = view.findViewById(R.id.detailText);
        selectedEmoji = view.findViewById(R.id.selectedEmoji);
        tvQuestionMark = view.findViewById(R.id.tvQuestionMark);
        tvRecordStatus = view.findViewById(R.id.tvRecordStatus);
        tvScoreChip = view.findViewById(R.id.tvScoreChip);
        tvStressChip = view.findViewById(R.id.tvStressChip);

        Calendar today = Calendar.getInstance();
        currentYear = today.get(Calendar.YEAR);
        currentMonth = today.get(Calendar.MONTH);
        selectedYear = currentYear;
        selectedMonth = currentMonth;
        selectedDay = today.get(Calendar.DAY_OF_MONTH);

        setupWeekHeader();
        setupMonthButtons();
        updateDateDisplay(selectedYear, selectedMonth, selectedDay);
        loadRecordedDatesForMonth();

        return view;
    }

    private void setupMonthButtons() {
        btnPrevMonth.setOnClickListener(v -> moveMonth(-1));
        btnNextMonth.setOnClickListener(v -> moveMonth(1));
    }

    private void moveMonth(int amount) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(currentYear, currentMonth, 1);
        calendar.add(Calendar.MONTH, amount);

        currentYear = calendar.get(Calendar.YEAR);
        currentMonth = calendar.get(Calendar.MONTH);

        // 월 이동 시 해당 월 1일을 기본 선택합니다.
        selectedYear = currentYear;
        selectedMonth = currentMonth;
        selectedDay = 1;

        updateDateDisplay(selectedYear, selectedMonth, selectedDay);
        loadRecordedDatesForMonth();
    }

    private void setupWeekHeader() {
        weekGrid.removeAllViews();
        String[] weeks = {"일", "월", "화", "수", "목", "금", "토"};

        for (String week : weeks) {
            TextView textView = new TextView(requireContext());
            textView.setText(week);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Color.parseColor("#777777"));
            textView.setTextSize(13);
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(32);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            textView.setLayoutParams(params);
            weekGrid.addView(textView);
        }
    }

    private void loadRecordedDatesForMonth() {
        if (getActivity() == null) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", currentYear, currentMonth + 1);

        if (currentUser == null) {
            GuestRecordStore.clearIfNotToday(requireContext());
            recordedEmotionMap.clear();
            Map<String, Object> guestRecord = GuestRecordStore.getTodayRecord(requireContext());
            if (guestRecord != null) {
                String dateKey = String.valueOf(guestRecord.get("date"));
                if (dateKey.startsWith(monthPrefix)) {
                    recordedEmotionMap.put(dateKey, String.valueOf(guestRecord.get("emotion")));
                }
            }
            renderCalendar();
            return;
        }

        String uid = currentUser.getUid();
        db.collection("users").document(uid).collection("daily_records")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    recordedEmotionMap.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String dateKey = document.getId();
                        if (dateKey.startsWith(monthPrefix)) {
                            String emotion = document.getString("emotion");
                            recordedEmotionMap.put(dateKey, emotion);
                        }
                    }

                    renderCalendar();
                })
                .addOnFailureListener(e -> {
                    recordedEmotionMap.clear();
                    renderCalendar();
                    android.util.Log.e("CalendarFragment", "Error fetching monthly records", e);
                });
    }

    private void renderCalendar() {
        if (getContext() == null) return;

        calendarGrid.removeAllViews();
        tvMonthTitle.setText(String.format(Locale.getDefault(), "%04d년 %02d월", currentYear, currentMonth + 1));

        Calendar firstDayCalendar = Calendar.getInstance();
        firstDayCalendar.set(currentYear, currentMonth, 1);

        int firstDayOfWeek = firstDayCalendar.get(Calendar.DAY_OF_WEEK); // 일요일=1
        int startBlankCount = firstDayOfWeek - 1;
        int lastDay = firstDayCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < startBlankCount; i++) {
            calendarGrid.addView(createBlankDayView());
        }

        for (int day = 1; day <= lastDay; day++) {
            calendarGrid.addView(createDayView(day));
        }
    }

    private TextView createBlankDayView() {
        TextView textView = new TextView(requireContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dpToPx(46);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(2, 3, 2, 3);
        textView.setLayoutParams(params);
        return textView;
    }

    private TextView createDayView(int day) {
        TextView textView = new TextView(requireContext());
        String dateKey = String.format(Locale.getDefault(), "%04d-%02d-%02d", currentYear, currentMonth + 1, day);
        String emotion = recordedEmotionMap.get(dateKey);
        boolean hasRecord = emotion != null;
        boolean isSelected = currentYear == selectedYear && currentMonth == selectedMonth && day == selectedDay;

        textView.setText(String.valueOf(day));
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(hasRecord ? 17 : 15);
        textView.setTypeface(Typeface.DEFAULT, hasRecord ? Typeface.BOLD : Typeface.NORMAL);
        textView.setTextColor(hasRecord ? getDayNumberColor(emotion) : COLOR_EMPTY_TEXT);
        textView.setBackground(makeDayBackground(emotion, isSelected));
        textView.setOnClickListener(v -> {
            selectedYear = currentYear;
            selectedMonth = currentMonth;
            selectedDay = day;
            updateDateDisplay(selectedYear, selectedMonth, selectedDay);
            renderCalendar();
        });

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dpToPx(46);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(2, 3, 2, 3);
        textView.setLayoutParams(params);

        return textView;
    }

    private GradientDrawable makeDayBackground(String emotion, boolean isSelected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dpToPx(18));

        boolean hasRecord = emotion != null;
        drawable.setColor(hasRecord ? getEmotionFaceColor(emotion) : COLOR_TRANSPARENT);

        if (isSelected) {
            int strokeColor = hasRecord ? getEmotionStrokeColor(emotion) : Color.parseColor("#8B918B");
            drawable.setStroke(dpToPx(2), strokeColor);
        }

        return drawable;
    }

    private void updateDateDisplay(int year, int month, int day) {
        String dateKey = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
        String dayText = String.format(Locale.getDefault(), "%02d월 %02d일", month + 1, day);

        selectedDate.setText(dayText);
        loadRecordForDate(dateKey);
    }

    private void loadRecordForDate(String dateKey) {
        if (getActivity() == null) return;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            GuestRecordStore.clearIfNotToday(requireContext());
            Map<String, Object> guestRecord = GuestRecordStore.getTodayRecord(requireContext());
            if (guestRecord != null && dateKey.equals(String.valueOf(guestRecord.get("date")))) {
                showRecordDetail(
                        String.valueOf(guestRecord.get("emotion")),
                        String.valueOf(guestRecord.get("score")),
                        String.valueOf(guestRecord.get("diary")),
                        String.valueOf(guestRecord.get("stress"))
                );
            } else {
                showEmptyState("게스트 기록은 오늘 하루 기록만 볼 수 있습니다.");
            }
            return;
        }

        String uid = currentUser.getUid();
        db.collection("users").document(uid).collection("daily_records").document(dateKey).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String emotion = documentSnapshot.getString("emotion");
                        Long scoreLong = documentSnapshot.getLong("score");
                        String score = (scoreLong != null) ? String.valueOf(scoreLong) : "-";
                        String diary = documentSnapshot.getString("diary");
                        String stress = documentSnapshot.getString("stress");
                        showRecordDetail(emotion, score, diary, stress);
                    } else {
                        showEmptyState("이날의 기록이 없습니다.");
                    }
                })
                .addOnFailureListener(e -> {
                    showEmptyState("기록을 불러오지 못했습니다.");
                    android.util.Log.e("CalendarFragment", "Error fetching record", e);
                });
    }

    private void showRecordDetail(String emotion, String score, String diary, String stress) {
        if (stress == null || stress.isEmpty() || stress.equals("미선택")) stress = "-";
        if (score == null || score.isEmpty()) score = "-";

        showRecordEmoji(emotion);
        tvRecordStatus.setText("감정 기록");
        tvRecordStatus.setTextColor(getEmotionStatusColor(emotion));
        tvRecordStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvScoreChip.setText("점수 | " + score + "점");
        tvStressChip.setText("스트레스 | " + stress);
        detailText.setText(diary == null || diary.isEmpty() ? "작성된 일기 내용이 없습니다." : diary);
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
        tvRecordStatus.setTextColor(Color.parseColor("#777777"));
        tvRecordStatus.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        tvScoreChip.setText("점수 | -");
        tvStressChip.setText("스트레스 | -");
        detailText.setText(message);
    }

    private int getEmotionFaceColor(String emotionCode) {
        if (emotionCode == null) return Color.parseColor("#E3EFE5");

        switch (emotionCode) {
            case "emo1": return Color.parseColor("#6F7573"); // one.png 얼굴색
            case "emo2": return Color.parseColor("#45714D"); // two.png 얼굴색
            case "emo3": return Color.parseColor("#85B785"); // three.png 얼굴색
            case "emo4": return Color.parseColor("#CDE099"); // four.png 얼굴색
            case "emo5": return Color.parseColor("#FEE99C"); // five.png 얼굴색
            default: return Color.parseColor("#E3EFE5");
        }
    }

    private int getDayNumberColor(String emotionCode) {
        return Color.BLACK;
    }

    private int getEmotionStrokeColor(String emotionCode) {
        if (emotionCode == null) return Color.parseColor("#8B918B");

        switch (emotionCode) {
            case "emo1": return Color.parseColor("#D8BE4F");
            case "emo2": return Color.parseColor("#97B85F");
            case "emo3": return Color.parseColor("#4F965C");
            case "emo4": return Color.parseColor("#2F5F3B");
            case "emo5": return Color.parseColor("#555D5A");
            default: return Color.parseColor("#8B918B");
        }
    }

    private int getEmotionStatusColor(String emotionCode) {
        if (emotionCode == null) return Color.parseColor("#2F4637");

        switch (emotionCode) {
            case "emo1": return Color.parseColor("#C9A93C");
            case "emo2": return Color.parseColor("#7EA052");
            case "emo3": return Color.parseColor("#4F965C");
            case "emo4": return Color.parseColor("#3C7852");
            case "emo5": return Color.parseColor("#6F7573");
            default: return Color.parseColor("#2F4637");
        }
    }

    private int getEmojiImage(String emotionCode) {
        if (emotionCode == null) return R.drawable.three;

        switch (emotionCode) {
            case "emo1": return R.drawable.one;
            case "emo2": return R.drawable.two;
            case "emo3": return R.drawable.three;
            case "emo4": return R.drawable.four;
            case "emo5": return R.drawable.five;
            default: return R.drawable.three;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}
