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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;


public class ExtraFragment extends Fragment {

    private FirebaseFirestore db;
    private LinearLayout recordsContainer;
    private TextView tvArchiveSummary;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccentBeige;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            colorTextPrimary = ContextCompat.getColor(getContext(), R.color.text_primary);
            colorTextSecondary = ContextCompat.getColor(getContext(), R.color.text_secondary);
            colorAccentBeige = ContextCompat.getColor(getContext(), R.color.accent_beige);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_extra, container, false);
        recordsContainer = view.findViewById(R.id.recordsContainer);
        tvArchiveSummary = view.findViewById(R.id.tvArchiveSummary);

        db = FirebaseFirestore.getInstance();
        if (recordsContainer != null) recordsContainer.removeAllViews();

        loadRecordsFromFirestore(inflater);

        return view;
    }

    private void loadRecordsFromFirestore(LayoutInflater inflater) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (currentUser != null) ? currentUser.getUid() : "guest_user";

        db.collection("users")
                .document(uid)
                .collection("daily_records")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (recordsContainer == null || tvArchiveSummary == null) return;
                    
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
                        
                        TextView tvDateTag = itemView.findViewById(R.id.tvItemDateTag);
                        ImageView ivEmoji = itemView.findViewById(R.id.ivItemEmoji);
                        TextView tvDiary = itemView.findViewById(R.id.tvItemDiary);
                        TextView tvMeta = itemView.findViewById(R.id.tvItemMeta);

                        if (tvDateTag != null) tvDateTag.setText(dayText);
                        
                        String diary = doc.getString("diary");
                        if (tvDiary != null) tvDiary.setText(diary == null || diary.isEmpty() ? "작성된 이야기가 없습니다." : diary);

                        Object scoreObj = doc.get("score");
                        String score = (scoreObj != null) ? String.valueOf(scoreObj) : "-";
                        if (tvMeta != null) tvMeta.setText("점수 | " + score + "점");

                        String emotion = doc.getString("emotion");
                        if (ivEmoji != null) ivEmoji.setImageResource(getEmojiImage(emotion));

                        itemView.setOnClickListener(v -> {
                            String formattedRecord = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
                                    fullDate, emotion, score, diary,
                                    doc.getString("meals"), doc.getString("influence"),
                                    doc.getString("stress"), doc.getString("fatigue"),
                                    doc.getString("sleep"), doc.getString("need"),
                                    doc.getString("feedback")
                            );

                            RecordDetailFragment fragment = new RecordDetailFragment();
                            Bundle bundle = new Bundle();
                            bundle.putString("record", formattedRecord);
                            fragment.setArguments(bundle);

                            if (isAdded()) {
                                requireActivity().getSupportFragmentManager().beginTransaction()
                                        .replace(R.id.frameLayout, fragment)
                                        .addToBackStack(null)
                                        .commit();
                            }
                        });

                        recordsContainer.addView(itemView);
                        recordCount++;
                    }

                    tvArchiveSummary.setText(monthCount + "개월 동안 " + recordCount + "개의 기록이 쌓였어요");
                })
                .addOnFailureListener(e -> {
                    if (tvArchiveSummary != null) tvArchiveSummary.setText("기록을 불러오는 중 오류가 발생했습니다.");
                });
    }

    private View createMonthHeader(String monthTitle) {
        LinearLayout headerLayout = new LinearLayout(getContext());
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.topMargin = dpToPx(16);
        headerParams.bottomMargin = dpToPx(16);
        headerLayout.setLayoutParams(headerParams);

        TextView monthTv = new TextView(getContext());
        monthTv.setText(monthTitle);
        monthTv.setTextSize(14);
        monthTv.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        monthTv.setTextColor(colorTextSecondary);
        monthTv.setPadding(dpToPx(4), 0, dpToPx(8), 0);
        headerLayout.addView(monthTv);

        View line = new View(getContext());
        line.setBackgroundColor(colorAccentBeige);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(0, dpToPx(1), 1);
        line.setLayoutParams(lineParams);
        headerLayout.addView(line);

        return headerLayout;
    }

    private TextView createEmptyView() {
        TextView emptyTv = new TextView(getContext());
        emptyTv.setText("아직 저장된 기록이 없습니다.\n오늘의 상태를 남겨보세요.");
        emptyTv.setGravity(android.view.Gravity.CENTER);
        emptyTv.setTextColor(colorTextSecondary);
        emptyTv.setTextSize(15);
        emptyTv.setLineSpacing(dpToPx(4), 1.0f);

        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emptyParams.topMargin = dpToPx(80);
        emptyTv.setLayoutParams(emptyParams);
        return emptyTv;
    }

    private String[] getDateInfo(String fullDate) {
        try {
            String datePart = fullDate.split(" ")[0];
            String[] parts = datePart.split("-");
            String monthKey = parts[0] + "-" + parts[1];
            String monthTitle = parts[0] + "년 " + Integer.parseInt(parts[1]) + "월";
            return new String[]{monthKey, monthTitle, parts[2] + "일"};
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
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
