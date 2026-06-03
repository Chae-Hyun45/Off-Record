package com.example.off_record;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import androidx.fragment.app.Fragment;

import java.util.Map;

public class ExtraFragment extends Fragment {

    private FirebaseFirestore db;
    private LinearLayout recordsContainer;
    private TextView tvArchiveSummary;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_extra, container, false);
        recordsContainer = view.findViewById(R.id.recordsContainer);
        tvArchiveSummary = view.findViewById(R.id.tvArchiveSummary);

        db = FirebaseFirestore.getInstance();
        recordsContainer.removeAllViews();

        loadRecordsFromFirestore(inflater);

        return view;
    }

    private void loadRecordsFromFirestore(LayoutInflater inflater) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            loadGuestTodayRecord(inflater);
            return;
        }

        String uid = currentUser.getUid();
        db.collection("users")
                .document(uid)
                .collection("daily_records")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        tvArchiveSummary.setText("아직 쌓인 기록이 없어요");
                        recordsContainer.addView(createEmptyView());
                        return;
                    }

                    String lastMonthKey = "";
                    int recordCount = 0;
                    int monthCount = 0;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String fullDate = doc.getString("timestamp");
                        if (fullDate == null) continue;

                        String[] dateInfo = getDateInfo(fullDate);
                        String monthKey = dateInfo[0];
                        String monthTitle = dateInfo[1];
                        String dayText = dateInfo[2];

                        if (!monthKey.equals(lastMonthKey)) {
                            recordsContainer.addView(createMonthHeader(monthTitle));
                            lastMonthKey = monthKey;
                            monthCount++;
                        }

                        View itemView = inflater.inflate(R.layout.item_record, recordsContainer, false);

                        ImageView ivEmoji = itemView.findViewById(R.id.ivItemEmoji);
                        TextView tvDateTag = itemView.findViewById(R.id.tvItemDateTag);
                        TextView tvDiary = itemView.findViewById(R.id.tvItemDiary);
                        TextView tvMeta = itemView.findViewById(R.id.tvItemMeta);

                        tvDateTag.setText(dayText);
                        String diary = doc.getString("diary");
                        tvDiary.setText(diary == null || diary.isEmpty() ? "작성된 일기 내용이 없습니다." : diary);

                        Object scoreObj = doc.get("score");
                        String score = (scoreObj != null) ? String.valueOf(scoreObj) : "-";
                        tvMeta.setText("점수 | " + score + "점");

                        String emotion = doc.getString("emotion");
                        ivEmoji.setImageResource(getEmojiImage(emotion));

                        itemView.setClickable(true);
                        itemView.setFocusable(true);
                        itemView.setOnClickListener(v -> {
                            // 상세 화면으로 전달할 데이터를 생성 (기존 방식 호환을 위해 포맷팅)
                            String formattedRecord = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
                                    fullDate,
                                    emotion,
                                    score,
                                    diary,
                                    doc.getString("meals"),
                                    doc.getString("influence"),
                                    doc.getString("stress"),
                                    doc.getString("fatigue"),
                                    doc.getString("sleep"),
                                    doc.getString("need"),
                                    doc.getString("feedback")
                            );

                            RecordDetailFragment fragment = new RecordDetailFragment();
                            Bundle bundle = new Bundle();
                            bundle.putString("record", formattedRecord);
                            fragment.setArguments(bundle);

                            requireActivity().getSupportFragmentManager().beginTransaction()
                                    .replace(R.id.frameLayout, fragment)
                                    .addToBackStack(null)
                                    .commit();
                        });

                        recordsContainer.addView(itemView);
                        recordCount++;
                    }

                    tvArchiveSummary.setText(monthCount + "개월 동안 " + recordCount + "개의 기록이 쌓였어요");
                })
                .addOnFailureListener(e -> {
                    tvArchiveSummary.setText("기록을 불러오는 중 오류가 발생했습니다.");
                    android.util.Log.e("ExtraFragment", "Error fetching records", e);
                });
    }

    private void loadGuestTodayRecord(LayoutInflater inflater) {
        GuestRecordStore.clearIfNotToday(requireContext());
        Map<String, Object> record = GuestRecordStore.getTodayRecord(requireContext());

        if (record == null) {
            tvArchiveSummary.setText("게스트 기록은 오늘 하루만 임시로 보관돼요");
            recordsContainer.addView(createEmptyView());
            return;
        }

        String fullDate = String.valueOf(record.get("timestamp"));
        String[] dateInfo = getDateInfo(fullDate);
        recordsContainer.addView(createMonthHeader(dateInfo[1]));

        View itemView = inflater.inflate(R.layout.item_record, recordsContainer, false);
        ImageView ivEmoji = itemView.findViewById(R.id.ivItemEmoji);
        TextView tvDateTag = itemView.findViewById(R.id.tvItemDateTag);
        TextView tvDiary = itemView.findViewById(R.id.tvItemDiary);
        TextView tvMeta = itemView.findViewById(R.id.tvItemMeta);

        String emotion = String.valueOf(record.get("emotion"));
        String diary = String.valueOf(record.get("diary"));
        String score = String.valueOf(record.get("score"));

        tvDateTag.setText(dateInfo[2]);
        tvDiary.setText(diary.isEmpty() ? "작성된 일기 내용이 없습니다." : diary);
        tvMeta.setText("점수 | " + score + "점");
        ivEmoji.setImageResource(getEmojiImage(emotion));

        itemView.setClickable(true);
        itemView.setFocusable(true);
        itemView.setOnClickListener(v -> {
            String formattedRecord = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
                    fullDate,
                    emotion,
                    score,
                    diary,
                    record.get("meals"),
                    record.get("influence"),
                    record.get("stress"),
                    record.get("fatigue"),
                    record.get("sleep"),
                    record.get("need"),
                    record.get("feedback")
            );

            RecordDetailFragment fragment = new RecordDetailFragment();
            Bundle bundle = new Bundle();
            bundle.putString("record", formattedRecord);
            fragment.setArguments(bundle);

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, fragment)
                    .addToBackStack(null)
                    .commit();
        });

        recordsContainer.addView(itemView);
        tvArchiveSummary.setText("오늘 하루 1개의 게스트 기록이 임시 저장되어 있어요");
    }

    private View createMonthHeader(String monthTitle) {
        LinearLayout headerLayout = new LinearLayout(getContext());
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.topMargin = dpToPx(14);
        headerParams.bottomMargin = dpToPx(12);
        headerLayout.setLayoutParams(headerParams);

        TextView monthTv = new TextView(getContext());
        monthTv.setText(monthTitle);
        monthTv.setTextSize(15);
        monthTv.setTypeface(null, Typeface.BOLD);
        monthTv.setTextColor(Color.WHITE);
        monthTv.setBackgroundResource(R.drawable.archive_month_bg);
        monthTv.setPadding(dpToPx(14), dpToPx(7), dpToPx(14), dpToPx(7));
        headerLayout.addView(monthTv);

        View line = new View(getContext());
        line.setBackgroundColor(Color.parseColor("#E2E2E2"));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                0,
                dpToPx(1),
                1
        );
        lineParams.leftMargin = dpToPx(10);
        line.setLayoutParams(lineParams);
        headerLayout.addView(line);

        return headerLayout;
    }

    private TextView createEmptyView() {
        TextView emptyTv = new TextView(getContext());
        emptyTv.setText("아직 저장된 기록이 없습니다.\n가운데 기록 버튼을 눌러 오늘의 상태를 남겨보세요.");
        emptyTv.setGravity(android.view.Gravity.CENTER);
        emptyTv.setTextColor(Color.parseColor("#777777"));
        emptyTv.setTextSize(15);
        emptyTv.setLineSpacing(dpToPx(4), 1.0f);

        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emptyParams.topMargin = dpToPx(70);
        emptyTv.setLayoutParams(emptyParams);
        return emptyTv;
    }

    private String[] getDateInfo(String fullDate) {
        try {
            String datePart = fullDate.split(" ")[0];
            String[] parts = datePart.split("-");

            String year = parts[0];
            String month = parts[1];
            String day = parts[2];

            String monthKey = year + "-" + month;
            String monthTitle = year + "년 " + Integer.parseInt(month) + "월";
            String dayText = day + "일";

            return new String[]{monthKey, monthTitle, dayText};
        } catch (Exception e) {
            return new String[]{"unknown", "날짜 미상", "--일"};
        }
    }

    private int getEmojiImage(String emotionCode) {
        if ("emo1".equals(emotionCode)) return R.drawable.one;
        if ("emo2".equals(emotionCode)) return R.drawable.two;
        if ("emo3".equals(emotionCode)) return R.drawable.three;
        if ("emo4".equals(emotionCode)) return R.drawable.four;
        if ("emo5".equals(emotionCode)) return R.drawable.five;
        return R.drawable.three;
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}