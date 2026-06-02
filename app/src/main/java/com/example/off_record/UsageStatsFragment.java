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
    private TextView tvRank1, tvRank2, tvRank3;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usage_stats, container, false);

        tvTotalTimeResult = view.findViewById(R.id.tvTotalTimeResult);
        tvRank1 = view.findViewById(R.id.tvRank1);
        tvRank2 = view.findViewById(R.id.tvRank2);
        tvRank3 = view.findViewById(R.id.tvRank3);

        displayUsageReport();
        return view;
    }

    private void displayUsageReport() {
        Map<String, Object> topApps = getTop3AppUsage();
        if (topApps.isEmpty()) return;

        long totalHours = topApps.containsKey("total_hours") ? (long) topApps.get("total_hours") : 0L;
        long totalMinutes = topApps.containsKey("total_minutes") ? (long) topApps.get("total_minutes") : 0L;

        String totalText = "";
        if (totalHours > 0) totalText += totalHours + "시간 ";
        totalText += totalMinutes + "분";
        tvTotalTimeResult.setText(totalText);

        if (topApps.containsKey("app1_name")) tvRank1.setText("🥇 1위 : " + topApps.get("app1_name") + " (" + topApps.get("app1_time") + "분)");
        if (topApps.containsKey("app2_name")) tvRank2.setText("🥈 2위 : " + topApps.get("app2_name") + " (" + topApps.get("app2_time") + "분)");
        if (topApps.containsKey("app3_name")) tvRank3.setText("🥉 3위 : " + topApps.get("app3_name") + " (" + topApps.get("app3_time") + "분)");
    }

    private Map<String, Object> getTop3AppUsage() {
        Map<String, Object> topAppsResult = new HashMap<>();
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
        Map<String, Long> openTimeMap = new HashMap<>();

        UsageEvents.Event event = new UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            String pkg = event.getPackageName();
            if (pkg.contains("launcher") || pkg.equals(requireContext().getPackageName()) || pkg.contains("systemui")) continue;

            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                openTimeMap.put(pkg, eventTime);
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (openTimeMap.containsKey(pkg)) {
                    long openTime = openTimeMap.remove(pkg);
                    long duration = eventTime - openTime;
                    if (duration > 0) appUsageMap.put(pkg, appUsageMap.getOrDefault(pkg, 0L) + duration);
                }
            }
        }

        for (String pkg : openTimeMap.keySet()) {
            long openTime = openTimeMap.get(pkg);
            long duration = endTime - openTime;
            if (duration > 0) appUsageMap.put(pkg, appUsageMap.getOrDefault(pkg, 0L) + duration);
        }

        long totalMillis = 0;
        for (long duration : appUsageMap.values()) totalMillis += duration;
        long totalTotalMinutes = totalMillis / (1000 * 60);
        topAppsResult.put("total_hours", totalTotalMinutes / 60);
        topAppsResult.put("total_minutes", totalTotalMinutes % 60);

        List<Map.Entry<String, Long>> sortedList = new ArrayList<>(appUsageMap.entrySet());
        sortedList.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        int rank = 1;
        for (Map.Entry<String, Long> entry : sortedList) {
            if (rank > 3) break;
            String packageName = entry.getKey();
            long minutes = entry.getValue() / (1000 * 60);
            if (minutes == 0) continue;

            String appLabel = packageName;
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                appLabel = packageManager.getApplicationLabel(appInfo).toString();
            } catch (PackageManager.NameNotFoundException e) {}

            topAppsResult.put("app" + rank + "_name", appLabel);
            topAppsResult.put("app" + rank + "_time", minutes);
            rank++;
        }
        return topAppsResult;
    }
}