package com.example.off_record;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.renderer.YAxisRenderer;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsFragment extends Fragment {

    private LineChart lineChart;
    private MaterialButtonToggleGroup toggleGroup;
    private TextView tvTotalCount;
    private TextView tvStreakTitle;
    private TextView tvStreakCount;
    private TextView tvStreakMessage;

    private FirebaseFirestore db;
    private String currentUid;
    private boolean isGuestMode = false;

    private HashSet<String> diaryDatesSet = new HashSet<>();
    private HashMap<String, Float> diaryMoodMap = new HashMap<>();

    public StatsFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        lineChart = view.findViewById(R.id.lineChart);
        toggleGroup = view.findViewById(R.id.toggleGroup);
        tvTotalCount = view.findViewById(R.id.tvTotalCount);
        tvStreakTitle = view.findViewById(R.id.tvStreakTitle);
        tvStreakCount = view.findViewById(R.id.tvStreakCount);
        tvStreakMessage = view.findViewById(R.id.tvStreakMessage);

        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = (currentUser != null) ? currentUser.getUid() : null;
        isGuestMode = (currentUid == null);

        setupLineChart();
        setupToggleButtons();

        if (isGuestMode) {
            loadGuestDiaryData();
        } else {
            loadDiaryDataFromServer();
        }

        return view;
    }

    private void setupLineChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getAxisRight().setEnabled(false);

        lineChart.setViewPortOffsets(100f, 60f, 100f, 80f);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(1f);
        leftAxis.setAxisMaximum(5f);
        leftAxis.setLabelCount(5, true);
        leftAxis.setDrawLabels(true);
        leftAxis.setTextColor(Color.TRANSPARENT);
        leftAxis.setDrawAxisLine(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(11f);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);

        EmojiYAxisRenderer emojiRenderer = new EmojiYAxisRenderer(
                lineChart.getViewPortHandler(),
                leftAxis,
                lineChart.getTransformer(YAxis.AxisDependency.LEFT)
        );
        lineChart.setRendererLeftYAxis(emojiRenderer);

        lineChart.setTouchEnabled(true);
        lineChart.animateX(800);
    }

    private class EmojiYAxisRenderer extends YAxisRenderer {
        private Bitmap[] emojiBitmaps = new Bitmap[5];
        private int emojiSize = 65;

        public EmojiYAxisRenderer(ViewPortHandler viewPortHandler, YAxis yAxis, Transformer transformer) {
            super(viewPortHandler, yAxis, transformer);
            if (getContext() != null) {
                emojiBitmaps[0] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.one);
                emojiBitmaps[1] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.two);
                emojiBitmaps[2] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.three);
                emojiBitmaps[3] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.four);
                emojiBitmaps[4] = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.five);

                for (int i = 0; i < 5; i++) {
                    if (emojiBitmaps[i] != null) {
                        emojiBitmaps[i] = Bitmap.createScaledBitmap(emojiBitmaps[i], emojiSize, emojiSize, true);
                    }
                }
            }
        }

        @Override
        protected void drawYLabels(Canvas c, float fixedPosition, float[] positions, float offset) {
            super.drawYLabels(c, fixedPosition, positions, offset);

            float safeXPosition = mViewPortHandler.contentLeft() - emojiSize - 20f;

            int emojiIndex = 0;
            for (int i = 0; i < positions.length; i += 2) {
                if (i + 1 >= positions.length) break;
                float yPos = positions[i + 1];

                if (emojiIndex < 5 && emojiBitmaps[emojiIndex] != null) {
                    float finalY = yPos - (emojiSize / 2f);
                    c.drawBitmap(emojiBitmaps[emojiIndex], safeXPosition, finalY, new Paint());
                }
                emojiIndex++;
            }
        }
    }

    private void setupToggleButtons() {
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnWeek) {
                    updateChartByPeriod("1주");
                } else if (checkedId == R.id.btnMonth) {
                    updateChartByPeriod("1개월");
                } else if (checkedId == R.id.btnYear) {
                    updateChartByPeriod("1년");
                }
            }
        });
    }

    private void updateChartByPeriod(String period) {
        List<Entry> entries = new ArrayList<>();
        XAxis xAxis = lineChart.getXAxis();
        String label = isGuestMode ? "오늘 게스트 감정 기록" : "내 기분 추이 흐름";

        float totalScore = 0f;
        int scoreCount = 0;
        List<String> periodDates = new ArrayList<>();

        switch (period) {
            case "1주":
                String[] weekLabels = {"월", "화", "수", "목", "금", "토", "일"};
                Calendar weekCal = Calendar.getInstance();
                int todayDow = weekCal.get(Calendar.DAY_OF_WEEK);
                int diffToMonday = (todayDow == Calendar.SUNDAY) ? 6 : todayDow - 2;
                weekCal.add(Calendar.DATE, -diffToMonday);

                for (int i = 0; i < 7; i++) {
                    String fullDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(weekCal.getTime());
                    periodDates.add(fullDateKey);
                    if (diaryMoodMap.containsKey(fullDateKey)) {
                        float score = diaryMoodMap.get(fullDateKey);
                        entries.add(new Entry((float) i, score));
                        totalScore += score;
                        scoreCount++;
                    }
                    weekCal.add(Calendar.DATE, 1);
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(weekLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(6f);
                xAxis.setLabelCount(7, true);
                label = isGuestMode ? "게스트 이번 주 감정 추이" : "이번 주 감정 추이";
                break;

            case "1개월":
                ArrayList<String> monthLabels = new ArrayList<>();
                SimpleDateFormat labelSdf = new SimpleDateFormat("MM/dd", Locale.KOREA);
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, -29);

                for (int i = 0; i < 30; i++) {
                    monthLabels.add(labelSdf.format(cal.getTime()));
                    String fullDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(cal.getTime());
                    periodDates.add(fullDateKey);
                    if (diaryMoodMap.containsKey(fullDateKey)) {
                        float score = diaryMoodMap.get(fullDateKey);
                        entries.add(new Entry((float) i, score));
                        totalScore += score;
                        scoreCount++;
                    }
                    cal.add(Calendar.DATE, 1);
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(monthLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(29f);
                xAxis.setLabelCount(5, false);
                label = isGuestMode ? "게스트 최근 1개월 감정 흐름" : "최근 1개월 감정 흐름";
                break;

            case "1년":
                String[] yearLabels = {"1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"};
                HashMap<Integer, ArrayList<Float>> monthMoodMap = new HashMap<>();

                Calendar yearCal = Calendar.getInstance();
                int currentYearValue = yearCal.get(Calendar.YEAR);

                for (Map.Entry<String, Float> entry : diaryMoodMap.entrySet()) {
                    try {
                        Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(entry.getKey());
                        Calendar dateCal = Calendar.getInstance();
                        dateCal.setTime(date);
                        if (dateCal.get(Calendar.YEAR) == currentYearValue) {
                            int monthIndex = dateCal.get(Calendar.MONTH);
                            if (!monthMoodMap.containsKey(monthIndex)) {
                                monthMoodMap.put(monthIndex, new ArrayList<>());
                            }
                            monthMoodMap.get(monthIndex).add(entry.getValue());
                            periodDates.add(entry.getKey());
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }

                for (int i = 0; i < 12; i++) {
                    ArrayList<Float> moods = monthMoodMap.get(i);
                    if (moods != null && !moods.isEmpty()) {
                        float sum = 0f;
                        for (Float mood : moods) sum += mood;
                        float avg = sum / moods.size();
                        entries.add(new Entry((float) i, avg));
                        totalScore += avg;
                        scoreCount++;
                    }
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(yearLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(11f);
                xAxis.setLabelCount(12, true);
                label = isGuestMode ? "게스트 올해 월별 감정 흐름" : "올해 월별 감정 흐름";
                break;
        }

        updateAnalysisResult(period, totalScore, scoreCount);
        calculateMaxStreakInPeriod(periodDates);

        if (entries.isEmpty()) {
            lineChart.clear();
            lineChart.setNoDataText("아직 기록된 기분 데이터가 없습니다.");
            lineChart.invalidate();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(Color.parseColor("#2E7D32"));
        dataSet.setCircleColor(Color.parseColor("#4CAF50"));
        dataSet.setLineWidth(3f);
        dataSet.setCircleRadius(5.5f);
        dataSet.setDrawCircleHole(true);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        dataSet.setDrawFilled(true);
        android.graphics.drawable.Drawable drawable = 
            new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#4CAF50"), Color.parseColor("#00FFFFFF")}
            );
        dataSet.setFillDrawable(drawable);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.invalidate();
    }

    private void calculateMaxStreakInPeriod(List<String> periodDates) {
        if (periodDates.isEmpty()) {
            if (tvStreakCount != null) tvStreakCount.setText("0일");
            return;
        }

        Collections.sort(periodDates);
        int maxStreak = 0;
        int currentStreak = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

        Calendar prevCal = null;

        for (String dateStr : periodDates) {
            if (!diaryDatesSet.contains(dateStr)) {
                maxStreak = Math.max(maxStreak, currentStreak);
                currentStreak = 0;
                prevCal = null;
                continue;
            }

            try {
                Date date = sdf.parse(dateStr);
                Calendar currentCal = Calendar.getInstance();
                currentCal.setTime(date);
                clearTime(currentCal);

                if (prevCal == null) {
                    currentStreak = 1;
                } else {
                    Calendar expectedCal = (Calendar) prevCal.clone();
                    expectedCal.add(Calendar.DATE, 1);
                    if (currentCal.equals(expectedCal)) {
                        currentStreak++;
                    } else {
                        maxStreak = Math.max(maxStreak, currentStreak);
                        currentStreak = 1;
                    }
                }
                prevCal = currentCal;
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        maxStreak = Math.max(maxStreak, currentStreak);

        if (tvStreakCount != null) {
            tvStreakCount.setText(maxStreak + "일");
        }
    }

    private void updateAnalysisResult(String period, float totalScore, int count) {
        if (count == 0) {
            tvStreakMessage.setText(period + " 동안의 기록이 없습니다. 일기를 작성해 보세요!");
            return;
        }

        float avgScore = totalScore / count;
        String emoji = getEmojiForScore(avgScore);
        String moodText = getMoodTextForScore(avgScore);

        StringBuilder sb = new StringBuilder();
        sb.append(period).append(" 평균 감정: ").append(emoji).append(" (").append(moodText).append(")\n");
        sb.append("기록 횟수: ").append(count).append("회\n\n");
        
        if (avgScore >= 4.0f) {
            sb.append("대체로 긍정적인 감정을 유지하고 계시네요! 지금처럼 나를 위한 시간을 가져보세요. ✨");
        } else if (avgScore >= 2.5f) {
            sb.append("평온한 감정 흐름을 보이고 있습니다. 조금 더 활기찬 활동을 시도해보는 건 어떨까요? 🌱");
        } else {
            sb.append("요즘 마음이 조금 무거우신 것 같아요. 충분한 휴식과 함께 따뜻한 차 한 잔 어떠신가요? 🍵");
        }

        tvStreakMessage.setText(sb.toString());
    }

    private String getEmojiForScore(float score) {
        if (score <= 1.5f) return "😠";
        if (score <= 2.5f) return "😔";
        if (score <= 3.5f) return "😐";
        if (score <= 4.5f) return "😊";
        return "😆";
    }

    private String getMoodTextForScore(float score) {
        if (score <= 1.5f) return "매우 안 좋아요";
        if (score <= 2.5f) return "안 좋아요";
        if (score <= 3.5f) return "보통이에요";
        if (score <= 4.5f) return "좋아요";
        return "매우 좋아요";
    }

    private void loadDiaryDataFromServer() {
        if (currentUid == null) {
            loadGuestDiaryData();
            return;
        }

        db.collection("users")
                .document(currentUid)
                .collection("daily_records")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        diaryDatesSet.clear();
                        diaryMoodMap.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String dateStr = document.getId();
                            Long emotionScore = document.getLong("emotionScore");
                            float moodScore;
                            if (emotionScore != null) {
                                moodScore = emotionScore.floatValue();
                            } else {
                                String emotion = document.getString("emotion");
                                moodScore = emotionToMoodScore(emotion);
                            }

                            if (dateStr != null) {
                                diaryDatesSet.add(dateStr);
                                diaryMoodMap.put(dateStr, moodScore);
                            }
                        }
                        if (tvTotalCount != null) tvTotalCount.setText(diaryDatesSet.size() + "회");
                        updateChartByPeriod("1주");
                    } else {
                        Toast.makeText(getContext(), "데이터 로드 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadGuestDiaryData() {
        diaryDatesSet.clear();
        diaryMoodMap.clear();

        SharedPreferences pref = requireContext().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        String allRecords = pref.getString("all_records", "");

        if (!allRecords.isEmpty()) {
            String[] recordsArray = allRecords.split("##");
            for (String record : recordsArray) {
                if (record.isEmpty()) continue;
                String[] detail = record.split("\\|");
                if (detail.length >= 2) {
                    String fullTime = detail[0];
                    String dateKey = fullTime.split(" ")[0];
                    String emotion = detail[1];
                    
                    float moodScore;
                    // Try to read explicitly stored emotionScore (index 12) if available
                    if (detail.length >= 13) {
                        try {
                            moodScore = Float.parseFloat(detail[12]);
                        } catch (Exception e) {
                            moodScore = emotionToMoodScore(emotion);
                        }
                    } else {
                        moodScore = emotionToMoodScore(emotion);
                    }

                    diaryDatesSet.add(dateKey);
                    diaryMoodMap.put(dateKey, moodScore);
                }
            }
        }

        if (tvTotalCount != null) tvTotalCount.setText(diaryDatesSet.size() + "회");
        updateChartByPeriod("1주");
    }

    private float emotionToMoodScore(String emotion) {
        if (emotion == null) return 3.0f;
        String normalized = normalizeEmotionValue(emotion);
        switch (normalized) {
            case "매우_안좋아요": return 1.0f;
            case "안좋아요": return 2.0f;
            case "보통이에요": return 3.0f;
            case "좋아요": return 4.0f;
            case "매우_좋아요": return 5.0f;
            default: return 3.0f;
        }
    }

    private void clearTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private String normalizeEmotionValue(String emotionValue) {
        if (emotionValue == null) return "";
        String value = emotionValue.trim();

        if ("emo1".equals(value) || "one".equals(value) || "매우_안좋아요".equals(value) || "매우 안 좋아요".equals(value)) return "매우_안좋아요";
        if ("emo2".equals(value) || "two".equals(value) || "안좋아요".equals(value) || "안 좋아요".equals(value)) return "안좋아요";
        if ("emo3".equals(value) || "three".equals(value) || "보통이에요".equals(value)) return "보통이에요";
        if ("emo4".equals(value) || "four".equals(value) || "좋아요".equals(value)) return "좋아요";
        if ("emo5".equals(value) || "five".equals(value) || "매우_좋아요".equals(value) || "매우 좋아요".equals(value)) return "매우_좋아요";

        return value;
    }

    private String getEmotionLabel(String emotionValue) {
        String value = normalizeEmotionValue(emotionValue);

        if ("매우_안좋아요".equals(value)) return "매우 안 좋아요";
        if ("안좋아요".equals(value)) return "안 좋아요";
        if ("보통이에요".equals(value)) return "보통이에요";
        if ("좋아요".equals(value)) return "좋아요";
        if ("매우_좋아요".equals(value)) return "매우 좋아요";

        return "감정 미선택";
    }
}
