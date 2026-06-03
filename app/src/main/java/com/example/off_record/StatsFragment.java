package com.example.off_record;

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
        tvStreakMessage = view.findViewById(R.id.tvStreakMessage);

        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = (currentUser != null) ? currentUser.getUid() : null;
        isGuestMode = (currentUid == null);

        // 1. 차트 기본 레이아웃 셋팅
        setupLineChart();
        setupToggleButtons();

        if (isGuestMode) {
            // 게스트는 DB 누적 통계는 없지만, 오늘 임시 기록이 있으면 오늘 데이터만 통계 화면에 표시합니다.
            loadGuestDiaryData();
        } else {
            // 파이어베이스 데이터 로드 및 연동
            loadDiaryDataFromServer();
        }

        return view;
    }

    /**
     * 게스트는 Firestore에 저장하지 않고 오늘 기록만 로컬에 임시 저장합니다.
     * 오늘 임시 기록이 있으면 통계 화면에 오늘 데이터 1개만 보여주고, 날짜가 바뀌면 빈 상태가 됩니다.
     */
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

    /**
     * 1. 선그래프의 좌우 치우침을 완벽하게 잡아주는 여백 강제 정렬 정의
     */
    private void setupLineChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getAxisRight().setEnabled(false); // 오른쪽 Y축 차단

        // 왼쪽 이모지가 그려질 공간(100f)과 우측 공간(100f)의 내부 오프셋 밸런스를 강제로 똑같이 맞춥니다!
        lineChart.setViewPortOffsets(100f, 60f, 100f, 80f);

        // Y축 환경 정의 (1~5점 고정)
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(1f);
        leftAxis.setAxisMaximum(5f);
        leftAxis.setLabelCount(5, true);
        leftAxis.setDrawLabels(true);
        leftAxis.setTextColor(Color.TRANSPARENT); // 수치 숫자는 투명하게 숨기기
        leftAxis.setDrawAxisLine(false);          // 축선 숨겨서 깔끔하게

        // X축(바닥 글자축) 디자인 정의
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(11f);
        xAxis.setGranularity(1f);

        // 커스텀 이모지 Y축 렌더러 등록
        EmojiYAxisRenderer emojiRenderer = new EmojiYAxisRenderer(
                lineChart.getViewPortHandler(),
                leftAxis,
                lineChart.getTransformer(YAxis.AxisDependency.LEFT)
        );
        lineChart.setRendererLeftYAxis(emojiRenderer);

        lineChart.setTouchEnabled(true);
        lineChart.animateX(800);
    }

    /**
     * 2. Y축 정수 높이선선 좌측에 맞춰 one~five 이모지 비트맵 이미지를 그리는 커스텀 렌더러
     */
    private class EmojiYAxisRenderer extends YAxisRenderer {
        private Bitmap[] emojiBitmaps = new Bitmap[5];
        private int emojiSize = 65; // 이모지 이미지 크기

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

    /**
     * 3. 기간 선택 토글 버튼 이벤트 관리
     */
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

    /**
     * 4. 주/월/년 선택 시 X축 글자 포맷팅 및 데이터 정렬 연동
     */
    private void updateChartByPeriod(String period) {
        List<Entry> entries = new ArrayList<>();
        XAxis xAxis = lineChart.getXAxis();
        String label = isGuestMode ? "오늘 게스트 감정 기록" : "내 기분 추이 흐름";

        switch (period) {
            case "1주":
                String[] weekLabels = {"월", "화", "수", "목", "금", "토", "일"};
                Calendar weekCal = Calendar.getInstance();
                int todayDow = weekCal.get(Calendar.DAY_OF_WEEK);
                int diffToMonday = (todayDow + 5) % 7;
                weekCal.add(Calendar.DATE, -diffToMonday);

                for (int i = 0; i < 7; i++) {
                    String fullDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(weekCal.getTime());
                    if (diaryMoodMap.containsKey(fullDateKey)) {
                        entries.add(new Entry((float) i, diaryMoodMap.get(fullDateKey)));
                    }
                    weekCal.add(Calendar.DATE, 1);
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(weekLabels));
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
                        entries.add(new Entry((float) i, diaryMoodMap.get(fullDateKey)));
                    }
                    cal.add(Calendar.DATE, 1);
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(monthLabels));
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

                for (int i = 0; i < 12; i++) {
                    ArrayList<Float> moods = monthMoodMap.get(i);
                    if (moods != null && !moods.isEmpty()) {
                        float sum = 0f;
                        for (Float mood : moods) sum += mood;
                        entries.add(new Entry((float) i, sum / moods.size()));
                    }
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(yearLabels));
                xAxis.setLabelCount(12, true);
                label = isGuestMode ? "오늘 게스트 감정 기록" : "올해 월별 감정 흐름";
                break;
        }

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
        // 실제 기록만 선으로 연결합니다. CUBIC_BEZIER는 같은 점수 사이에서도 곡선이 과하게 꺾여 보일 수 있어 LINEAR로 고정합니다.
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.invalidate();
    }

    /**
     * 5. 파이어베이스 데이터 조회 (💡 5단계 독립 서랍 격리 및 데이터 규격 매핑 적용)
     */
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
                            // 💡 새 구조에서는 문서 고유의 ID 자체가 날짜 Key값(yyyy-MM-dd)입니다!
                            String dateStr = document.getId();

                            // 💡 문자열 코드형 감정 데이터("emo1"~"emo5")를 그래프 소수점 점수(1.0f~5.0f)로 매핑합니다.
                            String emotion = document.getString("emotion");
                            float moodScore = emotionToMoodScore(emotion);

                            if (dateStr != null) {
                                diaryDatesSet.add(dateStr);
                                diaryMoodMap.put(dateStr, moodScore);
                            }
                        }
                        calculateDiaryStreak();
                        updateChartByPeriod("1주"); // 기본 시작 탭 주간으로 설정
                    } else {
                        Toast.makeText(getContext(), "데이터 로드 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadGuestDiaryData() {
        diaryDatesSet.clear();
        diaryMoodMap.clear();

        GuestRecordStore.clearIfNotToday(requireContext());
        Map<String, Object> guestRecord = GuestRecordStore.getTodayRecord(requireContext());
        if (guestRecord != null) {
            String dateStr = String.valueOf(guestRecord.get("date"));
            String emotion = String.valueOf(guestRecord.get("emotion"));
            diaryDatesSet.add(dateStr);
            diaryMoodMap.put(dateStr, emotionToMoodScore(emotion));
        }

        calculateDiaryStreak();
        updateChartByPeriod("1주");
    }

    private float emotionToMoodScore(String emotion) {
        if (emotion == null) return 3.0f;
        switch (emotion) {
            case "emo1": return 1.0f;
            case "emo2": return 2.0f;
            case "emo3": return 3.0f;
            case "emo4": return 4.0f;
            case "emo5": return 5.0f;
            default: return 3.0f;
        }
    }

    /**
     * 6. 연속 일기 작성 스트릭(Streak) 추적 로직
     */
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
}