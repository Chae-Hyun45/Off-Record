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

        // 권한 체크 추가
        if (!hasUsageStatsPermission(context)) {
            summary.digitalPattern = "권한 미허용";
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

    public static PhoneUsageSummary getYesterdayUsageSummary(Context context) {
        PhoneUsageSummary summary = new PhoneUsageSummary();

        if (context == null || !hasUsageStatsPermission(context)) {
            summary.digitalPattern = "데이터 없음";
            return summary;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long startTime = cal.getTimeInMillis();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        long endTime = cal.getTimeInMillis();

        return collectUsageSummary(context, startTime, endTime);
    }

    public static boolean hasUsageStatsPermission(Context context) {
        try {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private static PhoneUsageSummary collectUsageSummary(Context context, long startTime, long endTime) {
        PhoneUsageSummary summary = new PhoneUsageSummary();
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

        if (usageStatsManager == null || startTime >= endTime) {
            summary.digitalPattern = "데이터 없음";
            return summary;
        }

        // '생활 데이터' 메뉴와 100% 동일한 Raw Events 분석 로직
        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        List<AppInterval> rawIntervals = new ArrayList<>();
        
        String currentActiveApp = null;
        long currentAppStartTime = 0L;
        UsageEvents.Event event = new UsageEvents.Event();

        int totalOpenCount = 0;
        String lastPkg = "";

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            String pkg = event.getPackageName();
            if (shouldIgnorePackage(context, pkg)) continue;

            int type = event.getEventType();
            long time = event.getTimeStamp();

            if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (currentActiveApp != null && !currentActiveApp.equals(pkg)) {
                    addInterval(rawIntervals, currentActiveApp, currentAppStartTime, time, startTime, endTime);
                }
                currentActiveApp = pkg;
                currentAppStartTime = time;

                if (!pkg.equals(lastPkg)) {
                    totalOpenCount++;
                    lastPkg = pkg;
                }
            } else if (type == UsageEvents.Event.ACTIVITY_PAUSED) {
                if (currentActiveApp != null && currentActiveApp.equals(pkg)) {
                    addInterval(rawIntervals, currentActiveApp, currentAppStartTime, time, startTime, endTime);
                    currentActiveApp = null;
                }
            }
        }

        if (currentActiveApp != null) {
            addInterval(rawIntervals, currentActiveApp, currentAppStartTime, endTime, startTime, endTime);
        }

        // 중복 제거된 순수 사용 시간 계산
        rawIntervals.sort((a, b) -> Long.compare(a.start, b.start));
        long totalMillis = calculateMergedUsageMillis(rawIntervals);
        summary.totalUsageMinutes = totalMillis / (1000 * 60);
        summary.totalOpenCount = totalOpenCount;

        // 세부 지표 계산 (야간 사용 및 짧은 세션)
        long nightMillis = 0;
        int shortSessions = 0;
        
        Calendar nightStart = Calendar.getInstance();
        nightStart.setTimeInMillis(startTime);
        nightStart.set(Calendar.HOUR_OF_DAY, 0);
        nightStart.set(Calendar.MINUTE, 0);
        nightStart.set(Calendar.SECOND, 0);
        long nStart = nightStart.getTimeInMillis();
        long nEnd = nStart + (3 * 60 * 60 * 1000L); // 00:00 ~ 03:00

        for (AppInterval interval : rawIntervals) {
            long duration = interval.end - interval.start;
            if (duration > 5000L && duration <= SHORT_SESSION_LIMIT) {
                shortSessions++;
            }
            
            long overlapStart = Math.max(interval.start, nStart);
            long overlapEnd = Math.min(interval.end, nEnd);
            if (overlapEnd > overlapStart) {
                nightMillis += (overlapEnd - overlapStart);
            }
        }

        summary.nightUsageMinutes = Math.min(180, nightMillis / (1000 * 60));
        summary.shortSessionCount = shortSessions;

        if (summary.totalUsageMinutes == 0) {
            summary.digitalPattern = "활동 감지 안됨";
        } else {
            summary.digitalSignalScore = calculateDigitalSignalScore(summary);
            summary.digitalPattern = getDigitalPattern(summary.digitalSignalScore);
        }

        return summary;
    }

    private static void addInterval(List<AppInterval> intervals, String pkg, long start, long end, long queryStart, long queryEnd) {
        long safeStart = Math.max(start, queryStart);
        long safeEnd = Math.min(end, queryEnd);
        if (pkg != null && safeEnd > safeStart) {
            intervals.add(new AppInterval(pkg, safeStart, safeEnd));
        }
    }

    private static long calculateMergedUsageMillis(List<AppInterval> intervals) {
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

    private static List<AppInterval> mergeIntervals(List<AppInterval> intervals) {
        if (intervals.isEmpty()) return new ArrayList<>();
        intervals.sort((a, b) -> Long.compare(a.start, b.start));

        List<AppInterval> merged = new ArrayList<>();
        AppInterval current = new AppInterval(intervals.get(0).packageName, intervals.get(0).start, intervals.get(0).end);

        for (int i = 1; i < intervals.size(); i++) {
            AppInterval next = intervals.get(i);
            if (next.start <= current.end) {
                current.end = Math.max(current.end, next.end);
            } else {
                merged.add(current);
                current = new AppInterval(next.packageName, next.start, next.end);
            }
        }
        merged.add(current);
        return merged;
    }

    private static List<AppInterval> mergeIntervalsByPackage(List<AppInterval> intervals) {
        if (intervals.isEmpty()) return new ArrayList<>();
        
        // 시간순 정렬
        intervals.sort((a, b) -> Long.compare(a.start, b.start));

        List<AppInterval> merged = new ArrayList<>();
        AppInterval current = new AppInterval(intervals.get(0).packageName, intervals.get(0).start, intervals.get(0).end);

        for (int i = 1; i < intervals.size(); i++) {
            AppInterval next = intervals.get(i);
            
            // 동일한 앱이면서 시간 간격이 10초 이내인 경우 하나의 세션으로 간주
            if (next.packageName.equals(current.packageName) && next.start <= (current.end + 10000L)) {
                current.end = Math.max(current.end, next.end);
            } else {
                merged.add(current);
                current = new AppInterval(next.packageName, next.start, next.end);
            }
        }
        merged.add(current);
        return merged;
    }

    private static long calculateNightUsageFromMerged(List<AppInterval> mergedTimeline, long baseStartTime) {
        Calendar nightStart = Calendar.getInstance();
        nightStart.setTimeInMillis(baseStartTime);
        nightStart.set(Calendar.HOUR_OF_DAY, 0);
        nightStart.set(Calendar.MINUTE, 0);
        nightStart.set(Calendar.SECOND, 0);
        nightStart.set(Calendar.MILLISECOND, 0);

        Calendar nightEnd = (Calendar) nightStart.clone();
        nightEnd.set(Calendar.HOUR_OF_DAY, 3);

        long nStart = nightStart.getTimeInMillis();
        long nEnd = nightEnd.getTimeInMillis();

        long nightMillis = 0;
        for (AppInterval interval : mergedTimeline) {
            long overlapStart = Math.max(interval.start, nStart);
            long overlapEnd = Math.min(interval.end, nEnd);
            if (overlapEnd > overlapStart) {
                nightMillis += (overlapEnd - overlapStart);
            }
        }
        return nightMillis / (1000 * 60);
    }

    private static boolean shouldIgnorePackage(Context context, String pkg) {
        if (pkg == null) return true;
        String lower = pkg.toLowerCase(Locale.ROOT);

        // 시스템 필수 패키지 및 백그라운드 노이즈 필터링 강화
        return lower.contains("launcher")
                || lower.contains("systemui")
                || lower.contains("permissioncontroller")
                || lower.contains("packageinstaller")
                || lower.contains("gms") // Google Play Services
                || lower.contains("inputmethod") // 키보드
                || pkg.equals(context.getPackageName());
    }

    private static long calculateMergedUsageMinutes(List<AppInterval> intervals) {
        List<AppInterval> merged = mergeIntervals(intervals);
        long total = 0;
        for (AppInterval i : merged) total += (i.end - i.start);
        return total / (1000 * 60);
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