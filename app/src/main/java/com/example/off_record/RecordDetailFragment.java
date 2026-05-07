package com.example.off_record;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;


public class RecordDetailFragment extends Fragment {

    private String record = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_record_detail, container, false);


        if (getArguments() != null) {
            record = getArguments().getString("record", "");
        }

        View btnBack = view.findViewById(R.id.btnBack);
        ImageView ivDetailEmoji = view.findViewById(R.id.ivDetailEmoji);
        TextView tvDetailDate = view.findViewById(R.id.tvDetailDate);
        TextView tvDetailTime = view.findViewById(R.id.tvDetailTime);
        TextView tvDetailDiary = view.findViewById(R.id.tvDetailDiary);
        TextView tvDetailScore = view.findViewById(R.id.tvDetailScore);
        TextView tvDetailEmotionName = view.findViewById(R.id.tvDetailEmotionName);
        LinearLayout detailContainer = view.findViewById(R.id.detailContainer);

        btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        String[] detail = record.split("\\|", -1);

        if (detail.length >= 5) {
            String fullDate = safeGet(detail, 0);
            String emotionCode = safeGet(detail, 1);
            String score = safeGet(detail, 2);
            String diary = safeGet(detail, 3);
            String meal = safeGet(detail, 4);
            String influence = safeGet(detail, 5);
            String stress = safeGet(detail, 6);
            String fatigue = safeGet(detail, 7);
            String sleep = safeGet(detail, 8);
            String need = safeGet(detail, 9);
            String feedback = safeGet(detail, 10);

            ivDetailEmoji.setImageResource(getEmojiImage(emotionCode));
            tvDetailEmotionName.setText(getEmotionName(emotionCode));
            tvDetailDate.setText(formatDate(fullDate));
            tvDetailTime.setText(formatTime(fullDate));
            tvDetailDiary.setText(diary.isEmpty() ? "작성된 일기 내용이 없습니다." : diary);
            tvDetailScore.setText(score.isEmpty() ? "-점" : score + "점");

            detailContainer.removeAllViews();
            addInfoRow(detailContainer, "감정 영향", influence);
            addInfoRow(detailContainer, "스트레스", stress);
            addInfoRow(detailContainer, "피로도", fatigue);
            addInfoRow(detailContainer, "수면", sleep);
            addInfoRow(detailContainer, "필요한 것", need);
            addInfoRow(detailContainer, "원하는 피드백", feedback);
            addInfoRow(detailContainer, "식사", meal);
        } else {
            tvDetailDate.setText("기록을 불러올 수 없습니다");
            tvDetailTime.setText("");
            tvDetailDiary.setText("저장된 기록 형식이 올바르지 않습니다.");
            tvDetailScore.setText("-점");
            detailContainer.removeAllViews();
        }

        return view;
    }

    private void addInfoRow(LinearLayout container, String label, String value) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.item_detail_row, container, false);
        TextView tvLabel = row.findViewById(R.id.tvDetailLabel);
        TextView tvValue = row.findViewById(R.id.tvDetailValue);

        tvLabel.setText(label);

        if (value == null || value.trim().isEmpty() || "미선택".equals(value.trim())) {
            tvValue.setText("-");
            tvValue.setTextColor(Color.parseColor("#AAAAAA"));
        } else {
            tvValue.setText(value.trim());
            tvValue.setTextColor(Color.parseColor("#333333"));
        }

        container.addView(row);
    }

    private String safeGet(String[] array, int index) {
        if (array == null || index < 0 || index >= array.length || array[index] == null) return "";
        return array[index].trim();
    }

    private String formatDate(String fullDate) {
        try {
            String datePart = fullDate.split(" ")[0];
            String[] parts = datePart.split("-");
            return Integer.parseInt(parts[0]) + "년 "
                    + Integer.parseInt(parts[1]) + "월 "
                    + Integer.parseInt(parts[2]) + "일";
        } catch (Exception e) {
            return fullDate;
        }
    }

    private String formatTime(String fullDate) {
        try {
            String[] parts = fullDate.split(" ");
            if (parts.length > 1) return parts[1] + " 기록";
        } catch (Exception ignored) { }

        return "기록 상세";
    }

    private String getEmotionName(String emotionCode) {
        if ("emo1".equals(emotionCode)) return "매우 좋음";
        if ("emo2".equals(emotionCode)) return "좋음";
        if ("emo3".equals(emotionCode)) return "보통";
        if ("emo4".equals(emotionCode)) return "우울함";
        if ("emo5".equals(emotionCode)) return "화남/불편함";
        return "감정 미선택";
    }

    private int getEmojiImage(String emotionCode) {
        if ("emo1".equals(emotionCode)) return R.drawable.one;
        if ("emo2".equals(emotionCode)) return R.drawable.two;
        if ("emo3".equals(emotionCode)) return R.drawable.three;
        if ("emo4".equals(emotionCode)) return R.drawable.four;
        if ("emo5".equals(emotionCode)) return R.drawable.five;
        return R.drawable.three;
    }
}