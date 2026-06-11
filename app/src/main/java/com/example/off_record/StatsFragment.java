package com.example.off_record;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
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
import java.util.concurrent.Executor;

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

    private GenerativeModelFutures model;

    private static HashMap<String, String> aiCacheMap = new HashMap<>();
    private static HashSet<String> lastDiaryDatesSet = new HashSet<>();
    private static HashMap<String, Float> lastDiaryMoodMap = new HashMap<>();
    private static HashMap<String, String> lastDiaryTextMap = new HashMap<>();

    private HashSet<String> diaryDatesSet = new HashSet<>();
    private HashMap<String, Float> diaryMoodMap = new HashMap<>();
    private HashMap<String, String> diaryTextMap = new HashMap<>();

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

        try {
            GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.1-flash-lite");
            model = GenerativeModelFutures.from(ai);
        } catch (Exception e) {
            Log.e("GeminiError", "StatsFragment 모델 초기화 에러: " + e.getMessage());
        }

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
        String label = isGuestMode ? "게스트 감정 추이" : "내 감정 추이 흐름";

        float totalScoreSum = 0f;
        int scorePointCount = 0;
        int totalLogCountInPeriod = 0;
        List<String> periodDates = new ArrayList<>();

        StringBuilder diaryBundle = new StringBuilder();

        switch (period) {
            case "1주":
                String[] weekLabels = {"일", "월", "화", "수", "목", "금", "토"};
                Calendar weekCal = Calendar.getInstance();
                int todayDow = weekCal.get(Calendar.DAY_OF_WEEK);
                int diffToSunday = todayDow - Calendar.SUNDAY;
                weekCal.add(Calendar.DATE, -diffToSunday);

                for (int i = 0; i < 7; i++) {
                    String fullDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(weekCal.getTime());
                    periodDates.add(fullDateKey);
                    if (diaryMoodMap.containsKey(fullDateKey)) {
                        float score = diaryMoodMap.get(fullDateKey);
                        entries.add(new Entry((float) i, score));
                        totalScoreSum += score;
                        scorePointCount++;

                        String diaryText = diaryTextMap.get(fullDateKey);
                        if (diaryText == null || diaryText.trim().isEmpty()) diaryText = "없음";
                        diaryBundle.append(String.format("- %s (기분: %s): %s\n", fullDateKey, getMoodTextForScore(score), diaryText));
                    }
                    weekCal.add(Calendar.DATE, 1);
                }
                totalLogCountInPeriod = scorePointCount;
                xAxis.setValueFormatter(new IndexAxisValueFormatter(weekLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(6f);
                xAxis.setLabelCount(7, true);
                break;

            case "1개월":
                ArrayList<String> monthLabels = new ArrayList<>();
                SimpleDateFormat labelSdf = new SimpleDateFormat("MM/dd", Locale.KOREA);
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, -29);

                for (int i = 0; i < 30; i++) {
                    String fullDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(cal.getTime());
                    periodDates.add(fullDateKey);
                    monthLabels.add(labelSdf.format(cal.getTime()));
                    if (diaryMoodMap.containsKey(fullDateKey)) {
                        float score = diaryMoodMap.get(fullDateKey);
                        entries.add(new Entry((float) i, score));
                        totalScoreSum += score;
                        scorePointCount++;

                        String diaryText = diaryTextMap.get(fullDateKey);
                        if (diaryText == null || diaryText.trim().isEmpty()) diaryText = "없음";
                        diaryBundle.append(String.format("- %s (기분: %s): %s\n", fullDateKey, getMoodTextForScore(score), diaryText));
                    }
                    cal.add(Calendar.DATE, 1);
                }
                totalLogCountInPeriod = scorePointCount;
                xAxis.setValueFormatter(new IndexAxisValueFormatter(monthLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(29f);
                xAxis.setLabelCount(5, false);
                break;

            case "1년":
                String[] yearLabels = {"1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"};
                HashMap<Integer, ArrayList<Float>> monthMoodMap = new HashMap<>();
                int currentYear = Calendar.getInstance().get(Calendar.YEAR);

                List<String> sortedYearKeys = new ArrayList<>(diaryMoodMap.keySet());
                Collections.sort(sortedYearKeys);

                for (String key : sortedYearKeys) {
                    try {
                        Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(key);
                        Calendar dateCal = Calendar.getInstance();
                        dateCal.setTime(date);
                        if (dateCal.get(Calendar.YEAR) == currentYear) {
                            float val = diaryMoodMap.get(key);
                            int m = dateCal.get(Calendar.MONTH);
                            if (!monthMoodMap.containsKey(m)) monthMoodMap.put(m, new ArrayList<>());
                            monthMoodMap.get(m).add(val);
                            periodDates.add(key);

                            String diaryText = diaryTextMap.get(key);
                            if (diaryText == null || diaryText.trim().isEmpty()) diaryText = "없음";
                            diaryBundle.append(String.format("- %s (기분: %s): %s\n", key, getMoodTextForScore(val), diaryText));
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }

                for (int i = 0; i < 12; i++) {
                    ArrayList<Float> moods = monthMoodMap.get(i);
                    if (moods != null && !moods.isEmpty()) {
                        float sum = 0f;
                        for (Float val : moods) {
                            sum += val;
                            totalScoreSum += val;
                            totalLogCountInPeriod++;
                        }
                        float avg = sum / moods.size();
                        entries.add(new Entry((float) i, avg));
                        scorePointCount++;
                    }
                }
                xAxis.setValueFormatter(new IndexAxisValueFormatter(yearLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(11f);
                xAxis.setLabelCount(12, true);
                break;
        }

        updateAnalysisResult(period, totalScoreSum, totalLogCountInPeriod, diaryBundle.toString());
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
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);
        dataSet.setDrawFilled(true);
        android.graphics.drawable.Drawable drawable = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#4CAF50"), Color.parseColor("#00FFFFFF")}
        );
        dataSet.setFillDrawable(drawable);

        lineChart.setData(new LineData(dataSet));
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
                    Calendar expected = (Calendar) prevCal.clone();
                    expected.add(Calendar.DATE, 1);
                    if (currentCal.equals(expected)) {
                        currentStreak++;
                    } else {
                        maxStreak = Math.max(maxStreak, currentStreak);
                        currentStreak = 1;
                    }
                }
                prevCal = currentCal;
            } catch (Exception e) { e.printStackTrace(); }
        }
        maxStreak = Math.max(maxStreak, currentStreak);

        if (tvStreakCount != null) tvStreakCount.setText(maxStreak + "일");
    }

    private void updateAnalysisResult(String period, float totalScore, int count, String diaryBundle) {
        if (count == 0) {
            tvStreakMessage.setText(period + " 동안 등록된 데이터가 없습니다. 🌱");
            return;
        }

        float avgScore = totalScore / count;
        String emoji = getEmojiForScore(avgScore);
        String moodText = getMoodTextForScore(avgScore);
        String periodLabel = period.equals("1주") ? "주간" : period.equals("1개월") ? "월간" : "연간";

        if (aiCacheMap.containsKey(periodLabel)) {
            StringBuilder sb = new StringBuilder();
            sb.append(periodLabel).append(" 평균 감정: ").append(emoji).append(" (").append(moodText).append(")\n");
            sb.append("기록 횟수: ").append(count).append("회\n\n");
            sb.append(aiCacheMap.get(periodLabel));
            tvStreakMessage.setText(sb.toString());
            return;
        }

        tvStreakMessage.setText(periodLabel + " 평균 감정: " + emoji + " (" + moodText + ")\n기록 횟수: " + count + "회\n\n🔮 AI 종합 분석 리포트 생성 중...");
        askGeminiForStatsReport(periodLabel, avgScore, count, emoji, moodText, diaryBundle);
    }

    private void askGeminiForStatsReport(String periodLabel, float avgScore, int count, String emoji, String moodText, String diaryBundle) {
        if (model == null) return;

        String aiRole = "너는 데이터 통계와 깊은 심리 인지 상담에 능통한 전문 라이프 코치이자 심리 리포트 분석가야.";

        String customPrompt = String.format(
                "%s\n\n" +
                        "[출력 필수 규정]\n" +
                        "1. 유저를 지칭할 때는 절대로 '귀하' 또는 '귀하의' 같은 딱딱하고 이질적인 단어를 쓰지 마. 무조건 '사용자님' 또는 '사용자님의'라고 자연스럽고 친근하게 표현해줘.\n" +
                        "2. 텍스트 내에 마크다운 문법 기호인 별표 2개(**)를 절대 사용하지 마. 특정 단어를 강조하기 위해 '**기쁨 감정**' 같은 형식으로 출력하는 버릇을 버리고, 별표 기호를 아예 넣지 않은 순수한 텍스트(Plain Text)로만 출력해줘.\n\n" +
                        "[기간 종합 통계 데이터]\n" +
                        "- 분석 단위: %s 정산 보고서\n" +
                        "- 총 기록 일수: %d회\n" +
                        "- 감정 평균 점수: %.2f점 (5점 만점)\n" +
                        "- 대표적 심리 상태: %s %s\n\n" +
                        "[해당 기간 유저의 실제 일기 원문 기록 모음]\n" +
                        "%s\n\n" +
                        "너는 단순히 숫자 평균만 보고 기분을 추측하는 뻔한 답변을 절대 하지마. " +
                        "제공된 [실제 일기 원문 기록 모음]의 텍스트 맥락을 날카롭고 면밀하게 독해해서, 유저가 이 기간 동안 어떤 구체적인 일상적 사건, 인간관계(예: 친구, 가족, 연인 등), 학업 스트레스, 혹은 특정 성과 때문에 심리적 요동을 겪었는지 핵심 원인 패턴과 심리 트렌드를 짚어내줘. " +
                        "유저가 자신의 일상을 이성적으로 돌아보고 따뜻한 위로와 성장의 원동력을 얻을 수 있도록 사려 깊고 전문적인 통찰 보고서를 300자 내외로 명확하게 작성해줘.",
                aiRole, periodLabel, count, avgScore, emoji, moodText,
                diaryBundle.trim().isEmpty() ? "해당 기간에 작성된 일기 본문 글이 없습니다." : diaryBundle
        );

        Content prompt = new Content.Builder().addText(customPrompt).build();
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                if (!isAdded()) return;
                String text = result.getText();
                aiCacheMap.put(periodLabel, text);
                updateAnalysisResultDisplay(periodLabel, emoji, moodText, count, text);
            }
            @Override
            public void onFailure(Throwable t) {
                if (!isAdded()) return;
                tvStreakMessage.append("\n[AI 종합 정산 실패]");
            }
        }, executor);
    }

    private void updateAnalysisResultDisplay(String periodLabel, String emoji, String moodText, int count, String aiText) {
        StringBuilder sb = new StringBuilder();
        sb.append(periodLabel).append(" 평균 감정: ").append(emoji).append(" (").append(moodText).append(")\n");
        sb.append("기록 횟수: ").append(count).append("회\n\n");
        sb.append(aiText);
        tvStreakMessage.setText(sb.toString());
    }

    private String getEmojiForScore(float score) {
        if (score >= 4.5f) return "😆";
        if (score >= 3.5f) return "😊";
        if (score >= 2.5f) return "😐";
        if (score >= 1.5f) return "😔";
        return "😠";
    }

    private String getMoodTextForScore(float score) {
        if (score >= 4.5f) return "매우 좋아요";
        if (score >= 3.5f) return "좋아요";
        if (score >= 2.5f) return "보통이에요";
        if (score >= 1.5f) return "안 좋아요";
        return "매우 안 좋아요";
    }

    private void loadDiaryDataFromServer() {
        if (currentUid == null) { loadGuestDiaryData(); return; }
        db.collection("users").document(currentUid).collection("daily_records").get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                HashSet<String> newDates = new HashSet<>();
                HashMap<String, Float> newMoods = new HashMap<>();
                HashMap<String, String> newTexts = new HashMap<>();

                for (QueryDocumentSnapshot doc : task.getResult()) {
                    String dateStr = doc.getId();
                    Long scoreObj = doc.getLong("emotionScore");
                    String emoStr = doc.getString("emotion");
                    String diaryStr = doc.getString("diary");

                    float score;
                    if (scoreObj != null && scoreObj > 0) {
                        score = scoreObj.floatValue();
                    } else {
                        score = emotionToMoodScore(emoStr);
                    }
                    if (dateStr != null) {
                        newDates.add(dateStr);
                        newMoods.put(dateStr, score);
                        newTexts.put(dateStr, diaryStr != null ? diaryStr : "");
                    }
                }

                if (!newDates.equals(lastDiaryDatesSet) || !newMoods.equals(lastDiaryMoodMap) || !newTexts.equals(lastDiaryTextMap)) {
                    aiCacheMap.clear();
                    lastDiaryDatesSet = newDates;
                    lastDiaryMoodMap = newMoods;
                    lastDiaryTextMap = newTexts;
                }

                diaryDatesSet = new HashSet<>(lastDiaryDatesSet);
                diaryMoodMap = new HashMap<>(lastDiaryMoodMap);
                diaryTextMap = new HashMap<>(lastDiaryTextMap);

                if (tvTotalCount != null) tvTotalCount.setText(diaryDatesSet.size() + "회");
                updateChartByPeriod("1주");
            }
        });
    }

    private void loadGuestDiaryData() {
        HashSet<String> newDates = new HashSet<>();
        HashMap<String, Float> newMoods = new HashMap<>();
        HashMap<String, String> newTexts = new HashMap<>();

        String userSuffix = (currentUid != null) ? currentUid : "guest";
        SharedPreferences pref = requireContext().getSharedPreferences("DailyRecords_" + userSuffix, Context.MODE_PRIVATE);
        String allRecords = pref.getString("all_records", "");

        if (!allRecords.isEmpty()) {
            for (String record : allRecords.split("##")) {
                if (record.isEmpty()) continue;
                String[] detail = record.split("\\|");
                if (detail.length >= 2) {
                    String dateKey = detail[0].split(" ")[0];
                    float score = (detail.length >= 13) ? Float.parseFloat(detail[12]) : emotionToMoodScore(detail[1]);
                    if (score < 1f || score > 5f) score = emotionToMoodScore(detail[1]);

                    String diaryStr = (detail.length >= 4) ? detail[3] : "";

                    newDates.add(dateKey);
                    newMoods.put(dateKey, score);
                    newTexts.put(dateKey, diaryStr);
                }
            }
        }

        if (!newDates.equals(lastDiaryDatesSet) || !newMoods.equals(lastDiaryMoodMap) || !newTexts.equals(lastDiaryTextMap)) {
            aiCacheMap.clear();
            lastDiaryDatesSet = newDates;
            lastDiaryMoodMap = newMoods;
            lastDiaryTextMap = newTexts;
        }

        diaryDatesSet = new HashSet<>(lastDiaryDatesSet);
        diaryMoodMap = new HashMap<>(lastDiaryMoodMap);
        diaryTextMap = new HashMap<>(lastDiaryTextMap);

        if (tvTotalCount != null) tvTotalCount.setText(diaryDatesSet.size() + "회");
        updateChartByPeriod("1주");
    }

    private float emotionToMoodScore(String emotion) {
        if (emotion == null) return 3.0f;
        String val = normalizeEmotionValue(emotion);
        switch (val) {
            case "매우_안좋아요": return 1.0f;
            case "안좋아요": return 2.0f;
            case "보통이에요": return 3.0f;
            case "좋아요": return 4.0f;
            case "매우_좋아요": return 5.0f;
            default: return 3.0f;
        }
    }

    private void clearTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
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
}