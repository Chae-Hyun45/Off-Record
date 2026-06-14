package com.example.off_record;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
        Button btnStartChat = view.findViewById(R.id.btnStartChat);

        btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        String[] detail = record.split("\\|", -1);

        if (detail.length >= 5) {
            String fullDate    = safeGet(detail, 0);
            String emotionCode = safeGet(detail, 1);
            String score       = safeGet(detail, 2);
            String diary       = safeGet(detail, 3);
            String meal        = safeGet(detail, 4);
            String influence   = safeGet(detail, 5);
            String stress      = safeGet(detail, 6);
            String fatigue     = safeGet(detail, 7);
            String sleep       = safeGet(detail, 8);
            String need        = safeGet(detail, 9);
            String feedback    = safeGet(detail, 10);
            String resultText  = safeGet(detail, 11);
            String phoneTotalMinutes      = safeGet(detail, 12);
            String phoneOpenCount         = safeGet(detail, 13);
            String phoneShortSessionCount = safeGet(detail, 14);
            String phoneNightUsageMinutes = safeGet(detail, 15);
            String digitalSignalScore     = safeGet(detail, 16);
            String digitalPattern         = safeGet(detail, 17);

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
            addPhoneUsageRows(detailContainer, phoneTotalMinutes, phoneOpenCount,
                    phoneShortSessionCount, phoneNightUsageMinutes, digitalSignalScore, digitalPattern);

            if (tvResultView != null) {
                tvResultView.setText(resultText.isEmpty()
                        ? "아직 AI 피드백이 생성되지 않았어요." : resultText);
            }

            if (btnStartChat != null) {
                String chatSessionId = fullDate.split(" ")[0];
                String recordContext = String.format(
                        "너는 사용자의 멘탈 케어를 돕는 따뜻한 AI 상담사야.\n" +
                                "아래는 오늘(%s) 사용자의 기록이야. 이 내용을 바탕으로 대화해줘.\n\n" +
                                "📝 일기: %s\n" +
                                "😤 스트레스: %s | 😴 피로도: %s | 🌙 수면: %s\n" +
                                "🤖 기존 AI 피드백: %s\n\n" +
                                "대화 규칙: 짧고 친근하게, 공감 위주로, 필요하면 구체적 조언도 해줘.",
                        fullDate,
                        diary.isEmpty() ? "작성 없음" : diary,
                        stress, fatigue, sleep,
                        resultText.isEmpty() ? "없음" : resultText
                );

                btnStartChat.setOnClickListener(v -> {
                    AiChatFragment chatFragment =
                            AiChatFragment.newInstance(recordContext, chatSessionId);
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .setCustomAnimations(
                                    android.R.anim.slide_in_left,
                                    android.R.anim.slide_out_right,
                                    android.R.anim.slide_in_left,
                                    android.R.anim.slide_out_right)
                            .replace(R.id.frameLayout, chatFragment)
                            .addToBackStack(null)
                            .commit();
                });
            }

        } else {
            tvDetailDate.setText("기록을 불러올 수 없습니다");
            tvDetailTime.setText("");
            tvDetailDiary.setText("저장된 기록 형식이 올바르지 않습니다.");
            tvDetailScore.setText("-점");
            if (tvResultView != null) tvResultView.setText("AI 피드백을 불러올 수 없습니다.");
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
        TextView tv = new TextView(getContext());
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#252525"));
        tv.setTextSize(18);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dpToPx(26);
        p.bottomMargin = dpToPx(12);
        tv.setLayoutParams(p);
        container.addView(tv);
    }

    private void addPhoneUsageRows(LinearLayout container, String phoneTotalMinutes,
                                   String phoneOpenCount, String phoneShortSessionCount,
                                   String phoneNightUsageMinutes, String digitalSignalScore, String digitalPattern) {
        boolean hasData = parseInt(phoneTotalMinutes) > 0 || parseInt(phoneOpenCount) > 0
                || parseInt(phoneShortSessionCount) > 0 || parseInt(phoneNightUsageMinutes) > 0
                || parseInt(digitalSignalScore) > 0 || !digitalPattern.trim().isEmpty();
        if (!hasData) {
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

    private String safeGet(String[] arr, int i) {
        if (arr == null || i < 0 || i >= arr.length || arr[i] == null) return "";
        return "null".equals(arr[i].trim()) ? "" : arr[i].trim();
    }

    private String formatDate(String d) {
        try {
            String[] p = d.split(" ")[0].split("-");
            return Integer.parseInt(p[0]) + "년 " + Integer.parseInt(p[1]) + "월 " + Integer.parseInt(p[2]) + "일";
        } catch (Exception e) { return d; }
    }

    private String formatTime(String d) {
        try { String[] p = d.split(" "); if (p.length > 1) return p[1] + " 기록"; }
        catch (Exception ignored) { }
        return "기록 상세";
    }

    private String formatMinutes(String v) {
        int m = parseInt(v); if (m <= 0) return "0분";
        int h = m / 60, r = m % 60;
        if (h <= 0) return r + "분"; if (r <= 0) return h + "시간";
        return h + "시간 " + r + "분";
    }

    private String formatCount(String v) { return parseInt(v) + "회"; }
    private String formatScore(String v) { return parseInt(v) + "점"; }

    private int parseInt(String v) {
        try { return Integer.parseInt(v == null ? "0" : v.trim()); }
        catch (Exception e) { return 0; }
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }

    private String normalizeEmotionValue(String v) {
        if (v == null) return ""; v = v.trim();
        if ("emo1".equals(v)||"one".equals(v)||"매우_안좋아요".equals(v)||"매우 안 좋아요".equals(v)) return "매우_안좋아요";
        if ("emo2".equals(v)||"two".equals(v)||"안좋아요".equals(v)||"안 좋아요".equals(v)) return "안좋아요";
        if ("emo3".equals(v)||"three".equals(v)||"보통이에요".equals(v)) return "보통이에요";
        if ("emo4".equals(v)||"four".equals(v)||"좋아요".equals(v)) return "좋아요";
        if ("emo5".equals(v)||"five".equals(v)||"매우_좋아요".equals(v)||"매우 좋아요".equals(v)) return "매우_좋아요";
        return v;
    }

    private String getEmotionLabel(String v) {
        v = normalizeEmotionValue(v);
        if ("매우_안좋아요".equals(v)) return "매우 안 좋아요";
        if ("안좋아요".equals(v)) return "안 좋아요";
        if ("보통이에요".equals(v)) return "보통이에요";
        if ("좋아요".equals(v)) return "좋아요";
        if ("매우_좋아요".equals(v)) return "매우 좋아요";
        return "감정 미선택";
    }

    private String getEmotionName(String v) { return getEmotionLabel(v); }

    private int getEmojiImage(String v) {
        v = normalizeEmotionValue(v);
        if ("매우_안좋아요".equals(v)) return R.drawable.one;
        if ("안좋아요".equals(v)) return R.drawable.two;
        if ("보통이에요".equals(v)) return R.drawable.three;
        if ("좋아요".equals(v)) return R.drawable.four;
        if ("매우_좋아요".equals(v)) return R.drawable.five;
        return R.drawable.three;
    }
}