package com.example.off_record;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

    private int colorPrimaryYellow;
    private int colorTextPrimary;
    private int colorTextSecondary;

    public StatsFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            colorPrimaryYellow = ContextCompat.getColor(getContext(), R.color.butter_yellow_main);
            colorTextPrimary = ContextCompat.getColor(getContext(), R.color.text_premium_main);
            colorTextSecondary = ContextCompat.getColor(getContext(), R.color.text_premium_sub);
        }
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
            currentUid = "guest_user";
        }

        setupLineChart();
        loadDiaryDataFromServer();
        setupToggleButtons();

        return view;
    }

    private void setupLineChart() {
        if (lineChart == null) return;
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setEnabled(false); // 범례 숨김 (더 깔끔하게)
        
        // 뷰포트 여백 조정 (이모지 영역 확보)
        lineChart.setViewPortOffsets(120f, 60f, 60f, 80f);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setAxisMinimum(0.5f);
        leftAxis.setAxisMaximum(5.5f);
        leftAxis.setLabelCount(5, true);
        leftAxis.setDrawLabels(true);
        leftAxis.setTextColor(Color.TRANSPARENT);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#EEEEEE")); // 부드러운 그리드 색상

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false); // X축 그리드는 제거해서 더 모던하게
        xAxis.setTextSize(11f);
        xAxis.setTextColor(colorTextSecondary);
        xAxis.setGranularity(1f);
        xAxis.setYOffset(10f);

        EmojiYAxisRenderer emojiRenderer = new EmojiYAxisRenderer(
                lineChart.getViewPortHandler(),
                leftAxis,
                lineChart.getTransformer(YAxis.AxisDependency.LEFT)
        );
        lineChart.setRendererLeftYAxis(emojiRenderer);

        lineChart.setTouchEnabled(true);
        lineChart.animateY(1000); // Y축 애니메이션으로 부드럽게 등장
    }

    private class EmojiYAxisRenderer extends YAxisRenderer {
        private Bitmap[] emojiBitmaps = new Bitmap[5];
        private int emojiSize = 48;

        public EmojiYAxisRenderer(ViewPortHandler viewPortHandler, YAxis yAxis, Transformer transformer) {
            super(viewPortHandler, yAxis, transformer);
            if (getContext() != null) {
                int[] resIds = {R.drawable.one, R.drawable.two, R.drawable.three, R.drawable.four, R.drawable.five};
                for (int i = 0; i < 5; i++) {
                    Bitmap bmp = BitmapFactory.decodeResource(getContext().getResources(), resIds[i]);
                    if (bmp != null) {
                        emojiBitmaps[i] = Bitmap.createScaledBitmap(bmp, emojiSize, emojiSize, true);
                    }
                }
            }
        }

        @Override
        protected void drawYLabels(Canvas c, float fixedPosition, float[] positions, float offset) {
            float safeXPosition = mViewPortHandler.contentLeft() - emojiSize - 20f;
            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.BLACK);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(2f);
            borderPaint.setAntiAlias(true);

            for (int i = 0; i < positions.length; i += 2) {
                int emojiIndex = i / 2;
                if (emojiIndex < 5 && emojiBitmaps[emojiIndex] != null) {
                    float yPos = positions[i + 1];
                    float finalY = yPos - (emojiSize / 2f);
                    
                    // Draw emoji
                    c.drawBitmap(emojiBitmaps[emojiIndex], safeXPosition, finalY, new Paint());
                    
                    // Draw black border around emoji
                    c.drawCircle(safeXPosition + (emojiSize / 2f), finalY + (emojiSize / 2f), (emojiSize / 2f) + 2f, borderPaint);
                }
            }
        }
    }

    private void setupToggleButtons() {
        if (toggleGroup == null) return;
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnWeek) updateChartByPeriod("1주");
                else if (checkedId == R.id.btnMonth) updateChartByPeriod("1개월");
                else if (checkedId == R.id.btnYear) updateChartByPeriod("1년");
            }
        });
    }

    private void updateChartByPeriod(String period) {
        if (lineChart == null) return;
        List<Entry> entries = new ArrayList<>();
        XAxis xAxis = lineChart.getXAxis();
        String label = "감정 흐름";

        switch (period) {
            case "1주":
                String[] weekLabels = {"월", "화", "수", "목", "금", "토", "일"};
                for (int i = 0; i < 7; i++) {
                    entries.add(new Entry(i, getMoodOrFake(6 - i, (float) (2 + Math.random() * 3))));
                }
                xAxis.setValueFormatter(new IndexAxisValueFormatter(weekLabels));
                xAxis.setLabelCount(7, true);
                break;

            case "1개월":
                ArrayList<String> monthLabels = new ArrayList<>();
                SimpleDateFormat labelSdf = new SimpleDateFormat("M/d", Locale.KOREA);
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, -29);
                for (int i = 0; i < 30; i++) {
                    monthLabels.add(labelSdf.format(cal.getTime()));
                    String dateKey = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(cal.getTime());
                    entries.add(new Entry(i, diaryMoodMap.containsKey(dateKey) ? diaryMoodMap.get(dateKey) : (float) (2 + Math.random() * 3)));
                    cal.add(Calendar.DATE, 1);
                }
                xAxis.setValueFormatter(new IndexAxisValueFormatter(monthLabels));
                xAxis.setLabelCount(5, false);
                break;

            case "1년":
                String[] yearLabels = {"1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"};
                for (int i = 0; i < 12; i++) {
                    entries.add(new Entry(i, (float) (2.5 + Math.random() * 2.5)));
                }
                xAxis.setValueFormatter(new IndexAxisValueFormatter(yearLabels));
                xAxis.setLabelCount(12, true);
                break;
        }

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(colorPrimaryYellow);
        dataSet.setCircleColor(colorPrimaryYellow);
        dataSet.setLineWidth(3.5f);
        dataSet.setCircleRadius(5f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleColor(Color.WHITE);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER); // 부드러운 곡선

        // 🌟 하단 채우기 및 그라데이션 적용
        dataSet.setDrawFilled(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{colorPrimaryYellow, Color.TRANSPARENT}
            );
            gradient.setAlpha(80); // 부드러운 투명도
            dataSet.setFillDrawable(gradient);
        } else {
            dataSet.setFillColor(colorPrimaryYellow);
        }

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        
        // --- 📊 평균 점수 계산 및 메시지 업데이트 ---
        updateAverageMessage(period, entries);
        
        lineChart.invalidate();
    }

    private void updateAverageMessage(String period, List<Entry> entries) {
        if (tvStreakMessage == null || entries.isEmpty()) return;

        float sum = 0;
        int count = 0;
        for (Entry entry : entries) {
            // 기본 더미 데이터(랜덤값)를 제외한 실제 기록이 있는 경우를 우선하거나, 전체 평균 계산
            sum += entry.getY();
            count++;
        }

        float average = sum / count;
        String moodText = getMoodText(average);

        String message = String.format(Locale.KOREA, 
            "%s 동안의 평균 감정 지수는 %.1f점입니다.\n대체로 '%s' 상태였네요! ✨", 
            period, average, moodText);
        
        tvStreakMessage.setText(message);
    }

    private String getMoodText(float score) {
        if (score >= 4.5f) return "매우 행복";
        if (score >= 3.5f) return "좋음";
        if (score >= 2.5f) return "평범";
        if (score >= 1.5f) return "조금 지침";
        return "힘듦";
    }

    private float getMoodOrFake(int daysAgo, float fakeValue) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -daysAgo);
        String targetDate = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(cal.getTime());
        return diaryMoodMap.containsKey(targetDate) ? diaryMoodMap.get(targetDate) : fakeValue;
    }

    private void loadDiaryDataFromServer() {
        if (currentUid == null) return;
        db.collection("users").document(currentUid).collection("daily_records").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    diaryDatesSet.clear();
                    diaryMoodMap.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String dateStr = doc.getId();
                        String emotion = doc.getString("emotion");
                        float moodScore = 3.0f;
                        if (emotion != null) {
                            switch (emotion) {
                                case "emo1": moodScore = 1.0f; break;
                                case "emo2": moodScore = 2.0f; break;
                                case "emo3": moodScore = 3.0f; break;
                                case "emo4": moodScore = 4.0f; break;
                                case "emo5": moodScore = 5.0f; break;
                            }
                        }
                        diaryDatesSet.add(dateStr);
                        diaryMoodMap.put(dateStr, moodScore);
                    }
                    calculateDiaryStreak();
                    updateChartByPeriod("1주");
                });
    }

    private void calculateDiaryStreak() {
        if (tvStreakMessage == null) return;
        if (diaryDatesSet.isEmpty()) {
            tvStreakMessage.setText("첫 일기를 쓰고 기분을 기록해 보세요! ✍️");
            return;
        }

        List<Date> dateList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        for (String dateStr : diaryDatesSet) {
            try { dateList.add(sdf.parse(dateStr)); } catch (ParseException ignored) {}
        }
        Collections.sort(dateList);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        Date yesterday = new Date(cal.getTimeInMillis() - 86400000);

        Date latest = dateList.get(dateList.size() - 1);
        if (latest.before(yesterday)) {
            tvStreakMessage.setText("다시 시작해볼까요? 🔥 연속 기록이 0일입니다.");
            return;
        }

        int streak = 1;
        for (int i = dateList.size() - 1; i > 0; i--) {
            if (dateList.get(i).getTime() - dateList.get(i-1).getTime() <= 86400000) streak++;
            else break;
        }
        tvStreakMessage.setText("대단해요! 🔥 " + streak + "일 연속으로 기록 중입니다.");
    }
}
