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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CalendarFragment extends Fragment {

    private TextView selectedDate, detailText, tvRecordStatus, tvScoreChip, tvStressChip, tvQuestionMark;
    private TextView tvMonthTitle, tvCalendarTitle;
    private ImageButton btnPrevMonth, btnNextMonth;
    private GridLayout weekGrid, calendarGrid;
    private ImageView selectedEmoji;
    private FirebaseFirestore db;

    private final Map<String, String> recordedEmotionMap = new HashMap<>();
    private int currentYear;
    private int currentMonth;
    private int selectedYear;
    private int selectedMonth;
    private int selectedDay;

    private int colorButterYellow;
    private int colorSoftCream;
    private int colorTextPremiumMain;
    private int colorTextPremiumSub;
    private int colorMoodHappy;
    private int colorMoodGood;
    private int colorMoodNormal;
    private int colorMoodTired;
    private int colorMoodAngry;
    private int colorMintDark;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            // 🌟 Professional White Palette Sync
            colorButterYellow = ContextCompat.getColor(getContext(), R.color.brand_primary);
            colorSoftCream = ContextCompat.getColor(getContext(), R.color.bg_white_pure);
            colorTextPremiumMain = ContextCompat.getColor(getContext(), R.color.text_main);
            colorTextPremiumSub = ContextCompat.getColor(getContext(), R.color.text_sub);
            colorMoodHappy = ContextCompat.getColor(getContext(), R.color.mood_5);
            colorMoodGood = ContextCompat.getColor(getContext(), R.color.mood_4);
            colorMoodNormal = ContextCompat.getColor(getContext(), R.color.mood_3);
            colorMoodTired = ContextCompat.getColor(getContext(), R.color.mood_2);
            colorMoodAngry = ContextCompat.getColor(getContext(), R.color.mood_1);
            colorMintDark = ContextCompat.getColor(getContext(), R.color.brand_primary);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        db = FirebaseFirestore.getInstance();

        tvCalendarTitle = view.findViewById(R.id.tvCalendarTitle);
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

        updateUserGreeting();

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

    private void updateUserGreeting() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (tvCalendarTitle != null) {
            if (user != null) {
                String name = user.getDisplayName();
                if (name == null || name.isEmpty()) {
                    name = user.getEmail();
                }
                if (name != null && name.contains("@")) {
                    name = name.split("@")[0];
                }
                tvCalendarTitle.setText(name + "님");
            } else {
                tvCalendarTitle.setText("로그인이 필요합니다");
            }
        }
    }

    private void setupMonthButtons() {
        if (btnPrevMonth != null) btnPrevMonth.setOnClickListener(v -> moveMonth(-1));
        if (btnNextMonth != null) btnNextMonth.setOnClickListener(v -> moveMonth(1));
    }

    private void moveMonth(int amount) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(currentYear, currentMonth, 1);
        calendar.add(Calendar.MONTH, amount);

        currentYear = calendar.get(Calendar.YEAR);
        currentMonth = calendar.get(Calendar.MONTH);

        renderCalendar();
        loadRecordedDatesForMonth();
    }

    /**
     * 1. 요일 헤더 영역 시안급 무드로 전면 리뉴얼
     */
    private void setupWeekHeader() {
        if (weekGrid == null) return;
        weekGrid.removeAllViews();
        String[] weeks = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        for (String week : weeks) {
            TextView textView = new TextView(requireContext());
            textView.setText(week);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(colorTextPremiumSub);
            textView.setTextSize(12);
            textView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL)); // 모던 서체 적용

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(32);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            textView.setLayoutParams(params);
            weekGrid.addView(textView);
        }
    }

    private void loadRecordedDatesForMonth() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "guest_user";
        String monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", currentYear, currentMonth + 1);

        db.collection("users").document(uid).collection("daily_records")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    recordedEmotionMap.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String dateKey = document.getId();
                        if (dateKey.startsWith(monthPrefix)) {
                            recordedEmotionMap.put(dateKey, document.getString("emotion"));
                        }
                    }
                    renderCalendar();
                })
                .addOnFailureListener(e -> renderCalendar());
    }

    private void renderCalendar() {
        if (getContext() == null || calendarGrid == null) return;

        calendarGrid.removeAllViews();
        if (tvMonthTitle != null) {
            Calendar titleCal = Calendar.getInstance();
            titleCal.set(currentYear, currentMonth, 1);
            String monthName = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(titleCal.getTime());
            tvMonthTitle.setText(monthName);
        }

        Calendar firstDayCalendar = Calendar.getInstance();
        firstDayCalendar.set(currentYear, currentMonth, 1);

        int firstDayOfWeek = firstDayCalendar.get(Calendar.DAY_OF_WEEK);
        int startBlankCount = firstDayOfWeek - 1;
        int lastDay = firstDayCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < startBlankCount; i++) {
            calendarGrid.addView(createBlankDayView());
        }

        for (int day = 1; day <= lastDay; day++) {
            calendarGrid.addView(createDayView(day));
        }
    }

    private View createBlankDayView() {
        View view = new View(requireContext());
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dpToPx(52);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        view.setLayoutParams(params);
        return view;
    }

    private TextView createDayView(int day) {
        TextView textView = new TextView(requireContext());
        String dateKey = String.format(Locale.getDefault(), "%04d-%02d-%02d", currentYear, currentMonth + 1, day);
        String emotion = recordedEmotionMap.get(dateKey);
        boolean hasRecord = emotion != null;
        boolean isSelected = currentYear == selectedYear && currentMonth == selectedMonth && day == selectedDay;

        textView.setText(String.valueOf(day));
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(14);
        textView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));

        // 3D 조약돌 Pill 형태 백그라운드 드로어블 매칭
        textView.setBackgroundResource(R.drawable.calendar_day_pill);

        // 3D 파스텔 톤 5색 스타일 렌더링 시작
        apply3DDayStyle(textView, emotion, isSelected, hasRecord);

        textView.setOnClickListener(v -> {
            selectedYear = currentYear;
            selectedMonth = currentMonth;
            selectedDay = day;
            updateDateDisplay(selectedYear, selectedMonth, selectedDay);
            renderCalendar();
        });

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dpToPx(52);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4)); // 시안처럼 시원한 독립 배치 형성 마진
        textView.setLayoutParams(params);

        return textView;
    }

    /**
     * 2. 날짜 조약돌 버튼에 파스텔 밀키 3D 5색을 수놓는 입체 스타일 가이드
     */
    private void apply3DDayStyle(TextView view, String emotion, boolean isSelected, boolean hasRecord) {
        if (isSelected) {
            // 사용자가 오늘 날짜나 특정 날짜를 터치했을 때 붕 뜨는 하이라이트 원형 효과
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(dpToPx(40));
            drawable.setColor(colorButterYellow);
            view.setBackground(drawable);
            view.setTextColor(Color.WHITE); // 선택된 칸 글자는 하얀색 고대비 처리
            view.setElevation(dpToPx(5)); // 물리적 높이 추가
        } else if (hasRecord) {
            // 일기 기록이 채워져 있는 칸은 새 3D 밀키 파스텔 색상으로 정교하게 렌더링
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(dpToPx(40));
            drawable.setColor(getEmotionColor(emotion)); // ⭕ 3D 파스텔 감정 컬러 대입!
            view.setBackground(drawable);
            view.setTextColor(colorTextPremiumMain); // 파스텔 위에는 아늑한 차콜색 글자로 가독성 완성!
            view.setAlpha(1.0f);
        } else {
            // 기록이 아직 채워지지 않은 평범한 날짜들
            view.setTextColor(colorTextPremiumSub);
        }
    }

    /**
     * 3. [완벽 싱크] 사용자가 입력한 이모지 색깔(emo1~5)에 대응해 전문적인 파스텔 리소스를 반환
     */
    private int getEmotionColor(String emotionCode) {
        if (emotionCode == null) return colorSoftCream;

        switch (emotionCode) {
            case "emo1":
                return ContextCompat.getColor(requireContext(), R.color.mood_1);
            case "emo2":
                return ContextCompat.getColor(requireContext(), R.color.mood_2);
            case "emo3":
                return ContextCompat.getColor(requireContext(), R.color.mood_3);
            case "emo4":
                return ContextCompat.getColor(requireContext(), R.color.mood_4);
            case "emo5":
                return ContextCompat.getColor(requireContext(), R.color.mood_5);
            default:
                return colorSoftCream;
        }
    }

    private void updateDateDisplay(int year, int month, int day) {
        String dateKey = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);

        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day);
        String dayText = new SimpleDateFormat("MMMM d'th'", Locale.ENGLISH).format(cal.getTime());

        if (selectedDate != null) selectedDate.setText(dayText);
        loadRecordForDate(dateKey);
    }

    private void loadRecordForDate(String dateKey) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "guest_user";

        db.collection("users").document(uid).collection("daily_records").document(dateKey).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String emotion = documentSnapshot.getString("emotion");
                        Long scoreLong = documentSnapshot.getLong("score");
                        String score = (scoreLong != null) ? String.valueOf(scoreLong) : "-";
                        String diary = documentSnapshot.getString("diary");
                        String stress = documentSnapshot.getString("stress");
                        if (stress == null || stress.equals("미선택")) stress = "-";

                        showRecordEmoji(emotion);
                        if (tvRecordStatus != null) {
                            tvRecordStatus.setText("Captured");
                            tvRecordStatus.setTextColor(colorMintDark);
                        }
                        if (tvScoreChip != null) tvScoreChip.setText("Score | " + score);
                        if (tvStressChip != null) tvStressChip.setText("Stress | " + stress);
                        if (detailText != null) {
                            detailText.setText(diary == null || diary.isEmpty() ? "No story recorded for this day." : diary);
                            detailText.setTextColor(colorTextPremiumMain);
                        }
                    } else {
                        showEmptyState();
                    }
                })
                .addOnFailureListener(e -> showEmptyState());
    }

    private void showRecordEmoji(String emotion) {
        if (selectedEmoji != null) {
            selectedEmoji.setVisibility(View.VISIBLE);
            selectedEmoji.setImageResource(getEmojiImage(emotion));
        }
        if (tvQuestionMark != null) tvQuestionMark.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        if (selectedEmoji != null) selectedEmoji.setVisibility(View.GONE);
        if (tvQuestionMark != null) tvQuestionMark.setVisibility(View.VISIBLE);
        if (tvRecordStatus != null) {
            tvRecordStatus.setText("Not recorded");
            tvRecordStatus.setTextColor(colorTextPremiumSub);
        }
        if (tvScoreChip != null) tvScoreChip.setText("Score | -");
        if (tvStressChip != null) tvStressChip.setText("Stress | -");
        if (detailText != null) {
            detailText.setText("What story did you capture this day?");
            detailText.setTextColor(colorTextPremiumSub);
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
        if (getContext() == null) return dp;
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}