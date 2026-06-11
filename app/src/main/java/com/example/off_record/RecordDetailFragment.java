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
        TextView tvResultView = view.findViewById(R.id.tvResultView);
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
            String resultText = safeGet(detail, 11);

            String phoneTotalMinutes = safeGet(detail, 12);
            String phoneOpenCount = safeGet(detail, 13);
            String phoneShortSessionCount = safeGet(detail, 14);
            String phoneNightUsageMinutes = safeGet(detail, 15);
            String digitalSignalScore = safeGet(detail, 16);
            String digitalPattern = safeGet(detail, 17);

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

            addSectionTitle(detailContainer, "휴대폰 사용 기반 행동 데이터");
            addPhoneUsageRows(
                    detailContainer,
                    phoneTotalMinutes,
                    phoneOpenCount,
                    phoneShortSessionCount,
                    phoneNightUsageMinutes,
                    digitalSignalScore,
                    digitalPattern
            );

            if (tvResultView != null) {
                tvResultView.setText(resultText.isEmpty()
                        ? "아직 AI 피드백이 생성되지 않았어요."
                        : resultText);
            }
        } else {
            tvDetailDate.setText("기록을 불러올 수 없습니다");
            tvDetailTime.setText("");
            tvDetailDiary.setText("저장된 기록 형식이 올바르지 않습니다.");
            tvDetailScore.setText("-점");
            if (tvResultView != null) {
                tvResultView.setText("AI 피드백을 불러올 수 없습니다.");
            }
            detailContainer.removeAllViews();
        }

        return view;
    }

    private void addInfoRow(LinearLayout container, String label, String value) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.item_detail_row, container, false);
        TextView tvLabel = row.findViewById(R.id.tvDetailLabel);
        TextView tvValue = row.findViewById(R.id.tvDetailValue);

        tvLabel.setText(label);

        if (value == null || value.trim().isEmpty() || "미선택".equals(value.trim()) || "null".equals(value.trim())) {
            tvValue.setText("-");
            tvValue.setTextColor(Color.parseColor("#AAAAAA"));
        } else {
            tvValue.setText(value.trim());
            tvValue.setTextColor(Color.parseColor("#333333"));
        }

        container.addView(row);
    }

    private void addSectionTitle(LinearLayout container, String title) {
        if (getContext() == null) return;

        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextColor(Color.parseColor("#252525"));
        titleView.setTextSize(18);
        titleView.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(26);
        params.bottomMargin = dpToPx(12);
        titleView.setLayoutParams(params);

        container.addView(titleView);
    }

    private void addPhoneUsageRows(
            LinearLayout container,
            String phoneTotalMinutes,
            String phoneOpenCount,
            String phoneShortSessionCount,
            String phoneNightUsageMinutes,
            String digitalSignalScore,
            String digitalPattern
    ) {
        boolean hasCollectedData = parseInt(phoneTotalMinutes) > 0
                || parseInt(phoneOpenCount) > 0
                || parseInt(phoneShortSessionCount) > 0
                || parseInt(phoneNightUsageMinutes) > 0
                || parseInt(digitalSignalScore) > 0
                || !digitalPattern.trim().isEmpty();

        if (!hasCollectedData) {
            addInfoRow(container, "수집 상태", "휴대폰 사용 데이터가 충분히 수집되지 않았어요");
            addInfoRow(container, "확인 필요", "사용정보 접근 권한을 허용하면 더 정확한 분석이 가능해요");
            return;
        }

        addInfoRow(container, "총 사용시간", formatMinutes(phoneTotalMinutes));
        addInfoRow(container, "앱 실행 횟수", formatCount(phoneOpenCount));
        addInfoRow(container, "짧은 반복 사용", formatCount(phoneShortSessionCount));
        addInfoRow(container, "야간 사용시간", formatMinutes(phoneNightUsageMinutes));
        addInfoRow(container, "디지털 신호 점수", formatScore(digitalSignalScore));
        addInfoRow(container, "사용 패턴", digitalPattern);
    }

    private String safeGet(String[] array, int index) {
        if (array == null || index < 0 || index >= array.length || array[index] == null) return "";
        String value = array[index].trim();
        return "null".equals(value) ? "" : value;
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

    private String formatMinutes(String rawMinutes) {
        int minutes = parseInt(rawMinutes);
        if (minutes <= 0) return "0분";

        int hours = minutes / 60;
        int remainMinutes = minutes % 60;

        if (hours <= 0) return remainMinutes + "분";
        if (remainMinutes <= 0) return hours + "시간";
        return hours + "시간 " + remainMinutes + "분";
    }

    private String formatCount(String rawCount) {
        int count = parseInt(rawCount);
        return count + "회";
    }

    private String formatScore(String rawScore) {
        int score = parseInt(rawScore);
        return score + "점";
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private String normalizeEmotionValue(String emotionValue) {
        if (emotionValue == null) return "";
        String value = emotionValue.trim();

        if ("emo1".equals(value) || "one".equals(value) || "매우_안좋아요".equals(value) || "매우 안 좋아요".equals(value)) return "매우_안좋아요";
        if ("emo2".equals(value) || "two".equals(value) || "안좋아요".equals(value) || "안 좋아요".equals(value)) return "안좋아요";
        if ("emo3".equals(value) || "three".equals(value) || "보통이에요".equals(value)) return "보통이에요";
        if ("emo4".equals(value) || "four".equals(value) || "좋아요".equals(value)) return "좋아요";
        if ("emo5".equals(value) || "five".equals(value) || "매우_좋아요".equals(value) || "매우 좋아요".equals(value)) return "매우_좋아요";

        return value;
    }

    private String getEmotionLabel(String emotionValue) {
        String value = normalizeEmotionValue(emotionValue);

        if ("매우_안좋아요".equals(value)) return "매우 안 좋아요";
        if ("안좋아요".equals(value)) return "안 좋아요";
        if ("보통이에요".equals(value)) return "보통이에요";
        if ("좋아요".equals(value)) return "좋아요";
        if ("매우_좋아요".equals(value)) return "매우 좋아요";

        return "감정 미선택";
    }

    private int getEmotionScore(String emotionValue) {
        String value = normalizeEmotionValue(emotionValue);

        if ("매우_안좋아요".equals(value)) return 1;
        if ("안좋아요".equals(value)) return 2;
        if ("보통이에요".equals(value)) return 3;
        if ("좋아요".equals(value)) return 4;
        if ("매우_좋아요".equals(value)) return 5;

        return 0;
    }

    private String getEmotionName(String emotionValue) {
        return getEmotionLabel(emotionValue);
    }

    private int getEmojiImage(String emotionCode) {
        emotionCode = normalizeEmotionValue(emotionCode);
        if ("매우_안좋아요".equals(emotionCode)) return R.drawable.one;
        if ("안좋아요".equals(emotionCode)) return R.drawable.two;
        if ("보통이에요".equals(emotionCode)) return R.drawable.three;
        if ("좋아요".equals(emotionCode)) return R.drawable.four;
        if ("매우_좋아요".equals(emotionCode)) return R.drawable.five;
        return R.drawable.three;
    }
}
