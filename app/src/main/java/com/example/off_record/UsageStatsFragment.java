package com.example.off_record;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageStatsFragment extends Fragment {

    private TextView tvTotalTimeResult;
    private LinearLayout llAppList;

    private static class AppInterval {
        long start;
        long end;
        AppInterval(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usage_stats, container, false);

        tvTotalTimeResult = view.findViewById(R.id.tvTotalTimeResult);
        llAppList = view.findViewById(R.id.llAppList);

        displayUsageReport();
        return view;
    }

    private void displayUsageReport() {
        if (llAppList != null) llAppList.removeAllViews();

        UsageStatsManager usageStatsManager = (UsageStatsManager) requireContext().getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager packageManager = requireContext().getPackageManager();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);

        Map<String, Long> appUsageMap = new HashMap<>();
        List<AppInterval> intervalList = new ArrayList<>();

        String currentActiveApp = null;
        long currentAppStartTime = 0;

        UsageEvents.Event event = new UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            String pkg = event.getPackageName();
            if (pkg.contains("launcher") || pkg.equals(requireContext().getPackageName()) || pkg.contains("systemui")) continue;

            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (currentActiveApp != null && !currentActiveApp.equals(pkg)) {
                    long duration = eventTime - currentAppStartTime;
                    if (duration > 0) {
                        appUsageMap.put(currentActiveApp, appUsageMap.getOrDefault(currentActiveApp, 0L) + duration);
                        intervalList.add(new AppInterval(currentAppStartTime, eventTime));
                    }
                }
                currentActiveApp = pkg;
                currentAppStartTime = eventTime;
            }
            else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (currentActiveApp != null && currentActiveApp.equals(pkg)) {
                    long duration = eventTime - currentAppStartTime;
                    if (duration > 0) {
                        appUsageMap.put(currentActiveApp, appUsageMap.getOrDefault(currentActiveApp, 0L) + duration);
                        intervalList.add(new AppInterval(currentAppStartTime, eventTime));
                    }
                    currentActiveApp = null;
                }
            }
        }

        if (currentActiveApp != null) {
            long duration = endTime - currentAppStartTime;
            if (duration > 0) {
                appUsageMap.put(currentActiveApp, appUsageMap.getOrDefault(currentActiveApp, 0L) + duration);
                intervalList.add(new AppInterval(currentAppStartTime, endTime));
            }
        }

        // 1. 순수 총 스크린 타임 계산 및 출력 (오늘 하루 치만 정확히 계산됨)
        long totalMillis = calculatePureScreenOnTime(intervalList);
        long totalTotalMinutes = totalMillis / (1000 * 60);
        long totalHours = totalTotalMinutes / 60;
        long remainingMinutes = totalTotalMinutes % 60;

        String totalText = "";
        if (totalHours > 0) totalText += totalHours + "시간 ";
        totalText += remainingMinutes + "분";
        if (tvTotalTimeResult != null) tvTotalTimeResult.setText(totalText);

        // 2. 오늘 사용량 순 정렬
        List<Map.Entry<String, Long>> sortedList = new ArrayList<>(appUsageMap.entrySet());
        sortedList.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        // 3. 무제한 리스트 출력
        int rank = 1;
        for (Map.Entry<String, Long> entry : sortedList) {
            String packageName = entry.getKey();
            long minutes = entry.getValue() / (1000 * 60);
            if (minutes == 0) continue; // 1분 미만 사용 앱 출력 제외

            String appLabel = packageName;
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                appLabel = packageManager.getApplicationLabel(appInfo).toString();
            } catch (PackageManager.NameNotFoundException e) {}

            TextView tvAppItem = new TextView(getContext());
            tvAppItem.setText(rank + "위 : " + appLabel + " (" + minutes + "분)");
            tvAppItem.setTextSize(15);
            tvAppItem.setTextColor(0xFF334155);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, 32);
            tvAppItem.setLayoutParams(params);

            if (llAppList != null) llAppList.addView(tvAppItem);
            rank++;
        }

        if (rank == 1 && llAppList != null) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("오늘 사용한 앱 데이터가 존재하지 않습니다.");
            llAppList.addView(tvEmpty);
        }
    }

    private long calculatePureScreenOnTime(List<AppInterval> intervals) {
        if (intervals.isEmpty()) return 0;
        intervals.sort((a, b) -> Long.compare(a.start, b.start));

        long totalDuration = 0;
        long currentStart = intervals.get(0).start;
        long currentEnd = intervals.get(0).end;

        for (int i = 1; i < intervals.size(); i++) {
            AppInterval next = intervals.get(i);
            if (next.start <= currentEnd) {
                currentEnd = Math.max(currentEnd, next.end);
            } else {
                totalDuration += (currentEnd - currentStart);
                currentStart = next.start;
                currentEnd = next.end;
            }
        }
        totalDuration += (currentEnd - currentStart);
        return totalDuration;
    }
}