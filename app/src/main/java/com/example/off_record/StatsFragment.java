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

public class StatsFragment extends Fragment {

    private LineChart lineChart;
    private MaterialButtonToggleGroup toggleGroup;
    private TextView tvStreakMessage;

    private FirebaseFirestore db;
    private String currentUid;

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
        tvStreakMessage = view.findViewById(R.id.tvStreakMessage);

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } else {
            currentUid = "GUEST_USER";
        }

        // 1. 차트 기본 레이아웃 셋팅
        setupLineChart();

        // 파이어베이스 데이터 로드 및 연동
        loadDiaryDataFromServer();
        setupToggleButtons();

        return view;
    }

    /**
     * 1. 선그래프의 좌우 치우침을 완벽하게 잡아주는 여백 강제 정렬 정의
     */
    private void setupLineChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getAxisRight().setEnabled(false); // 오른쪽 Y축 차단

        // 🛠️ [치우침 버그 완벽 해결 치트키]
        // 왼쪽 이모지가 그려질 공간(100f)과 우측 공간(100f)의 내부 오프셋 밸런스를 강제로 똑같이 맞춥니다!
        // 이 코드가 들어가면 그래프 선 본체가 오른쪽으로 절대 치우치지 않고 정중앙에 배치됩니다. ⭐
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

            // 🛠️ setViewPortOffsets(100f, ...) 기준선에 맞춰 이모지가 딱 정렬되도록 중심 좌푯값 세밀 조정
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
     * 3. 기간 선택 토글 버튼 이벤트 관리 (1일 제거)
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
     * 4. 주/월/년 선택 시 X축 글자 포맷팅 및 데이터 정렬 연동 (1일 완전 삭제)
     */
    private void updateChartByPeriod(String period) {
        List<Entry> entries = new ArrayList<>();
        XAxis xAxis = lineChart.getXAxis();
        String label = "내 기분 추이 흐름";

        switch (period) {
            case "1주":
                // X축에 "월", "화", "수", "목", "금", "토", "일" 매핑
                String[] weekLabels = {"월", "화", "수", "목", "금", "토", "일"};

                entries.add(new Entry(0f, getMoodOrFake(6, 4f)));
                entries.add(new Entry(1f, getMoodOrFake(5, 2f)));
                entries.add(new Entry(2f, getMoodOrFake(4, 3f)));
                entries.add(new Entry(3f, getMoodOrFake(3, 5f)));
                entries.add(new Entry(4f, getMoodOrFake(2, 2f)));
                entries.add(new Entry(5f, getMoodOrFake(1, 4f)));
                entries.add(new Entry(6f, getMoodOrFake(0, 5f)));

                xAxis.setValueFormatter(new IndexAxisValueFormatter(weekLabels));
                xAxis.setLabelCount(7, true);
                label = "이번 주 감정 추이";
                break;

            case "1개월":
                // 오늘 날짜 기준으로 한 달 전(30일 전)부터 오늘까지 날짜 생성
                ArrayList<String> monthLabels = new ArrayList<>();
                SimpleDateFormat labelSdf = new SimpleDateFormat("MM/dd", Locale.KOREA);
                Calendar cal = Calendar.getInstance();

                cal.add(Calendar.DATE, -29);
                for (int i = 0; i < 30; i++) {
                    monthLabels.add(labelSdf.format(cal.getTime()));

                    String fullDateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(cal.getTime());
                    float moodValue = diaryMoodMap.containsKey(fullDateKey) ? diaryMoodMap.get(fullDateKey) : (float)(2.0 + Math.random() * 3.0);
                    entries.add(new Entry((float) i, moodValue));

                    cal.add(Calendar.DATE, 1);
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(monthLabels));
                xAxis.setLabelCount(5, false); // 글자 중첩 방지용 5개 거점 세팅
                label = "최근 1개월 감정 흐름";
                break;

            case "1년":
                // X축에 "1월" ~ "12월" 순서대로 박히도록 수정
                String[] yearLabels = {"1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"};

                for (int i = 0; i < 12; i++) {
                    float monthAverage = (float) (2.5 + Math.random() * 2.5);
                    entries.add(new Entry((float) i, monthAverage));
                }

                xAxis.setValueFormatter(new IndexAxisValueFormatter(yearLabels));
                xAxis.setLabelCount(12, true);
                label = "올해 월별 감정 흐름";
                break;
        }

        if (entries.isEmpty()) {
            lineChart.clear();
            lineChart.setNoDataText("선택한 기간에 기록된 기분 데이터가 없습니다.");
            return;
        }

        // 선그래프 디자인 속성 정의
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(Color.parseColor("#2E7D32"));
        dataSet.setCircleColor(Color.parseColor("#4CAF50"));
        dataSet.setLineWidth(3f);
        dataSet.setCircleRadius(4.5f);
        dataSet.setDrawCircleHole(true);
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);      // 부드러운 곡선 모드

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.invalidate(); // 차트 리프레시 갱신
    }

    private float getMoodOrFake(int daysAgo, float fakeValue) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -daysAgo);
        String targetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(cal.getTime());

        if (diaryMoodMap.containsKey(targetDate)) {
            return diaryMoodMap.get(targetDate);
        }
        return fakeValue;
    }

    /**
     * 5. 파이어베이스 데이터 조회
     */
    private void loadDiaryDataFromServer() {
        if (currentUid == null) return;

        db.collection("diaries")
                .whereEqualTo("uid", currentUid)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        diaryDatesSet.clear();
                        diaryMoodMap.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String dateStr = document.getString("date");
                            Long moodScoreLong = document.getLong("moodScore");
                            float moodScore = (moodScoreLong != null) ? moodScoreLong.floatValue() : 3.0f;

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