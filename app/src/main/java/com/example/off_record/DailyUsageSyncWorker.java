package com.example.off_record;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DailyUsageSyncWorker extends Worker {

    private static final String WORK_NAME = "DailyUsageSyncWork";

    public DailyUsageSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void enqueueDailyWork(Context context) {
        // 매일 새벽 00:05분에 실행되도록 설정 (12일 00:05에 실행되면 11일 데이터 결산)
        Calendar targetTime = Calendar.getInstance();
        targetTime.set(Calendar.HOUR_OF_DAY, 0);
        targetTime.set(Calendar.MINUTE, 5);
        targetTime.set(Calendar.SECOND, 0);
        targetTime.set(Calendar.MILLISECOND, 0);

        if (targetTime.getTimeInMillis() <= System.currentTimeMillis()) {
            targetTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        long delayMillis = targetTime.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                DailyUsageSyncWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build();

        // [핵심 수정] CANCEL_AND_REENQUEUE 정책을 사용하여 이전의 모든 잘못된 스케줄을 즉시 파괴하고 새 시간으로 교체
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, 
                workRequest
        );
        
        Log.d("DailyUsageSyncWorker", "Daily work scheduled at 00:05 with delay: " + (delayMillis/1000) + "s");
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("DailyUsageSyncWorker", "Starting daily usage sync...");

        Context context = getApplicationContext();
        if (!UsageStatsHelper.hasUsageStatsPermission(context)) {
            Log.d("DailyUsageSyncWorker", "No usage stats permission. Skipping sync.");
            return Result.success();
        }

        // 어제 날짜 계산 (24:00에 실행되므로 '어제'가 우리가 마감할 날짜임)
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterdayId = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        // 어제 데이터 수집
        UsageStatsHelper.PhoneUsageSummary summary = UsageStatsHelper.getYesterdayUsageSummary(context);

        // 데이터 맵 구성
        Map<String, Object> data = new HashMap<>();
        data.put("phoneTotalMinutes", summary.totalUsageMinutes);
        data.put("phoneOpenCount", summary.totalOpenCount);
        data.put("phoneShortSessionCount", summary.shortSessionCount);
        data.put("phoneNightUsageMinutes", summary.nightUsageMinutes);
        data.put("digitalSignalScore", summary.digitalSignalScore);
        data.put("digitalPattern", summary.digitalPattern);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            try {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                String uid = currentUser.getUid();

                // Tasks.await를 사용하여 비동기 작업이 완료될 때까지 대기 (WorkManager 스레드 차단)
                Tasks.await(db.collection("users")
                        .document(uid)
                        .collection("daily_records")
                        .document(yesterdayId)
                        .set(data, SetOptions.merge()));
                
                Log.d("DailyUsageSyncWorker", "Firestore sync successful: " + yesterdayId);
            } catch (Exception e) {
                Log.e("DailyUsageSyncWorker", "Firestore sync failed", e);
                return Result.retry();
            }
        } else {
            GuestRecordStore.saveTodayRecord(context, data, yesterdayId);
            Log.d("DailyUsageSyncWorker", "Guest sync done for: " + yesterdayId);
        }

        return Result.success();
    }
}
