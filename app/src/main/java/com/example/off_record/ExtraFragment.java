package com.example.off_record;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;

public class ExtraFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_extra, container, false);
        LinearLayout recordsContainer = view.findViewById(R.id.recordsContainer);

        // 1. 저장된 데이터 가져오기
        SharedPreferences pref = getActivity().getSharedPreferences("DailyRecords", Context.MODE_PRIVATE);
        String allRecords = pref.getString("all_records", "");

        if (allRecords.isEmpty()) {
            recordsContainer.removeAllViews();
            TextView emptyTv = new TextView(getContext());
            emptyTv.setText("아직 저장된 기록이 없습니다.");
            emptyTv.setGravity(android.view.Gravity.CENTER);
            recordsContainer.addView(emptyTv);
        } else {
            recordsContainer.removeAllViews(); // "로딩 중" 메시지 삭제

            String[] recordsArray = allRecords.split("##");

            for (String record : recordsArray) {
                if (record.isEmpty()) continue;

                String[] detail = record.split("\\|");
                if (detail.length >= 5) {
                    // 2. item_record.xml 디자인을 한 칸 가져오기
                    View itemView = inflater.inflate(R.layout.item_record, recordsContainer, false);

                    ImageView ivEmoji = itemView.findViewById(R.id.ivItemEmoji);
                    TextView tvDateTag = itemView.findViewById(R.id.tvItemDateTag);
                    TextView tvDiary = itemView.findViewById(R.id.tvItemDiary);

                    // 3. 데이터 채우기
                    // 날짜 태그 (예: 2026-04-29 -> 29일)
                    String fullDate = detail[0];
                    String day = fullDate.split("-")[2].split(" ")[0] + "일";
                    tvDateTag.setText(day);

                    // 일기 내용
                    tvDiary.setText(detail[3]);

                    // 감정 이미지 매칭
                    String emoKey = detail[1];
                    if (emoKey.equals("emo1")) ivEmoji.setImageResource(R.drawable.one);
                    else if (emoKey.equals("emo2")) ivEmoji.setImageResource(R.drawable.two);
                    else if (emoKey.equals("emo3")) ivEmoji.setImageResource(R.drawable.three);
                    else if (emoKey.equals("emo4")) ivEmoji.setImageResource(R.drawable.four);
                    else if (emoKey.equals("emo5")) ivEmoji.setImageResource(R.drawable.five);

                    // 4. 완성된 칸을 리스트에 추가
                    recordsContainer.addView(itemView);
                }
            }
        }
        return view;
    }
}
