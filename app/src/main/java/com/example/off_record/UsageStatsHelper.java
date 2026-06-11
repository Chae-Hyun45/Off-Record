package com.example.off_record;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageStatsHelper {

    private static final long SHORT_SESSION_LIMIT = 2 * 60 * 1000L; // 2분 이하

    public static class PhoneUsageSummary {
        public long totalUsageMinutes;
        public int totalOpenCount;
        public int shortSessionCount;
        public long nightUsageMinutes;
        public int digitalSignalScore;
        public String digitalPattern;

        public String toPromptText() {
            return "- 총 휴대폰 사용시간: " + totalUsageMinutes + "분\n" +
                    "- 앱 실행 횟수: " + totalOpenCount + "회\n" +
                    "- 짧은 반복 사용 횟수: " + shortSessionCount + "회\n" +
                    "- 야간 사용시간(00시~03시): " + nightUsageMinutes + "분\n" +
                    "- 디지털 불안 신호 점수: " + digitalSignalScore + "점\n" +
                    "- 사용 패턴: " + digitalPattern;
        }
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

    public static PhoneUsageSummary getTodayUsageSummary(Context context) {
        PhoneUsageSummary summary = new PhoneUsageSummary();

        if (context == null) {
            summary.digitalPattern = "데이터 없음";
            return summary;
        }

        Calendar startCal = Calendar.getInstance();
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);

        long startTime = startCal.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        return collectUsageSummary(context, startTime, endTime);
    }

    private static PhoneUsageSummary collectUsageSummary(Context context, long startTime, long endTime) {
        PhoneUsageSummary summary = new PhoneUsageSummary();

        UsageStatsManager usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        if (usageStatsManager == null || startTime >= endTime) {
            summary.digitalPattern = "데이터 없음";
            return summary;
        }

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);

        List<AppInterval> intervals = new ArrayList<>();
        Map<String, Long> openTimeMap = new HashMap<>();

        UsageEvents.Event event = new UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);

            String pkg = event.getPackageName();
            if (shouldIgnorePackage(context, pkg)) continue;

            int eventType = event.getEventType();
            long eventTime = event.getTimeStamp();

            if (eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                summary.totalOpenCount++;
                openTimeMap.put(pkg, eventTime);
            } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (openTimeMap.containsKey(pkg)) {
                    long openTime = openTimeMap.remove(pkg);
                    long duration = eventTime - openTime;

                    if (duration > 0) {
                        intervals.add(new AppInterval(pkg, openTime, eventTime));

                        if (duration <= SHORT_SESSION_LIMIT) {
                            summary.shortSessionCount++;
                        }

                        summary.nightUsageMinutes += calculateNightUsageMinutes(openTime, eventTime);
                    }
                }
            }
        }

        for (Map.Entry<String, Long> entry : openTimeMap.entrySet()) {
            long openTime = entry.getValue();
            long duration = endTime - openTime;

            if (duration > 0) {
                intervals.add(new AppInterval(entry.getKey(), openTime, endTime));

                if (duration <= SHORT_SESSION_LIMIT) {
                    summary.shortSessionCount++;
                }

                summary.nightUsageMinutes += calculateNightUsageMinutes(openTime, endTime);
            }
        }

        summary.totalUsageMinutes = calculateMergedUsageMinutes(intervals);
        summary.digitalSignalScore = calculateDigitalSignalScore(summary);
        summary.digitalPattern = getDigitalPattern(summary.digitalSignalScore);

        return summary;
    }

    private static boolean shouldIgnorePackage(Context context, String pkg) {
        if (pkg == null) return true;

        String lower = pkg.toLowerCase(Locale.ROOT);

        return lower.contains("launcher")
                || lower.contains("systemui")
                || lower.contains("permissioncontroller")
                || lower.contains("packageinstaller")
                || lower.contains("settings")
                || pkg.equals(context.getPackageName());
    }

    private static long calculateMergedUsageMinutes(List<AppInterval> intervals) {
        if (intervals.isEmpty()) return 0L;

        intervals.sort((a, b) -> Long.compare(a.start, b.start));

        long totalMillis = 0L;
        long currentStart = intervals.get(0).start;
        long currentEnd = intervals.get(0).end;

        for (int i = 1; i < intervals.size(); i++) {
            AppInterval next = intervals.get(i);

            if (next.start <= currentEnd) {
                currentEnd = Math.max(currentEnd, next.end);
            } else {
                totalMillis += currentEnd - currentStart;
                currentStart = next.start;
                currentEnd = next.end;
            }
        }

        totalMillis += currentEnd - currentStart;

        return totalMillis / (1000 * 60);
    }

    private static long calculateNightUsageMinutes(long start, long end) {
        Calendar nightStart = Calendar.getInstance();
        nightStart.setTimeInMillis(start);
        nightStart.set(Calendar.HOUR_OF_DAY, 0);
        nightStart.set(Calendar.MINUTE, 0);
        nightStart.set(Calendar.SECOND, 0);
        nightStart.set(Calendar.MILLISECOND, 0);

        Calendar nightEnd = (Calendar) nightStart.clone();
        nightEnd.set(Calendar.HOUR_OF_DAY, 3);

        long overlapStart = Math.max(start, nightStart.getTimeInMillis());
        long overlapEnd = Math.min(end, nightEnd.getTimeInMillis());

        if (overlapEnd <= overlapStart) return 0L;

        return (overlapEnd - overlapStart) / (1000 * 60);
    }

    private static int calculateDigitalSignalScore(PhoneUsageSummary summary) {
        int score = 0;

        // 앱을 자주 여는 행동
        score += Math.min(25, summary.totalOpenCount / 2);

        // 짧게 자주 확인하는 행동
        score += Math.min(25, summary.shortSessionCount * 2);

        // 야간 사용
        score += Math.min(20, (int) (summary.nightUsageMinutes / 5));

        // 총 사용시간
        score += Math.min(30, (int) (summary.totalUsageMinutes / 20));

        return Math.min(100, score);
    }

    private static String getDigitalPattern(int score) {
        if (score >= 80) return "높은 디지털 불안 신호";
        if (score >= 60) return "짧은 반복 확인형";
        if (score >= 40) return "주의형";
        return "안정형";
    }
}