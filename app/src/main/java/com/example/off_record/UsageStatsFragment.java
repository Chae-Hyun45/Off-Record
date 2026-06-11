package com.example.off_record;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageStatsFragment extends Fragment {

    private static final int COLOR_TEXT_MAIN = 0xFF141414;
    private static final int COLOR_TEXT_SUB = 0xFF767986;
    private static final int COLOR_LINE = 0xFFE9EAF0;
    private static final int COLOR_YELLOW_ACTIVE = 0xFFF5C433;
    private static final int COLOR_BAR_DEFAULT = 0xFFC8CCD7;
    private static final int COLOR_YELLOW_SOFT = 0xFFF9E8A7;
    private static final int COLOR_BAR_BG = 0xFFE7E9EE;

    private static final int COLOR_SOCIAL = 0xFF2979FF;
    private static final int COLOR_ENTERTAINMENT = 0xFF18C6C8;
    private static final int COLOR_SHOPPING = 0xFFFF8A20;
    private static final int COLOR_OTHER = 0xFF9C9EA7;

    private TextView tvWeekBadge;
    private TextView tvWeeklyTotal;
    private TextView tvWeekRange;
    private TextView tvDailyAverage;
    private TextView tvUpdatedAt;
    private TextView tvSelectedDayTitle;
    private TextView tvSocialTime;
    private TextView tvEntertainmentTime;
    private TextView tvShoppingTime;
    private TextView btnPrevWeek;
    private TextView btnNextWeek;
    private LinearLayout llWeeklyChart;
    private LinearLayout llAppList;

    private int weekOffset = 0; // 0 = current week, -1 = previous week
    private int selectedDayIndex = -1; // 0 = Sunday ... 6 = Saturday
    private WeekUsageReport currentWeekReport;

    private enum UsageCategory {
        SOCIAL,
        ENTERTAINMENT,
        SHOPPING,
        OTHER
    }

    private static class AppInterval {
        String packageName;
        long start;
        long end;

        AppInterval(String packageName, long start, long end) {
            this.packageName = packageName;
            this.start = start;
            this.end = end;
        }
    }

    private static class UsageReport {
        long totalMillis = 0L;
        Map<String, Long> appUsageMap = new HashMap<>();
        Map<UsageCategory, Long> categoryUsageMap = new HashMap<>();
    }

    private static class WeekUsageReport {
        Calendar weekStart;
        Calendar weekEnd;
        UsageReport[] dailyReports = new UsageReport[7];
        long weeklyTotalMillis = 0L;
        long dailyAverageMillis = 0L;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usage_stats, container, false);

        tvWeekBadge = view.findViewById(R.id.tvWeekBadge);
        tvWeeklyTotal = view.findViewById(R.id.tvWeeklyTotal);
        tvWeekRange = view.findViewById(R.id.tvWeekRange);
        tvDailyAverage = view.findViewById(R.id.tvDailyAverage);
        tvUpdatedAt = view.findViewById(R.id.tvUpdatedAt);
        tvSelectedDayTitle = view.findViewById(R.id.tvSelectedDayTitle);
        tvSocialTime = view.findViewById(R.id.tvSocialTime);
        tvEntertainmentTime = view.findViewById(R.id.tvEntertainmentTime);
        tvShoppingTime = view.findViewById(R.id.tvShoppingTime);
        btnPrevWeek = view.findViewById(R.id.btnPrevWeek);
        btnNextWeek = view.findViewById(R.id.btnNextWeek);
        llWeeklyChart = view.findViewById(R.id.llWeeklyChart);
        llAppList = view.findViewById(R.id.llAppList);

        View btnBack = view.findViewById(R.id.btnBackUsage);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        }

        if (btnPrevWeek != null) {
            btnPrevWeek.setOnClickListener(v -> {
                weekOffset -= 1;
                bindWeekReport();
            });
        }

        if (btnNextWeek != null) {
            btnNextWeek.setOnClickListener(v -> {
                if (weekOffset < 0) {
                    weekOffset += 1;
                    bindWeekReport();
                }
            });
        }

        bindWeekReport();
        return view;
    }

    private void bindWeekReport() {
        if (!isAdded()) return;

        currentWeekReport = collectWeekUsageReport(weekOffset);
        if (currentWeekReport == null) return;

        if (weekOffset == 0) {
            if (tvWeekBadge != null) tvWeekBadge.setText("✨ 이번 주");
        } else {
            if (tvWeekBadge != null) tvWeekBadge.setText("✨ 이전 주");
        }

        if (selectedDayIndex < 0 || selectedDayIndex > 6 || weekOffset != 0 && selectedDayIndex == getTodayDayIndex()) {
            selectedDayIndex = getDefaultSelectedDayIndex(currentWeekReport);
        }

        if (tvWeeklyTotal != null) tvWeeklyTotal.setText(formatDuration(currentWeekReport.weeklyTotalMillis));
        if (tvWeekRange != null) tvWeekRange.setText(formatWeekRange(currentWeekReport.weekStart, currentWeekReport.weekEnd));
        if (tvDailyAverage != null) tvDailyAverage.setText("일 평균 " + formatDuration(currentWeekReport.dailyAverageMillis));
        if (tvUpdatedAt != null) {
            String updated = new SimpleDateFormat("오늘 a h:mm에 업데이트됨", Locale.KOREAN).format(Calendar.getInstance().getTime());
            tvUpdatedAt.setText(updated);
        }

        if (btnNextWeek != null) {
            boolean enabled = weekOffset < 0;
            btnNextWeek.setAlpha(enabled ? 1f : 0.45f);
            btnNextWeek.setClickable(enabled);
        }

        drawWeeklyChart(currentWeekReport);
        bindSelectedDay(currentWeekReport.dailyReports[selectedDayIndex], selectedDayIndex);
    }

    private int getDefaultSelectedDayIndex(WeekUsageReport report) {
        if (weekOffset == 0) {
            return getTodayDayIndex();
        }

        int latestWithData = 0;
        for (int i = 0; i < 7; i++) {
            if (report.dailyReports[i] != null && report.dailyReports[i].totalMillis > 0) {
                latestWithData = i;
            }
        }
        return latestWithData;
    }

    private int getTodayDayIndex() {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
    }

    private WeekUsageReport collectWeekUsageReport(int offset) {
        WeekUsageReport weekReport = new WeekUsageReport();

        Calendar weekStart = Calendar.getInstance();
        weekStart.setFirstDayOfWeek(Calendar.SUNDAY);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);
        int currentDayIndex = weekStart.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        weekStart.add(Calendar.DAY_OF_MONTH, -currentDayIndex);
        weekStart.add(Calendar.WEEK_OF_YEAR, offset);

        Calendar weekEnd = (Calendar) weekStart.clone();
        weekEnd.add(Calendar.DAY_OF_MONTH, 6);

        weekReport.weekStart = (Calendar) weekStart.clone();
        weekReport.weekEnd = (Calendar) weekEnd.clone();

        long now = System.currentTimeMillis();
        long weeklySum = 0L;

        for (int i = 0; i < 7; i++) {
            Calendar dayStart = (Calendar) weekStart.clone();
            dayStart.add(Calendar.DAY_OF_MONTH, i);
            Calendar dayEnd = (Calendar) dayStart.clone();
            dayEnd.add(Calendar.DAY_OF_MONTH, 1);

            long start = dayStart.getTimeInMillis();
            long end = Math.min(dayEnd.getTimeInMillis(), now);

            UsageReport dayReport;
            if (start >= now) {
                dayReport = new UsageReport();
            } else {
                dayReport = collectUsageReport(start, end);
            }

            weekReport.dailyReports[i] = dayReport;
            weeklySum += dayReport.totalMillis;
        }

        weekReport.weeklyTotalMillis = weeklySum;
        weekReport.dailyAverageMillis = weeklySum / 7L;
        return weekReport;
    }

    private UsageReport collectUsageReport(long startTime, long endTime) {
        UsageReport report = new UsageReport();
        if (getContext() == null || startTime >= endTime) return report;

        UsageStatsManager usageStatsManager = (UsageStatsManager) requireContext().getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) return report;

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        List<AppInterval> intervals = new ArrayList<>();

        String currentActiveApp = null;
        long currentAppStartTime = 0L;
        UsageEvents.Event event = new UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            String pkg = event.getPackageName();
            if (shouldIgnorePackage(pkg)) continue;

            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (currentActiveApp != null && !currentActiveApp.equals(pkg)) {
                    addInterval(intervals, currentActiveApp, currentAppStartTime, eventTime, startTime, endTime);
                }
                currentActiveApp = pkg;
                currentAppStartTime = eventTime;
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (currentActiveApp != null && currentActiveApp.equals(pkg)) {
                    addInterval(intervals, currentActiveApp, currentAppStartTime, eventTime, startTime, endTime);
                    currentActiveApp = null;
                }
            }
        }

        if (currentActiveApp != null) {
            addInterval(intervals, currentActiveApp, currentAppStartTime, endTime, startTime, endTime);
        }

        intervals.sort((a, b) -> Long.compare(a.start, b.start));
        report.totalMillis = calculateMergedScreenTime(intervals);

        for (AppInterval interval : intervals) {
            long duration = interval.end - interval.start;
            if (duration <= 0) continue;

            report.appUsageMap.put(interval.packageName,
                    report.appUsageMap.getOrDefault(interval.packageName, 0L) + duration);

            UsageCategory category = getUsageCategory(interval.packageName);
            report.categoryUsageMap.put(category,
                    report.categoryUsageMap.getOrDefault(category, 0L) + duration);
        }

        return report;
    }

    private void addInterval(List<AppInterval> intervals, String pkg, long start, long end, long queryStart, long queryEnd) {
        long safeStart = Math.max(start, queryStart);
        long safeEnd = Math.min(end, queryEnd);
        if (pkg != null && safeEnd > safeStart) {
            intervals.add(new AppInterval(pkg, safeStart, safeEnd));
        }
    }

    private boolean shouldIgnorePackage(String pkg) {
        if (pkg == null || getContext() == null) return true;
        String lower = pkg.toLowerCase(Locale.ROOT);
        return lower.contains("launcher")
                || lower.contains("systemui")
                || lower.contains("permissioncontroller")
                || lower.contains("packageinstaller")
                || lower.contains("settings")
                || pkg.equals(requireContext().getPackageName());
    }

    private long calculateMergedScreenTime(List<AppInterval> intervals) {
        if (intervals.isEmpty()) return 0L;

        long total = 0L;
        long currentStart = intervals.get(0).start;
        long currentEnd = intervals.get(0).end;

        for (int i = 1; i < intervals.size(); i++) {
            AppInterval next = intervals.get(i);
            if (next.start <= currentEnd) {
                currentEnd = Math.max(currentEnd, next.end);
            } else {
                total += currentEnd - currentStart;
                currentStart = next.start;
                currentEnd = next.end;
            }
        }

        total += currentEnd - currentStart;
        return total;
    }

    private UsageCategory getUsageCategory(String packageName) {
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);

        if (pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("twitter")
                || pkg.contains("kakao.talk") || pkg.contains("line") || pkg.contains("discord")
                || pkg.contains("everytime") || pkg.contains("band") || pkg.contains("threads")) {
            return UsageCategory.SOCIAL;
        }
        if (pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("tiktok")
                || pkg.contains("wavve") || pkg.contains("tving") || pkg.contains("music")
                || pkg.contains("melon") || pkg.contains("genie") || pkg.contains("spotify")
                || pkg.contains("afreeca") || pkg.contains("game")) {
            return UsageCategory.ENTERTAINMENT;
        }
        if (pkg.contains("baemin") || pkg.contains("yogiyo") || pkg.contains("coupang")
                || pkg.contains("kurly") || pkg.contains("ably") || pkg.contains("musinsa")
                || pkg.contains("11st") || pkg.contains("gmarket") || pkg.contains("auction")
                || pkg.contains("market") || pkg.contains("store") || pkg.contains("shopping")) {
            return UsageCategory.SHOPPING;
        }

        try {
            PackageManager pm = requireContext().getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            if (info.category == ApplicationInfo.CATEGORY_SOCIAL) return UsageCategory.SOCIAL;
            if (info.category == ApplicationInfo.CATEGORY_VIDEO
                    || info.category == ApplicationInfo.CATEGORY_AUDIO
                    || info.category == ApplicationInfo.CATEGORY_GAME) {
                return UsageCategory.ENTERTAINMENT;
            }
            // Android ApplicationInfo에는 CATEGORY_SHOPPING 상수가 없어서 쇼핑 앱은 위의 패키지명 조건으로 분류합니다.
        } catch (Exception ignored) {
        }

        return UsageCategory.OTHER;
    }

    private void drawWeeklyChart(WeekUsageReport weekReport) {
        if (llWeeklyChart == null || getContext() == null) return;
        llWeeklyChart.removeAllViews();

        long max = 10 * 60 * 1000L;
        for (UsageReport report : weekReport.dailyReports) {
            if (report != null) {
                max = Math.max(max, report.totalMillis);
            }
        }

        String[] labels = {"일", "월", "화", "수", "목", "금", "토"};

        for (int i = 0; i < 7; i++) {
            UsageReport dayReport = weekReport.dailyReports[i];
            long millis = dayReport == null ? 0L : dayReport.totalMillis;
            boolean isSelected = i == selectedDayIndex;

            LinearLayout column = new LinearLayout(getContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            column.setClickable(true);
            column.setFocusable(true);
            column.setPadding(dp(2), 0, dp(2), 0);
            final int finalI = i;
            column.setOnClickListener(v -> {
                selectedDayIndex = finalI;
                drawWeeklyChart(currentWeekReport);
                bindSelectedDay(currentWeekReport.dailyReports[finalI], finalI);
            });

            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            llWeeklyChart.addView(column, columnParams);

            /*
            TextView timeLabel = new TextView(getContext());
            timeLabel.setText(formatDurationTwoLine(millis));
            timeLabel.setTextColor(isSelected ? COLOR_TEXT_MAIN : COLOR_TEXT_SUB);
            timeLabel.setTextSize(11f);
            timeLabel.setGravity(Gravity.CENTER);
            timeLabel.setTypeface(Typeface.DEFAULT, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            column.addView(timeLabel);

            View spacer = new View(getContext());
            column.addView(spacer, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
            */

            View bar = new View(getContext());
            int barHeight = millis <= 0 ? dp(10) : (int) Math.max(dp(10), (millis * dp(96)) / (float) max);
            int barWidth = dp(34);
            bar.setBackground(createRoundedDrawable(isSelected ? COLOR_YELLOW_ACTIVE : COLOR_BAR_DEFAULT, 12));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(barWidth, barHeight);
            column.addView(bar, barParams);

            View spacer2 = new View(getContext());
            column.addView(spacer2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));

            TextView dayLabel = new TextView(getContext());
            dayLabel.setText(labels[i]);
            dayLabel.setGravity(Gravity.CENTER);
            dayLabel.setTextSize(13f);
            dayLabel.setTextColor(isSelected ? COLOR_TEXT_MAIN : COLOR_TEXT_SUB);
            dayLabel.setTypeface(Typeface.DEFAULT, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            if (isSelected) {
                dayLabel.setBackground(createRoundedDrawable(COLOR_YELLOW_SOFT, 999));
                dayLabel.setPadding(dp(12), dp(4), dp(12), dp(4));
            }
            column.addView(dayLabel);
        }
    }

    private void bindSelectedDay(UsageReport selectedReport, int dayIndex) {
        if (selectedReport == null) selectedReport = new UsageReport();

        String[] dayNames = {"일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일"};
        if (tvSelectedDayTitle != null) {
            tvSelectedDayTitle.setText(dayNames[dayIndex] + " 사용 앱");
        }

        setCategoryText(tvSocialTime, selectedReport.categoryUsageMap.getOrDefault(UsageCategory.SOCIAL, 0L));
        setCategoryText(tvEntertainmentTime, selectedReport.categoryUsageMap.getOrDefault(UsageCategory.ENTERTAINMENT, 0L));
        setCategoryText(tvShoppingTime, selectedReport.categoryUsageMap.getOrDefault(UsageCategory.SHOPPING, 0L));
        drawAppList(selectedReport.appUsageMap);
    }

    private void drawAppList(Map<String, Long> appUsageMap) {
        if (llAppList == null || getContext() == null) return;
        llAppList.removeAllViews();

        PackageManager pm = requireContext().getPackageManager();
        List<Map.Entry<String, Long>> sortedList = new ArrayList<>(appUsageMap.entrySet());
        sortedList.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        long maxDuration = 1L;
        for (Map.Entry<String, Long> entry : sortedList) {
            if (entry.getValue() >= 60 * 1000L) {
                maxDuration = Math.max(maxDuration, entry.getValue());
            }
        }

        int shown = 0;
        for (Map.Entry<String, Long> entry : sortedList) {
            if (entry.getValue() < 60 * 1000L) continue;
            if (shown >= 7) break;

            String packageName = entry.getKey();
            String appLabel = packageName;
            Drawable appIcon = null;

            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                appLabel = pm.getApplicationLabel(appInfo).toString();
                appIcon = pm.getApplicationIcon(appInfo);
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            llAppList.addView(createAppRow(appLabel, appIcon, entry.getValue(), maxDuration));
            shown++;
        }

        if (shown == 0) {
            TextView empty = new TextView(getContext());
            empty.setText("선택한 날짜의 앱 사용 데이터가 아직 없어요.");
            empty.setTextColor(COLOR_TEXT_SUB);
            empty.setTextSize(15f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, dp(20));
            llAppList.addView(empty);
        }
    }

    private View createAppRow(String appLabel, Drawable icon, long duration, long maxDuration) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        ImageView iconView = new ImageView(getContext());
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iconView.setBackground(createRoundedDrawable(0xFFF7F6F1, 14));
        iconView.setPadding(dp(6), dp(6), dp(6), dp(6));
        if (icon != null) {
            iconView.setImageDrawable(icon);
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconParams.rightMargin = dp(14);
        row.addView(iconView, iconParams);

        LinearLayout center = new LinearLayout(getContext());
        center.setOrientation(LinearLayout.VERTICAL);
        row.addView(center, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(getContext());
        title.setText(appLabel);
        title.setTextColor(COLOR_TEXT_MAIN);
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        center.addView(title);

        ProgressBar progressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        int progress = (int) Math.max(8, duration * 1000f / maxDuration);
        progressBar.setProgress(progress);
        progressBar.setProgressDrawable(createProgressDrawable());
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressParams.topMargin = dp(10);
        center.addView(progressBar, progressParams);

        TextView time = new TextView(getContext());
        time.setText(formatDuration(duration));
        time.setTextColor(COLOR_TEXT_SUB);
        time.setTextSize(16f);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.leftMargin = dp(12);
        row.addView(time, timeParams);

        return row;
    }

    private LayerDrawable createProgressDrawable() {
        GradientDrawable bg = createRoundedDrawable(COLOR_BAR_BG, 999);
        GradientDrawable progress = createRoundedDrawable(COLOR_YELLOW_ACTIVE, 999);
        ClipDrawable clip = new ClipDrawable(progress, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layer = new LayerDrawable(new Drawable[]{bg, clip});
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.progress);
        return layer;
    }

    private GradientDrawable createRoundedDrawable(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void setCategoryText(TextView textView, long millis) {
        if (textView != null) textView.setText(formatDuration(millis));
    }

    private String formatWeekRange(Calendar start, Calendar end) {
        return String.format(Locale.KOREAN, "%d월 %d일 - %d월 %d일",
                start.get(Calendar.MONTH) + 1,
                start.get(Calendar.DAY_OF_MONTH),
                end.get(Calendar.MONTH) + 1,
                end.get(Calendar.DAY_OF_MONTH));
    }

    private String formatDuration(long millis) {
        long totalMinutes = Math.max(0, millis / (1000 * 60));
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0 && minutes > 0) return hours + "시간 " + minutes + "분";
        if (hours > 0) return hours + "시간";
        return minutes + "분";
    }

    private String formatDurationTwoLine(long millis) {
        long totalMinutes = Math.max(0, millis / (1000 * 60));
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        if (hours > 0 && minutes > 0) return hours + "시간\n" + minutes + "분";
        if (hours > 0) return hours + "시간";
        return minutes + "분";
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
