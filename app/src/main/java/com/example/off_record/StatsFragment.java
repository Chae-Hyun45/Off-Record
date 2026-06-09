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
    private TextView tvStreakMessage;

    private FirebaseFirestore db;
    private String currentUid;
    private boolean isGuestMode = false;

    private GenerativeModelFutures model;

    // 🌟 [구조 변경] 메인 탭 이동(Fragment 재생성) 시에도 답변이 유지되도록 static(정적 메모리) 보관함으로 승격
    private static HashMap<String, String> aiCacheMap = new HashMap<>();

    // 🌟 [구조 추가] 실제 데이터베이스의 값이 바뀌었는지 실시간 싱크 체크를 위한 데이터 정산 기록용 정적 장부
    private static HashSet<String> lastDiaryDatesSet = new HashSet<>();
    private static HashMap<String, Float> lastDiaryMoodMap = new HashMap<>();

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

    private void showEmptyGuestStats() {
        diaryDatesSet.clear();
        diaryMoodMap.clear();

        if (toggleGroup != null) {
            toggleGroup.setVisibility(View.VISIBLE);
        }
        if (lineChart != null) {
            lineChart.setVisibility(View.VISIBLE);
        }
        if (tvStreakTitle != null) {
            tvStreakTitle.setVisibility(View.VISIBLE);
        }

        calculateDiaryStreak();
        updateChartByPeriod("1주");
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
        int actualLogCount = 0;

        switch (period) {
            case "1주":
                String[] weekLabels = {"월", "화", "수", "목", "금", "토", "일"};
                Calendar weekCal = Calendar.getInstance();
                int todayDow = weekCal.get(Calendar.DAY_OF_WEEK);
                int diffToMonday = (todayDow == Calendar.SUNDAY) ? 6 : todayDow - 2;
                weekCal.add(Calendar.DATE, -diffToMonday);

                for (int i = 0; i < 7; i++) {
                    String fullDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(weekCal.getTime());
                    if (diaryMoodMap.containsKey(fullDateKey)) {
                        float score = diaryMoodMap.get(fullDateKey);
                        entries.add(new Entry((float) i, score));
                        totalScore += score;
                        scoreCount++;
                    }
                    weekCal.add(Calendar.DATE, 1);
                }
                actualLogCount = scoreCount;

                xAxis.setValueFormatter(new IndexAxisValueFormatter(weekLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(6f);
                xAxis.setLabelCount(7, true);
                label = isGuestMode ? "오늘 게스트 감정 기록" : "이번 주 감정 추이";
                break;

            case "1개월":
                ArrayList<String> monthLabels = new ArrayList<>();
                SimpleDateFormat labelSdf = new SimpleDateFormat("MM/dd", Locale.KOREA);
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, -29);

                for (int i = 0; i < 30; i++) {
                    monthLabels.add(labelSdf.format(cal.getTime()));
                    String fullDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(cal.getTime());
                    if (diaryMoodMap.containsKey(fullDateKey)) {
                        float score = diaryMoodMap.get(fullDateKey);
                        entries.add(new Entry((float) i, score));
                        totalScore += score;
                        scoreCount++;
                    }
                    cal.add(Calendar.DATE, 1);
                }
                actualLogCount = scoreCount;

                xAxis.setValueFormatter(new IndexAxisValueFormatter(monthLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(29f);
                xAxis.setLabelCount(5, false);
                label = isGuestMode ? "오늘 게스트 감정 기록" : "최근 1개월 감정 흐름";
                break;

            case "1년":
                String[] yearLabels = {"1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"};
                HashMap<Integer, ArrayList<Float>> monthMoodMap = new HashMap<>();

                for (Map.Entry<String, Float> entry : diaryMoodMap.entrySet()) {
                    try {
                        Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(entry.getKey());
                        Calendar dateCal = Calendar.getInstance();
                        dateCal.setTime(date);
                        int year = dateCal.get(Calendar.YEAR);
                        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                        if (year == currentYear) {
                            int monthIndex = dateCal.get(Calendar.MONTH);
                            if (!monthMoodMap.containsKey(monthIndex)) {
                                monthMoodMap.put(monthIndex, new ArrayList<>());
                            }
                            monthMoodMap.get(monthIndex).add(entry.getValue());
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }

                float yearlyTotalScoreSum = 0f;
                for (int i = 0; i < 12; i++) {
                    ArrayList<Float> moods = monthMoodMap.get(i);
                    if (moods != null && !moods.isEmpty()) {
                        float sum = 0f;
                        for (Float mood : moods) {
                            sum += mood;
                            yearlyTotalScoreSum += mood;
                        }
                        float avg = sum / moods.size();
                        entries.add(new Entry((float) i, avg));
                        actualLogCount += moods.size();
                        scoreCount++;
                    }
                }

                if (actualLogCount > 0) {
                    totalScore = yearlyTotalScoreSum;
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(yearLabels));
                xAxis.setAxisMinimum(0f);
                xAxis.setAxisMaximum(11f);
                xAxis.setLabelCount(12, true);
                label = isGuestMode ? "오늘 게스트 감정 기록" : "올해 월별 감정 흐름";
                break;
        }

        updateAnalysisResult(period, totalScore, actualLogCount);

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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            android.graphics.drawable.Drawable drawable =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{Color.parseColor("#4CAF50"), Color.parseColor("#00FFFFFF")}
                    );
            dataSet.setFillDrawable(drawable);
        } else {
            dataSet.setFillColor(Color.parseColor("#4CAF50"));
        }

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.invalidate();
    }

    private void updateAnalysisResult(String period, float totalScore, int count) {
        if (count == 0) {
            tvStreakMessage.setText(period + " 동안 등록된 마음 데이터가 없습니다.\n오늘의 감정을 일기에 기록해 보세요! 🌱");
            return;
        }

        float avgScore = totalScore / count;
        String emoji = getEmojiForScore(avgScore);
        String moodText = getMoodTextForScore(avgScore);

        String periodLabel = "선택 기간";
        if ("1주".equals(period)) periodLabel = "주간";
        else if ("1개월".equals(period)) periodLabel = "월간";
        else if ("1년".equals(period)) periodLabel = "연간";

        // 🌟 캐시 보관함 조회 (정적 변수로 승격되어 다른 큰 탭 이동 후 컴백해도 데이터가 완벽히 보존됨)
        if (aiCacheMap.containsKey(periodLabel)) {
            StringBuilder sb = new StringBuilder();
            sb.append(periodLabel).append(" 평균 감정: ").append(emoji).append(" (").append(moodText).append(")\n");
            sb.append("기록 횟수: ").append(count).append("회\n\n");
            sb.append(aiCacheMap.get(periodLabel));

            tvStreakMessage.setText(sb.toString());
            return;
        }

        String basicSummary = periodLabel + " 평균 감정: " + emoji + " (" + moodText + ")\n" +
                "기록 횟수: " + count + "회\n\n" +
                "🔮 AI가 사용자님의 " + periodLabel + " 흐름을 심층 정산하는 중입니다...";
        tvStreakMessage.setText(basicSummary);

        askGeminiForStatsReport(periodLabel, avgScore, count, emoji, moodText);
    }

    private void askGeminiForStatsReport(String periodLabel, float avgScore, int count, String emoji, String moodText) {
        if (model == null) {
            tvStreakMessage.setText(periodLabel + " 평균 감정: " + emoji + " (" + moodText + ")\n기록 횟수: " + count + "회\n\n[AI 연결 실패] 네트워크 상태를 확인해 주세요.");
            return;
        }

        String aiRole = "너는 사용자의 장기 기분 트렌드를 예리하게 포착해 내는 '데이터 기반 전문 심리 분석가'이자 다정한 멘토야.";

        // 🌟 [수정 완료] 기존의 '정원님' 호칭 구역을 '사용자님'으로 완벽하게 한글화 교체!
        String customPrompt = String.format(
                "%s\n\n" +
                        "사용자가 기록한 %s 마음 통계 데이터 결과야. 이 수치들을 유심히 분석해서 유저의 마음 흐름을 보듬어주는 종합 심리 리포트를 200자 내외로 자연스럽게 작성해줘.\n\n" +
                        "[전달할 통계 리포트 정보]\n" +
                        "- 분석 단위: %s 리포트\n" +
                        "- 기간 총 기록 횟수: %d회\n" +
                        "- 마음 평균 점수: %.2f점 (5점 만점 기준)\n" +
                        "- 기간 종합 대표 기분: %s (%s)\n\n" +
                        "위 요약 수치에 기반하여 최근 어떤 심리적 패턴을 보이고 있는지 공감해 주고, 사용자님이 앞으로 더 활기차고 평온하게 지낼 수 있도록 맞춤형 피드백을 다정하게 해줘.",
                aiRole, periodLabel, periodLabel, count, avgScore, emoji, moodText
        );

        Content prompt = new Content.Builder().addText(customPrompt).build();
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                if (!isAdded() || getContext() == null) return;

                String aiAnalysisText = result.getText();
                aiCacheMap.put(periodLabel, aiAnalysisText);

                StringBuilder sb = new StringBuilder();
                sb.append(periodLabel).append(" 평균 감정: ").append(emoji).append(" (").append(moodText).append(")\n");
                sb.append("기록 횟수: ").append(count).append("회\n\n");
                sb.append(aiAnalysisText);

                tvStreakMessage.setText(sb.toString());
            }

            @Override
            public void onFailure(Throwable t) {
                if (!isAdded() || getContext() == null) return;
                Log.e("GeminiStats", "통계 제미나이 호출 중 에러 발생", t);
                tvStreakMessage.setText(periodLabel + " 평균 감정: " + emoji + " (" + moodText + ")\n기록 횟수: " + count + "회\n\n[제미나이 엔진 응답 지연으로 통계 분석을 불러오지 못했습니다.]");
            }
        }, executor);
    }

    private String getEmojiForScore(float score) {
        if (score >= 4.5f) return "🥰";
        if (score >= 3.5f) return "😊";
        if (score >= 2.5f) return "😐";
        if (score >= 1.5f) return "😔";
        return "😭";
    }

    private String getMoodTextForScore(float score) {
        if (score >= 4.5f) return "매우 좋아요";
        if (score >= 3.5f) return "좋아요";
        if (score >= 2.5f) return "보통이에요";
        if (score >= 1.5f) return "안 좋아요";
        return "매우 안 좋아요";
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
                        HashSet<String> newDatesSet = new HashSet<>();
                        HashMap<String, Float> newMoodMap = new HashMap<>();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String dateStr = document.getId();
                            String emotion = document.getString("emotion");
                            float moodScore = emotionToMoodScore(emotion);

                            if (dateStr != null) {
                                newDatesSet.add(dateStr);
                                newMoodMap.put(dateStr, moodScore);
                            }
                        }

                        // 🌟 [변동 감지 알고리즘 적용]
                        // 서버에서 불러온 진짜 일기 원본 데이터가 '과거 정산 내역'과 단 하나라도 다를 때만 캐시를 폐기합니다!
                        if (!newDatesSet.equals(lastDiaryDatesSet) || !newMoodMap.equals(lastDiaryMoodMap)) {
                            aiCacheMap.clear(); // 데이터가 진짜 바뀌었으니 캐시 청소
                            lastDiaryDatesSet = newDatesSet;
                            lastDiaryMoodMap = newMoodMap;
                        }

                        // 복원 완료된 전역 버퍼 데이터를 인스턴스로 복사
                        diaryDatesSet = new HashSet<>(lastDiaryDatesSet);
                        diaryMoodMap = new HashMap<>(lastDiaryMoodMap);

                        if (tvTotalCount != null) {
                            tvTotalCount.setText(diaryDatesSet.size() + "회");
                        }
                        calculateDiaryStreak();
                        updateChartByPeriod("1주");
                    } else {
                        Toast.makeText(getContext(), "데이터 로드 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadGuestDiaryData() {
        HashSet<String> newDatesSet = new HashSet<>();
        HashMap<String, Float> newMoodMap = new HashMap<>();

        GuestRecordStore.clearIfNotToday(requireContext());
        Map<String, Object> guestRecord = GuestRecordStore.getTodayRecord(requireContext());
        if (guestRecord != null) {
            String dateStr = String.valueOf(guestRecord.get("date"));
            String emotion = String.valueOf(guestRecord.get("emotion"));
            newDatesSet.add(dateStr);
            newMoodMap.put(dateStr, emotionToMoodScore(emotion));
        }

        // 🌟 게스트 모드도 정밀 변동 체크 결합
        if (!newDatesSet.equals(lastDiaryDatesSet) || !newMoodMap.equals(lastDiaryMoodMap)) {
            aiCacheMap.clear();
            lastDiaryDatesSet = newDatesSet;
            lastDiaryMoodMap = newMoodMap;
        }

        diaryDatesSet = new HashSet<>(lastDiaryDatesSet);
        diaryMoodMap = new HashMap<>(lastDiaryMoodMap);

        calculateDiaryStreak();
        updateChartByPeriod("1주");
    }

    private float emotionToMoodScore(String emotion) {
        if (emotion == null) return 3.0f;
        switch (emotion) {
            case "매우_안좋아요": return 1.0f;
            case "안좋아요": return 2.0f;
            case "보통이에요": return 3.0f;
            case "좋아요": return 4.0f;
            case "매우_좋아요": return 5.0f;
            default: return 3.0f;
        }
    }

    private void calculateDiaryStreak() {
        if (diaryDatesSet.isEmpty()) {
            tvStreakMessage.setText("아직 작성된 일기가 없습니다. 첫 일기를 쓰고 기분을 기록해 보세요! ✍️");
            return;
        }

        List<Date> dateList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);

        for (String dateStr : diaryDatesSet) {
            try { dateList.add(sdf.parse(dateStr)); } catch (ParseException e) { e.printStackTrace(); }
        }
        Collections.sort(dateList);

        Calendar cal = Calendar.getInstance();
        clearTime(cal);
        Date today = cal.getTime();
        cal.add(Calendar.DATE, -1);
        Date yesterday = cal.getTime();

        Date latestDiaryDate = dateList.get(dateList.size() - 1);
        if (latestDiaryDate.before(yesterday)) {
            tvStreakMessage.setText("불타는 일기 열정! 🔥 현재 0일 연속으로 일기를 기록 중입니다.");
            return;
        }

        int streakCount = 1;
        for (int i = dateList.size() - 1; i > 0; i--) {
            Calendar currentCal = Calendar.getInstance();
            currentCal.setTime(dateList.get(i));
            Calendar prevCal = Calendar.getInstance();
            prevCal.setTime(dateList.get(i - 1));

            currentCal.add(Calendar.DATE, -1);
            if (currentCal.getTime().equals(prevCal.getTime())) {
                streakCount++;
            } else if (currentCal.getTime().after(prevCal.getTime())) {
                continue;
            } else {
                break;
            }
        }
        tvStreakMessage.setText("불타는 일기 열정! 🔥 현재 " + streakCount + "일 연속으로 일기를 기록 중입니다.");
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

    private int getEmotionScore(String emotionValue) {
        String value = normalizeEmotionValue(emotionValue);

        if ("매우_안좋아요".equals(value)) return 1;
        if ("안좋아요".equals(value)) return 2;
        if ("보통이에요".equals(value)) return 3;
        if ("좋아요".equals(value)) return 4;
        if ("매우_좋아요".equals(value)) return 5;

        return 0;
    }
}